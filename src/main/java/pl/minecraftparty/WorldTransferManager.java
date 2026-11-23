package pl.minecraftparty;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DirectoryLock;
import net.minecraft.world.level.storage.LevelResource;
import pl.minecraftparty.core.*;
import pl.minecraftparty.ui.WorldTransferScreen;
import pl.minecraftparty.world.WorldImport;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static pl.minecraftparty.ui.PartyVisuals.tr;

/** A single explicit world gift: consent, final vanilla save, verified copy, leader handoff, vanilla world load. */
public final class WorldTransferManager {
    private final PartyManager party;
    private final Minecraft client;
    private final ExecutorService io = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("party-world-transfer").factory());
    private Transfer current;
    private String phase = "idle", failure = "", lastName = "";
    private boolean opening;
    private static final class Transfer {
        final UUID id, target;
        final HostNode host;
        final GuestNode guest;
        final String name;
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicLong bytes = new AtomicLong();
        volatile long total;
        long deadline = System.nanoTime() + 90_000_000_000L;
        IntegratedServer server;
        Path source, temporary;
        String sourceFolder, importedFolder;
        boolean leave, stopped;
        boolean handoffObserved;
        long lostConnectionAt;
        WorldPacket pending;
        long lastSignal;
        Transfer(UUID id, UUID target, String name, HostNode host, GuestNode guest) {
            this.id = id; this.target = target; this.name = name; this.host = host; this.guest = guest;
        }
    }
    public WorldTransferManager(PartyManager party, Minecraft client) { this.party = party; this.client = client; }
    public String phase() { return phase; }
    public String failure() { return failure; }
    public String name() { return current == null ? lastName : current.name; }
    public long bytes() { return current == null ? 0 : current.bytes.get(); }
    public long total() { return current == null ? 0 : current.total; }
    public boolean busy() { return current != null && !Set.of("complete", "failed", "cancelled").contains(phase); }
    public boolean holdsWorld() { return busy() && current.host != null && !phase.equals("offered"); }
    public boolean canCancel() { return busy() && !Set.of("handoff", "starting").contains(phase); }
    public boolean canResume() { return current != null && !busy() && current.host == party.host() && current.sourceFolder != null && client.level == null && client.getSingleplayerServer() == null; }
    public boolean canRetryOpen() { return current != null && current.importedFolder != null && party.host() != null && !busy() && client.level == null
            && client.getSingleplayerServer() == null && client.getConnection() == null; }
    public boolean canOffer() {
        return !busy() && !party.leadership.busy() && party.host() != null && client.level != null && client.getSingleplayerServer() != null
                && party.state() != null && party.state().worldId() != null && party.state().multiplayerTarget() == null;
    }
    public void offer(UUID recipient, boolean leave) {
        if (!canOffer()) return;
        IntegratedServer server = client.getSingleplayerServer(); Path source = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        String folder = source.getFileName().toString();
        if (!source.equals(client.getLevelSource().getLevelPath(folder).toAbsolutePath().normalize())) { phase = "failed"; failure = "unsupported_world"; return; }
        String name = server.getWorldData().getLevelName();
        name = name.replaceAll("[\\p{Cc}\\p{Cf}§]", "").strip(); if (name.isBlank()) name = "Minecraft World";
        if (name.length() > 80) name = name.substring(0, 80);
        clearPrevious(); var t = new Transfer(UUID.randomUUID(), recipient, name, party.host(), null);
        t.server = server; t.source = source; t.sourceFolder = folder; t.leave = leave;
        current = t; phase = "offered"; failure = ""; lastName = name;
        party.background(() -> {
            try { t.host.offerWorld(t.id, recipient, t.name); }
            catch (Exception e) { client.execute(() -> fail(t, "recipient_unavailable")); }
        });
    }
    public void hostEvent(HostNode source, HostNode.WorldEvent event) {
        Transfer t = current;
        if (t == null || t.host != source || party.host() != source || !t.target.equals(event.sender()) || !t.id.equals(event.packet().transaction())) return;
        if (event.packet() instanceof WorldPacket.Cancel) { fail(t, "declined"); return; }
        if (event.packet() instanceof WorldPacket.Accept && phase.equals("offered")) {
            if (client.getSingleplayerServer() != t.server || client.level == null) { fail(t, "world_changed"); return; }
            phase = "saving"; t.deadline = System.nanoTime() + TimeUnit.HOURS.toNanos(2);
            party.background(() -> { try { source.worldSaving(t.id); source.closeWorld(); } catch (Exception e) { client.execute(() -> fail(t, "transfer_failed")); } });
            party.retireSharedWorld(); client.disconnect(new WorldTransferScreen(new TitleScreen()), false);
        } else if (event.packet() instanceof WorldPacket.Imported && phase.equals("sending")) {
            phase = "handoff"; t.deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(150);
            party.background(() -> { source.finishWorldTransfer(t.id); client.execute(() -> {
                if (current == t && !t.cancelled.get()) party.leadership.transfer(t.target, t.leave);
            }); });
        }
    }
    public void receive(GuestNode source, WorldPacket packet) {
        if (party.guest() != source) return;
        if (packet instanceof WorldPacket.Offer offer) {
            if (current != null && current.guest == source && current.id.equals(offer.transaction())) return;
            if (busy() || party.leadership.busy() || client.getSingleplayerServer() != null) {
                send(source, new WorldPacket.Cancel(packet.transaction())); return;
            }
            clearPrevious(); var t = new Transfer(offer.transaction(), client.getUser().getProfileId(), offer.name(), null, source);
            current = t; phase = "offered"; failure = ""; lastName = offer.name(); Screen previous = client.gui.screen();
            client.gui.setScreen(new ConfirmScreen(yes -> {
                if (current != t || !phase.equals("offered")) { client.gui.setScreen(previous); return; }
                if (yes) { phase = "waiting_save"; t.deadline = System.nanoTime() + TimeUnit.HOURS.toNanos(2); signal(t, new WorldPacket.Accept(t.id)); client.gui.setScreen(new WorldTransferScreen(previous)); }
                else { cancel(); client.gui.setScreen(previous); }
            }, tr("gift.offer_title"), tr("gift.offer_prompt", offer.name()), tr("gift.accept"), tr("gift.decline")));
            return;
        }
        Transfer t = current;
        if (t == null || t.guest != source || !t.id.equals(packet.transaction())) return;
        if (packet instanceof WorldPacket.Cancel) { fail(t, "cancelled"); return; }
        if (packet instanceof WorldPacket.Saving && phase.equals("waiting_save")) t.pending = null;
        if (packet instanceof WorldPacket.Archive archive && phase.equals("waiting_save")) {
            t.pending = null;
            UUID leader = party.state() == null ? null : party.state().members().stream().filter(LobbyState.Member::host).findFirst().orElseThrow().id();
            if (!archive.info().owner().equals(leader)) { fail(t, "transfer_failed"); return; }
            phase = "receiving"; t.total = archive.info().bytes();
            Path saves = client.getLevelSource().getBaseDir().toAbsolutePath().normalize();
            io.execute(() -> download(t, archive.info(), saves));
        }
    }
    private void download(Transfer t, WorldArchive.Info info, Path saves) {
        try {
            t.temporary = createTemporary(t.id);
            if (Files.getFileStore(t.temporary).getUsableSpace() < info.bytes() + (256L << 20)
                    || Files.getFileStore(saves).getUsableSpace() < info.unpacked() + info.bytes() + (256L << 20)) throw new IOException("no_space");
            Path archive = t.temporary.resolve("world.zip.part"); t.guest.downloadWorld(t.id, info, archive, t.cancelled::get, t.bytes::set);
            WorldArchive.checkCancelled(t.cancelled::get);
            t.bytes.set(0); t.total = info.unpacked();
            client.execute(() -> { if (current == t && !t.cancelled.get()) phase = "importing"; });
            String folder = WorldImport.install(archive, saves, t.id, info, t.target, t.cancelled::get, t.bytes::set);
            client.execute(() -> {
                if (current != t || t.cancelled.get()) return;
                t.importedFolder = folder; phase = "handoff"; t.deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(150);
                signal(t, new WorldPacket.Imported(t.id));
            });
        } catch (Exception e) {
            String reason = "no_space".equals(e.getMessage()) ? "no_space" : t.cancelled.get() ? "cancelled" : "transfer_failed";
            client.execute(() -> fail(t, reason));
        } finally {
            cleanupTemporary(t);
        }
    }
    private void pack(Transfer t) {
        io.execute(() -> {
            try {
                t.temporary = createTemporary(t.id);
                try (DirectoryLock lock = acquireLock(t)) {
                    WorldArchive.Info info = WorldArchive.pack(t.source, t.temporary.resolve("world.zip"), t.name,
                            client.getUser().getProfileId(), t.cancelled::get, t.bytes::set);
                    WorldArchive.checkCancelled(t.cancelled::get);
                    client.execute(() -> {
                        if (current != t || t.cancelled.get()) return;
                        t.bytes.set(0); t.total = info.bytes(); phase = "sending";
                        party.background(() -> { try { t.host.worldArchive(t.id, t.temporary.resolve("world.zip"), info); }
                            catch (Exception e) { client.execute(() -> fail(t, "recipient_unavailable")); } });
                    });
                }
            } catch (Exception e) { client.execute(() -> fail(t, t.cancelled.get() ? "cancelled" : "save_failed")); }
        });
    }
    private DirectoryLock acquireLock(Transfer t) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (true) {
            WorldArchive.checkCancelled(t.cancelled::get);
            try { return DirectoryLock.create(t.source); }
            catch (IOException e) { if (System.nanoTime() >= deadline) throw e; Thread.sleep(250); }
        }
    }
    private Path createTemporary(UUID id) throws IOException {
        Path parent = client.gameDirectory.toPath().resolve("minecraft-party-transfers"); Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent)) throw new IOException("transfer directory link");
        return Files.createDirectory(parent.resolve(id.toString()));
    }
    public void serverStopped(MinecraftServer server) { if (current != null && current.server == server) current.stopped = true; }
    public void tick() {
        Transfer t = current; if (t == null || !busy()) return;
        if (t.cancelled.get()) return;
        if (t.pending != null && t.guest != null && t.guest.connected() && System.nanoTime() - t.lastSignal > 2_000_000_000L) signal(t, t.pending);
        if (System.nanoTime() >= t.deadline) { fail(t, "timeout"); return; }
        if (phase.equals("saving") && (t.stopped || t.server.isShutdown()) && client.level == null && client.getSingleplayerServer() == null) {
            phase = "packing"; pack(t);
        }
        if (phase.equals("sending")) t.bytes.set(t.host.worldBytesSent(t.id));
        if (phase.equals("handoff")) {
            if (party.leadership.busy()) { t.handoffObserved = true; t.pending = null; }
            else if (t.handoffObserved && party.message().equals("leader_failed")) fail(t, "handoff_failed");
        }
        if (t.guest != null && !phase.equals("starting") && !phase.equals("handoff")) {
            if (t.guest.connected()) t.lostConnectionAt = 0;
            else if (t.lostConnectionAt == 0) t.lostConnectionAt = System.nanoTime();
            else if (System.nanoTime() - t.lostConnectionAt > 110_000_000_000L) fail(t, "recipient_unavailable");
        }
        if (phase.equals("starting") && party.host() != null) {
            if (!opening && client.level == null && client.getConnection() == null && client.getSingleplayerServer() == null) open(t, t.importedFolder);
            else if (opening && client.level != null && client.getSingleplayerServer() != null && party.state() != null && party.state().worldId() != null) {
                phase = "complete"; lastName = t.name; opening = false; party.message("gift_complete");
            }
        }
    }
    public void promoted() {
        Transfer t = current;
        if (t != null && t.guest != null && t.importedFolder != null && phase.equals("handoff")) {
            phase = "starting"; opening = false; t.deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
        }
    }
    public void committedOutgoing() {
        Transfer t = current;
        if (t != null && t.host != null && phase.equals("handoff")) { phase = "complete"; lastName = t.name; cleanup(t); }
    }
    private void open(Transfer t, String folder) {
        opening = true;
        client.createWorldOpenFlows().openWorld(folder, () -> { if (current == t) { opening = false; phase = "failed"; failure = "load_failed"; client.gui.setScreen(new WorldTransferScreen(new TitleScreen())); } });
    }
    public void resumeOriginal() {
        Transfer t = current; if (!canResume()) return;
        phase = "complete"; client.createWorldOpenFlows().openWorld(t.sourceFolder, () -> client.gui.setScreen(new WorldTransferScreen(new TitleScreen())));
    }
    public void retryOpen() { if (canRetryOpen()) { phase = "starting"; opening = false; failure = ""; current.cancelled.set(false); current.deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5); } }
    public void cancel() { Transfer t = current; if (t != null && canCancel()) { fail(t, "cancelled"); phase = "cancelled"; } }
    private void fail(Transfer t, String reason) {
        if (current != t || Set.of("complete", "failed", "cancelled").contains(phase)) return;
        t.cancelled.set(true); phase = "failed"; failure = reason; opening = false;
        if (t.host != null) party.background(() -> t.host.cancelWorldTransfer(t.id));
        if (t.guest != null) { t.guest.cancelWorldDownload(); send(t.guest, new WorldPacket.Cancel(t.id)); }
        cleanup(t);
    }
    private void send(GuestNode guest, WorldPacket packet) { party.background(() -> { try { guest.worldPacket(packet); } catch (IOException ignored) { } }); }
    private void signal(Transfer t, WorldPacket packet) { t.pending = packet; t.lastSignal = System.nanoTime(); send(t.guest, packet); }
    private void clearPrevious() { if (current != null) cleanup(current); current = null; }
    private void cleanup(Transfer t) { io.execute(() -> cleanupTemporary(t)); }
    private static void cleanupTemporary(Transfer t) {
        if (t.temporary == null) return;
        for (int attempt = 0; attempt < 5; attempt++) {
            try { WorldArchive.deleteTree(t.temporary); return; }
            catch (IOException ignored) { try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; } }
        }
    }
    public void reset() { if (busy()) fail(current, "cancelled"); clearPrevious(); if (!phase.equals("complete")) phase = "idle"; }
    public void shutdown() { reset(); io.shutdown(); }
}
