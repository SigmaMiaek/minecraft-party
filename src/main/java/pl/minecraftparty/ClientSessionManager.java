package pl.minecraftparty;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import pl.minecraftparty.core.*;
import pl.minecraftparty.ui.PartyScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import java.util.UUID;

/** Automatically joins each world epoch once; explicit Retry avoids endless login-error loops. */
public final class ClientSessionManager {
    private final PartyManager manager;
    private final Minecraft client;
    private UUID attemptedWorld;
    private GuestNode attemptedGuest;
    private boolean inPartyConnection;
    private String endpoint = "";
    private int attempt;
    private String stage = "IDLE", failure = "NONE";
    ClientSessionManager(PartyManager manager, Minecraft client) { this.manager = manager; this.client = client; }
    public void tick() {
        if (client.level != null && inPartyConnection && !isPartyServer()) inPartyConnection = false;
        GuestNode guest = manager.guest(); LobbyState state = manager.state();
        if (guest == null || state == null) { stage = guest == null ? "NO_GUEST" : "WAITING_LOBBY_STATE"; return; }
        if (!guest.connected() || state.worldId() == null) {
            if (attemptedWorld != null) {
                ++attempt; attemptedWorld = null; manager.background(guest::stopProxy);
                if (inPartyConnection) {
                    inPartyConnection = false;
                    client.disconnect(new PartyScreen(new TitleScreen()), false);
                }
                manager.returnToParty();
            }
            stage = guest.connected() ? "WAITING_WORLD" : "WAITING_LOBBY_CONNECTION";
            return;
        }
        UUID world = state.worldId();
        if (attemptedWorld != null && !world.equals(attemptedWorld)) {
            ++attempt; attemptedWorld = null; manager.background(guest::stopProxy);
            if (inPartyConnection) {
                inPartyConnection = false;
                client.disconnect(new PartyScreen(new TitleScreen()), false);
            }
        }
        if (world.equals(attemptedWorld)) { stage = inPartyConnection ? client.level != null ? "IN_WORLD" : "CONNECTING" : "AWAITING_RETRY"; return; }
        if (client.level != null || client.getSingleplayerServer() != null) { stage = "OTHER_GAME_ACTIVE"; return; }
        if (client.getConnection() != null) { stage = "OTHER_CONNECTION_ACTIVE"; return; }
        if (!canAutoJoinFrom(client.gui.screen() == null ? null : client.gui.screen().getClass())) { stage = "WAITING_MENU"; return; }
        attemptedWorld = world; attemptedGuest = guest; inPartyConnection = true;
        int operation = ++attempt;
        guest.status(LobbyState.Status.CONNECTING);
        MultiplayerTarget destination = state.multiplayerTarget();
        stage = destination == null ? "PREPARING_TUNNEL" : "PREPARING_MULTIPLAYER"; failure = "NONE";
        manager.message(destination == null ? "connecting_world" : "connecting_multiplayer");
        manager.background(() -> {
            try {
                String address = destination == null ? "127.0.0.1:" + guest.prepareWorld(world) : destination.address();
                client.execute(() -> {
                    if (attempt != operation || manager.guest() != guest || manager.state() == null || !world.equals(manager.state().worldId())) return;
                    endpoint = address;
                    stage = "CONNECT_SCREEN";
                    ConnectScreen.startConnecting(new PartyScreen(new TitleScreen()), client,
                            ServerAddress.parseString(endpoint), new ServerData("Minecraft Party", endpoint, ServerData.Type.OTHER), false, null);
                });
            } catch (Exception e) {
                String type = e.getClass().getSimpleName();
                client.execute(() -> { if (operation == attempt) { stage = "TUNNEL_FAILED"; failure = type;
                    inPartyConnection = false; manager.message("tunnel_failed"); guest.status(LobbyState.Status.LOBBY); } });
            }
        });
    }
    public void joined() {
        if (inPartyConnection && !isPartyServer()) { inPartyConnection = false; return; }
        if (inPartyConnection && manager.guest() == attemptedGuest) {
            stage = "IN_WORLD";
            manager.guest().status(LobbyState.Status.IN_WORLD); manager.message("in_world");
        }
    }
    public void disconnected() {
        if (inPartyConnection && manager.guest() != null) {
            stage = "DISCONNECTED";
            manager.guest().status(LobbyState.Status.LOBBY); manager.message("world_disconnected");
            inPartyConnection = false;
        }
    }
    public void retry() { attemptedWorld = null; }
    private boolean isPartyServer() { return client.getCurrentServer() != null && endpoint.equals(client.getCurrentServer().ip); }
    static boolean canAutoJoinFrom(Class<? extends Screen> type) {
        // Third-party launchers can replace TitleScreen with an unrelated Screen subclass.
        // Existing worlds/connections are guarded above; protect in-progress connection/loading/world-selection flows too.
        return type != null && !ConnectScreen.class.isAssignableFrom(type) && !PauseScreen.class.isAssignableFrom(type)
                && !ProgressScreen.class.isAssignableFrom(type) && !LevelLoadingScreen.class.isAssignableFrom(type)
                && !CreateWorldScreen.class.isAssignableFrom(type) && !SelectWorldScreen.class.isAssignableFrom(type);
    }
    public String diagnostics() {
        LobbyState snapshot = manager.state();
        return "Auto join stage: " + stage + "\nAuto join failure: " + failure
                + "\nIncoming world available: " + (snapshot != null && snapshot.worldId() != null)
                + "\nIncoming target: " + (snapshot == null || snapshot.worldId() == null ? "NONE" : snapshot.multiplayerTarget() == null ? "INTEGRATED" : "MULTIPLAYER")
                + "\nClient world loaded: " + (client.level != null)
                + "\nClient screen: " + (client.gui.screen() == null ? "NONE" : client.gui.screen().getClass().getSimpleName()) + "\n";
    }
    public void reset() {
        ++attempt;
        if (inPartyConnection && client.getSingleplayerServer() == null) client.disconnect(new PartyScreen(new TitleScreen()), false);
        inPartyConnection = false; attemptedWorld = null; attemptedGuest = null; endpoint = "";
        stage = "IDLE"; failure = "NONE";
    }
}
