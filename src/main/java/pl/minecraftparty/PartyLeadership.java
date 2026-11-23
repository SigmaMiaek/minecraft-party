package pl.minecraftparty;

import net.minecraft.client.Minecraft;
import pl.minecraftparty.core.*;
import pl.minecraftparty.network.E4mcRelay;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Two-phase handoff. The current leader stays online until the successor has a real TLS/e4mc listener. */
public final class PartyLeadership {
    private final PartyManager manager;
    private final Minecraft client;
    private final ArrayDeque<UUID> candidates = new ArrayDeque<>();
    private LeadershipPlan outgoing, preparedPlan;
    private UUID incoming;
    private HostNode preparedHost;
    private E4mcRelay preparedRelay;
    private AtomicBoolean cancelled = new AtomicBoolean();
    private long deadline;
    private int epoch;
    private boolean starting, departing, committing;
    private GuestNode waitingSource;
    PartyLeadership(PartyManager manager, Minecraft client) { this.manager = manager; this.client = client; }
    public boolean busy() { return starting || outgoing != null || incoming != null; }
    public boolean leave() {
        if (busy()) { manager.message("leader_busy"); return true; }
        LobbyState state = manager.state();
        if (manager.host() == null || state == null) return false;
        candidates.clear();
        state.members().stream().filter(m -> !m.host() && m.status() != LobbyState.Status.DISCONNECTED).forEach(m -> candidates.add(m.id()));
        if (candidates.isEmpty()) return false;
        departing = true; next(); return true;
    }
    public void transfer(UUID id) {
        if (manager.worlds.busy()) { manager.message("gift_busy"); return; }
        transfer(id, false);
    }
    void transfer(UUID id, boolean leaving) {
        if (manager.host() == null || busy()) return;
        if (manager.state() == null || manager.state().worldId() != null || client.level != null || client.getSingleplayerServer() != null) {
            manager.message("leader_only_lobby"); return;
        }
        candidates.clear(); candidates.add(id); departing = leaving; next();
    }
    private void next() {
        UUID target = candidates.poll();
        if (target == null) { starting = false; outgoing = null; committing = false; manager.message("leader_failed"); return; }
        HostNode host = manager.host(); if (host == null) return;
        int operation = ++epoch; starting = true; deadline = System.nanoTime() + 90_000_000_000L;
        manager.message("leader_preparing");
        manager.retireSharedWorld();
        manager.background(() -> {
            try {
                host.closeWorld();
                LeadershipPlan plan = host.planLeadership(target, departing); host.beginLeadership(plan);
                client.execute(() -> {
                    if (epoch != operation || manager.host() != host) { manager.background(() -> host.abortLeadership(plan.transaction())); return; }
                    outgoing = plan; starting = false;
                });
            } catch (Exception e) { client.execute(() -> { if (epoch == operation) failed(); }); }
        });
    }
    public void hostEvent(HostNode host, HostNode.LeadershipEvent event) {
        if (manager.host() != host || outgoing == null || !outgoing.transaction().equals(event.packet().transaction())) return;
        if (event.packet() instanceof LeadershipPacket.Failed && !committing) { failed(); return; }
        if (!(event.packet() instanceof LeadershipPacket.Ready ready) || committing) return;
        if (!ready.invite().relay()) { failed(); return; }
        LeadershipPlan plan = outgoing; int operation = epoch; committing = true;
        manager.message("leader_switching");
        manager.background(() -> {
            try {
                CompletableFuture<Void> delivered = host.commitLeadership(plan.transaction(), ready.invite());
                try { delivered.get(8, TimeUnit.SECONDS); } catch (TimeoutException ignored) { /* Online clients have their pinned destination; slow ones reconnect. */ }
                client.execute(() -> {
                    if (epoch != operation || manager.host() != host) return;
                    outgoing = null; starting = committing = false; candidates.clear();
                    if (plan.leaving()) { manager.worlds.committedOutgoing(); manager.leaveImmediately(); }
                    else manager.migrateGuest(ready.invite(), plan.credentials().get(plan.previousLeader()));
                });
            } catch (Exception e) { client.execute(() -> { if (epoch == operation) failed(); }); }
        });
    }
    private void failed() {
        HostNode host = manager.host(); LeadershipPlan old = outgoing;
        outgoing = null; starting = committing = false;
        if (host != null && old != null) manager.background(() -> host.abortLeadership(old.transaction()));
        if (departing && !candidates.isEmpty()) next(); else { ++epoch; candidates.clear(); manager.message("leader_failed"); }
    }
    public void receive(GuestNode source, LeadershipPacket packet) {
        if (source == null || manager.guest() != source) return;
        if (packet instanceof LeadershipPacket.Starting) {
            incoming = packet.transaction(); deadline = System.nanoTime() + 100_000_000_000L; manager.message("leader_preparing");
        } else if (packet instanceof LeadershipPacket.Prepare prepare) {
            resetPrepared(); incoming = packet.transaction(); preparedPlan = prepare.plan(); deadline = System.nanoTime() + 100_000_000_000L;
            boolean closingPartyGame = manager.retireSharedWorld();
            if (client.level != null || client.getSingleplayerServer() != null || client.getConnection() != null) {
                if (closingPartyGame && client.getSingleplayerServer() == null) { waitingSource = source; return; }
                manager.background(() -> { try { source.leadership(new LeadershipPacket.Failed(packet.transaction())); } catch (Exception ignored) { } });
                incoming = null; preparedPlan = null; return;
            }
            prepare(source, prepare.plan());
        } else if (packet instanceof LeadershipPacket.Abort && packet.transaction().equals(incoming)) {
            resetPrepared(); incoming = null; manager.message("leader_failed");
        } else if (packet instanceof LeadershipPacket.Commit commit && packet.transaction().equals(incoming)) {
            if (commit.successor().equals(client.getUser().getProfileId())) {
                if (preparedHost == null || preparedRelay == null || !preparedRelay.valid() || preparedPlan == null || !preparedPlan.transaction().equals(commit.transaction())) {
                    resetPrepared(); incoming = null; manager.message("leader_failed"); return;
                }
                HostNode host = preparedHost; E4mcRelay relay = preparedRelay;
                preparedHost = null; preparedRelay = null; preparedPlan = null; incoming = null;
                manager.promoteHost(host, relay);
            } else {
                incoming = null; manager.migrateGuest(commit.invite(), source.migrationCredential());
            }
        }
    }
    private void prepare(GuestNode source, LeadershipPlan plan) {
        int operation = ++epoch; AtomicBoolean abort = new AtomicBoolean(); cancelled = abort;
        manager.message("leader_preparing");
        manager.background(() -> {
            HostNode host = null; E4mcRelay relay = null;
            try {
                AtomicReference<HostNode> reference = new AtomicReference<>();
                host = HostNode.successor(plan, List.of("127.0.0.1"), InetAddress.getByName("127.0.0.1"), next -> client.execute(() -> {
                    if (reference.get() != null) manager.acceptHostState(reference.get(), next);
                }));
                reference.set(host);
                relay = E4mcRelay.open(host.invite(), abort::get, key -> client.execute(() -> {
                    if (manager.host() == reference.get()) manager.relayLost(key);
                }));
                HostNode readyHost = host; E4mcRelay readyRelay = relay;
                client.execute(() -> {
                    if (epoch != operation || abort.get() || manager.guest() != source) {
                        manager.background(() -> { readyRelay.close(); readyHost.close(); }); return;
                    }
                    preparedHost = readyHost; preparedRelay = readyRelay;
                    manager.background(() -> {
                        if (abort.get()) return;
                        try { source.leadership(new LeadershipPacket.Ready(plan.transaction(), readyRelay.invitation(readyHost.invite()))); }
                        catch (Exception e) { client.execute(() -> { if (epoch == operation) { resetPrepared(); incoming = null; manager.message("leader_failed"); } }); }
                    });
                });
            } catch (Exception e) {
                if (relay != null) relay.close(); if (host != null) host.close();
                if (!abort.get()) try { source.leadership(new LeadershipPacket.Failed(plan.transaction())); } catch (Exception ignored) { }
                client.execute(() -> { if (epoch == operation) { incoming = null; preparedPlan = null; manager.message("leader_failed"); } });
            }
        });
    }
    public void tick() {
        if (waitingSource != null && client.level == null && client.getConnection() == null && client.getSingleplayerServer() == null) {
            GuestNode source = waitingSource; waitingSource = null;
            if (preparedPlan != null && manager.guest() == source) prepare(source, preparedPlan);
        }
        if (busy() && !committing && System.nanoTime() >= deadline) {
            if (outgoing != null || starting) failed(); else { resetPrepared(); incoming = null; manager.message("leader_failed"); }
        }
    }
    private void resetPrepared() {
        ++epoch; cancelled.set(true);
        HostNode host = preparedHost; E4mcRelay relay = preparedRelay;
        preparedHost = null; preparedRelay = null; preparedPlan = null;
        waitingSource = null;
        if (host != null || relay != null) manager.background(() -> { if (relay != null) relay.close(); if (host != null) host.close(); });
    }
    public void reset() { resetPrepared(); outgoing = null; incoming = null; starting = committing = false; candidates.clear(); }
}
