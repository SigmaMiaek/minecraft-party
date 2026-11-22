package pl.minecraftparty;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import pl.minecraftparty.core.*;
import pl.minecraftparty.ui.PartyScreen;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;
import net.fabricmc.loader.api.FabricLoader;
import pl.minecraftparty.network.E4mcRelay;
import pl.minecraftparty.network.ShortCodeClient;

/** Minecraft-facing coordinator. UI state is written on the Minecraft client thread. */
public final class PartyManager {
    private final Minecraft client;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("party-actions").factory());
    private final IntegratedServerBridge bridge;
    private final ClientSessionManager sessions;
    public final PartyConfig config;
    public final PartyWorldPermissions permissions;
    public final PartyCodes codes;
    public final PartyLeadership leadership;
    private HostNode host;
    private GuestNode guest;
    private LobbyState state;
    private String invite = "";
    private String message = "idle";
    private boolean busy;
    private boolean returnPending;
    private boolean waitForMenuBeforeSharing;
    private int generation;
    private MultiplayerTarget sharedMultiplayer;
    private long lastChatNanos;
    private InternetExposure internetExposure;
    private E4mcRelay relayExposure;
    private AtomicBoolean cancelCreate = new AtomicBoolean();
    private String diagnosticRole = "NONE", previousDiagnostics = "", setupError = "NONE";
    private int targetPort, candidateCount;
    public String diagnostics() {
        String version = FabricLoader.getInstance().getModContainer("minecraftparty").orElseThrow().getMetadata().getVersion().getFriendlyString();
        StringBuilder report = new StringBuilder("Minecraft Party ").append(version)
                .append("\nUTC: ").append(java.time.Instant.now()).append("\nOS: ").append(System.getProperty("os.name"))
                .append("\nJava: ").append(System.getProperty("java.version")).append("\nRole: ").append(diagnosticRole)
                .append("\nState: ").append(message).append("\nSetup error: ").append(setupError).append('\n');
        if (host != null) report.append(hostDiagnostics()).append(bridge.diagnostics());
        if (guest != null) report.append("Guest transport: ").append(guest.relay() ? "E4MC_RELAY" : "DIRECT")
                .append("\nTarget TCP port: ").append(targetPort).append("\nCandidates: ").append(candidateCount)
                .append("\n").append(guest.diagnostics()).append('\n').append(sessions.diagnostics());
        if (host == null && guest == null) report.append(previousDiagnostics);
        return report.toString();
    }
    private String hostDiagnostics() {
        HostNode node = host; if (node == null) return "";
        var stats = node.connectionStats(); InternetExposure exposure = internetExposure;
        E4mcRelay relay = relayExposure;
        return "Mode: " + (relay != null ? "E4MC_RELAY" : exposure == null ? "LAN" : exposure.mechanism())
                + "\nLocal TCP port: " + stats.localPort()
                + "\nAdvertised TCP port: " + (relay != null ? E4mcProtocol.PORT : exposure == null ? stats.localPort() : exposure.advertisedPort())
                + "\nExposure valid: " + (relay != null ? relay.valid() : exposure == null || exposure.valid())
                + "\nAccepted TCP: " + stats.tcpAccepted() + "\nEstablished TLS: " + stats.tlsAccepted()
                + "\nFailed TLS: " + stats.tlsFailed() + "\nAccepted lobby sessions: " + stats.lobbyAccepted()
                + "\nRejected lobby sessions: " + stats.lobbyRejected() + "\nRejected protocol: " + stats.protocolRejected()
                + "\nHost world available: " + (node.snapshot().worldId() != null)
                + "\nMultiplayer following enabled: " + config.shareMultiplayer + "\n"
                + (relay == null ? "" : relay.diagnostics());
    }
    public PartyManager(Minecraft client) {
        this.client = client; config = PartyConfig.load(); permissions = new PartyWorldPermissions(this::message);
        codes = new PartyCodes(this, client);
        leadership = new PartyLeadership(this, client);
        bridge = new IntegratedServerBridge(this); sessions = new ClientSessionManager(this, client);
    }
    public LobbyState state() { return state; }
    public HostNode host() { return host; }
    public GuestNode guest() { return guest; }
    public String invite() { return invite; }
    public String message() { return message; }
    public boolean busy() { return busy || leadership.busy(); }
    public boolean active() { return host != null || guest != null || busy; }
    public boolean canChat() { return !leadership.busy() && (host != null || guest != null && guest.connected()); }
    public boolean chat(String text) {
        String clean = text.strip();
        if (!canChat() || clean.isEmpty() || clean.length() > LobbyModel.MAX_CHAT_LENGTH) return false;
        long now = System.nanoTime();
        if (lastChatNanos != 0 && now - lastChatNanos < 750_000_000L) { message = "chat_rate"; return false; }
        lastChatNanos = now;
        HostNode hostNode = host; GuestNode guestNode = guest; int operation = generation;
        background(() -> {
            try { if (hostNode != null) hostNode.chat(clean); else if (guestNode != null) guestNode.chat(clean); }
            catch (Exception e) { client.execute(() -> { if (generation == operation) message = "chat_failed"; }); }
        });
        return true;
    }
    public void toggleMultiplayerSharing() {
        if (host == null) return;
        config.shareMultiplayer = !config.shareMultiplayer;
        background(config::save);
        if (!config.shareMultiplayer && sharedMultiplayer != null) {
            sharedMultiplayer = null; HostNode node = host; background(node::closeWorld);
        }
    }
    public void toggleTeleport() {
        if (host == null || !permissions.available()) return;
        config.allowPartyTeleport = !config.allowPartyTeleport;
        permissions.teleport(config.allowPartyTeleport); background(config::save);
    }
    public void toggleOperator(UUID id) {
        if (host != null && permissions.available()) permissions.setOperator(id, !permissions.operator(id));
    }
    public void create(String name, int limit) {
        if (active()) return;
        waitForMenuBeforeSharing = false;
        config.networkMode = PartyConfig.NetworkMode.RELAY;
        int operation = ++generation; busy = true; message = "creating";
        diagnosticRole = "HOST"; previousDiagnostics = ""; setupError = "NONE";
        UUID id = client.getUser().getProfileId(); String username = client.getUser().getName();
        config.lobbyName = name; config.memberLimit = limit;
        PartyConfig.NetworkMode mode = config.networkMode;
        boolean internet = mode == PartyConfig.NetworkMode.DIRECT, relayed = mode == PartyConfig.NetworkMode.RELAY;
        AtomicBoolean cancelled = new AtomicBoolean(); cancelCreate = cancelled;
        background(() -> {
            HostNode node = null; InternetExposure exposure = null; E4mcRelay relay = null;
            try {
                config.save();
                java.util.function.Consumer<LobbyState> updated = next -> client.execute(() -> {
                    if (generation == operation) state = next;
                });
                node = relayed ? new HostNode(id, username, name, limit, java.util.List.of("127.0.0.1"),
                        java.net.InetAddress.getByName("127.0.0.1"), updated) : new HostNode(id, username, name, limit, updated);
                if (relayed) {
                    client.execute(() -> { if (generation == operation) message = "preparing_relay"; });
                    relay = E4mcRelay.open(node.invite(), cancelled::get, key -> client.execute(() -> {
                        if (generation == operation) { invite = ""; message = key; }
                    }));
                }
                if (internet) {
                    client.execute(() -> { if (generation == operation) message = "preparing_internet"; });
                    exposure = InternetExposure.open(node.invite(), cancelled::get, key -> client.execute(() -> {
                        if (generation == operation) { invite = ""; message = key; }
                    }));
                }
                HostNode created = node; InternetExposure opened = exposure; E4mcRelay openedRelay = relay;
                String code = (openedRelay != null ? openedRelay.invitation(created.invite())
                        : opened == null ? created.invite() : opened.invitation(created.invite())).encode();
                client.execute(() -> {
                    if (generation != operation) { background(() -> { created.close(); if (opened != null) opened.close(); if (openedRelay != null) openedRelay.close(); }); return; }
                    boolean valid = (openedRelay == null || openedRelay.valid()) && (opened == null || opened.valid());
                    host = created; internetExposure = opened; relayExposure = openedRelay; state = created.snapshot(); invite = valid ? code : ""; busy = false;
                    observeHost(created);
                    message = valid ? relayed ? "hosting_relay" : internet ? "hosting_internet" : "hosting"
                            : relayed ? "relay_lost" : "internet_lost";
                    PartyClient.LOG.info("Party host ready: mode={}, local TCP={}", relayed ? "E4MC_RELAY" : opened == null ? "LAN" : opened.mechanism(),
                            created.connectionStats().localPort());
                });
            } catch (Exception e) {
                if (node != null) node.close(); if (exposure != null) exposure.close(); if (relay != null) relay.close();
                String type = e instanceof E4mcRelay.Failure failure ? failure.key() : e.getClass().getSimpleName();
                client.execute(() -> { if (generation == operation) setupError = type; });
                failed(operation, e instanceof E4mcRelay.Failure failure ? failure.key()
                        : relayed ? "relay_unavailable" : internet ? "internet_unavailable" : "create_failed");
            }
        });
    }
    public void join(String code) {
        if (active()) return;
        if (!ShortCodeClient.isCode(code)) { message = "codes_six_required"; return; }
        if (config.codeServiceUrl.isBlank()) { message = "codes_not_configured"; return; }
        int operation = ++generation; busy = true; message = "connecting";
        diagnosticRole = "GUEST"; previousDiagnostics = ""; setupError = "NONE";
        UUID id = client.getUser().getProfileId(); String username = client.getUser().getName();
        message = "codes_requesting";
        codes.join(code, username, invitation -> connectInvite(invitation, operation, id, username), key -> failed(operation, key));
    }
    private void connectInvite(String code, int operation, UUID id, String username) {
        connectInvite(code, operation, id, username, "");
    }
    private void connectInvite(String code, int operation, UUID id, String username, String resume) {
        background(() -> {
            try {
                Invite invitation = Invite.decode(code.strip());
                client.execute(() -> { if (generation == operation) { targetPort = invitation.port(); candidateCount = invitation.relay() ? 1 : invitation.candidates().size(); } });
                var reference = new java.util.concurrent.atomic.AtomicReference<GuestNode>();
                GuestNode node = new GuestNode(invitation, id, username, resume,
                        next -> client.execute(() -> { if (generation == operation) state = next; }),
                        next -> client.execute(() -> { if (generation == operation) {
                            message = next;
                            if (!next.equals("connected") && !next.equals("reconnecting")) PartyClient.LOG.info("Party connection result: {}", next);
                        } }), packet -> client.execute(() -> { if (generation == operation) leadership.receive(reference.get(), packet); }));
                reference.set(node);
                client.execute(() -> {
                    if (generation != operation) { background(node::close); return; }
                    guest = node; busy = false;
                });
            } catch (Exception e) { failed(operation, "invite_invalid"); }
        });
    }
    private void failed(int operation, String text) {
        client.execute(() -> { if (generation == operation) { busy = false; message = text; if (host == null && guest == null) state = null; } });
    }
    public void rotateInvite() {
        HostNode node = host; if (node == null || leadership.busy()) return;
        codes.reset();
        invite = "";
        int operation = generation;
        InternetExposure exposure = internetExposure;
        E4mcRelay relay = relayExposure;
        background(() -> { node.rotateInvite(); String code = publishedInvite(node, exposure, relay);
            client.execute(() -> { if (generation == operation) { invite = code; message = code.isEmpty() ? relay != null ? "relay_lost" : "internet_lost" : "rotated"; } }); });
    }
    public void kick(UUID id) {
        HostNode node = host; if (node == null || leadership.busy()) return;
        codes.reset();
        invite = "";
        int operation = generation;
        InternetExposure exposure = internetExposure;
        E4mcRelay relay = relayExposure;
        permissions.revoke(id);
        background(() -> { node.kick(id); String code = publishedInvite(node, exposure, relay);
            client.execute(() -> { if (generation == operation) invite = code; }); });
    }
    public void leave() {
        if (leadership.busy()) { message = "leader_busy"; return; }
        if (host != null && leadership.leave()) return;
        leaveImmediately();
    }
    void leaveImmediately() {
        leadership.reset();
        codes.reset();
        previousDiagnostics = host != null ? hostDiagnostics() : guest != null ? "Target TCP port: " + targetPort + "\n" + guest.diagnostics() : previousDiagnostics;
        ++generation; cancelCreate.set(true);
        HostNode oldHost = host; GuestNode oldGuest = guest;
        InternetExposure oldExposure = internetExposure; internetExposure = null;
        E4mcRelay oldRelay = relayExposure; relayExposure = null;
        host = null; guest = null; state = null; invite = ""; busy = false; message = "idle";
        returnPending = false; sharedMultiplayer = null; bridge.detach(); sessions.reset();
        waitForMenuBeforeSharing = false;
        background(() -> { if (oldHost != null) oldHost.close(); if (oldGuest != null) oldGuest.close(); if (oldExposure != null) oldExposure.close(); if (oldRelay != null) oldRelay.close(); });
    }
    private static String publishedInvite(HostNode node, InternetExposure exposure, E4mcRelay relay) {
        try { return (relay != null ? relay.invitation(node.invite()) : exposure != null ? exposure.invitation(node.invite()) : node.invite()).encode(); }
        catch (IllegalStateException e) { return ""; }
    }
    public void tick() {
        leadership.tick();
        if (!leadership.busy()) codes.tick();
        if (!leadership.busy()) {
            if (waitForMenuBeforeSharing && client.level == null && client.getConnection() == null && client.getSingleplayerServer() == null)
                waitForMenuBeforeSharing = false;
            if (!waitForMenuBeforeSharing) { bridge.tick(client); syncMultiplayer(); }
            permissions.update(state); sessions.tick();
        }
        if (returnPending && active() && client.level == null && client.getSingleplayerServer() == null
                && (client.gui.screen() instanceof TitleScreen || client.gui.screen() instanceof PartyScreen
                || client.gui.screen() instanceof net.minecraft.client.gui.screens.DisconnectedScreen)) {
            returnPending = false; client.gui.setScreen(new PartyScreen(new TitleScreen()));
        }
    }
    private void syncMultiplayer() {
        if (host == null || !config.shareMultiplayer || client.level == null || client.getSingleplayerServer() != null
                || client.getCurrentServer() == null) return;
        if (sharedMultiplayer != null && sharedMultiplayer.address().equals(client.getCurrentServer().ip)) return;
        try {
            MultiplayerTarget target = new MultiplayerTarget(client.getCurrentServer().ip);
            if (target.equals(sharedMultiplayer)) return;
            sharedMultiplayer = target; returnPending = false;
            HostNode node = host; UUID epoch = UUID.randomUUID();
            background(() -> node.openMultiplayer(target, epoch));
            message = "sharing_multiplayer";
        } catch (IllegalArgumentException e) { message = "server_invalid"; }
    }
    public void playJoined() { sessions.joined(); }
    public void playDisconnected() {
        sessions.disconnected();
        if (host != null && sharedMultiplayer != null) {
            HostNode node = host; sharedMultiplayer = null; background(node::closeWorld); returnToParty();
        }
    }
    public void serverStopping(MinecraftServer server) {
        client.execute(() -> { bridge.serverStopping(server); returnToParty(); });
    }
    public void serverPlayer(UUID id, boolean present) {
        client.execute(() -> { HostNode node = host; if (node != null) background(() -> node.playerInWorld(id, present)); });
    }
    public void retryWorld() { sessions.retry(); }
    public void background(Runnable action) { worker.execute(action); }
    public void message(String key) { client.execute(() -> message = key); }
    public void returnToParty() { client.execute(() -> returnPending = active()); }
    public void shutdown() { leaveImmediately(); worker.shutdown(); }
    private void observeHost(HostNode node) {
        node.leadershipListener(event -> client.execute(() -> leadership.hostEvent(node, event)));
    }
    void acceptHostState(HostNode node, LobbyState next) { if (host == node) state = next; }
    void relayLost(String key) { invite = ""; message = key; }
    boolean retireSharedWorld() {
        boolean partyConnection = sessions.partyConnection();
        sessions.reset(); bridge.detach(); sharedMultiplayer = null;
        return partyConnection;
    }
    void promoteHost(HostNode next, E4mcRelay relay) {
        GuestNode previous = guest;
        ++generation; codes.reset(); leadership.reset(); retireSharedWorld();
        guest = null; host = next; relayExposure = relay; internetExposure = null;
        state = next.snapshot(); invite = relay.invitation(next.invite()).encode(); busy = false; diagnosticRole = "HOST";
        waitForMenuBeforeSharing = client.level != null || client.getConnection() != null || client.getSingleplayerServer() != null;
        observeHost(next); message = "leader_changed";
        if (previous != null) background(previous::close);
    }
    void migrateGuest(Invite next, String resume) {
        HostNode previousHost = host; GuestNode previousGuest = guest;
        E4mcRelay previousRelay = relayExposure; InternetExposure previousExposure = internetExposure;
        int operation = ++generation;
        leadership.reset(); codes.reset(); retireSharedWorld();
        host = null; guest = null; relayExposure = null; internetExposure = null; invite = ""; busy = true; diagnosticRole = "GUEST";
        message = "leader_switching";
        background(() -> {
            if (previousGuest != null) previousGuest.close(); if (previousHost != null) previousHost.close();
            if (previousRelay != null) previousRelay.close(); if (previousExposure != null) previousExposure.close();
        });
        connectInvite(next.encode(), operation, client.getUser().getProfileId(), client.getUser().getName(), resume);
    }
}
