package pl.minecraftparty;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import pl.minecraftparty.core.HostNode;
import pl.minecraftparty.mixin.*;
import java.net.*;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;
import pl.minecraftparty.core.LobbyState;
import java.util.concurrent.atomic.AtomicBoolean;

/** Only the vanilla listener binds loopback. The public listener is authenticated TLS. */
public final class IntegratedServerBridge {
    private final PartyManager manager;
    private Context attached;
    private MinecraftServer retired;
    private String idleStage = "NO_LOCAL_SERVER";
    IntegratedServerBridge(PartyManager manager) { this.manager = manager; }

    public void tick(Minecraft client) {
        IntegratedServer server = client.getSingleplayerServer();
        if (attached != null && server != attached.server) {
            detach(); manager.returnToParty();
        }
        if (manager.host() == null) { idleStage = "NO_HOST"; return; }
        if (server == null) { idleStage = "NO_LOCAL_SERVER"; return; }
        if (client.level == null || client.player == null) { idleStage = "WAITING_PLAYER"; return; }
        if (!server.isReady() || !server.isRunning()) { idleStage = "WAITING_SERVER"; return; }
        if (server == retired) { idleStage = "RETIRED_SERVER"; return; }
        if (attached != null) return;
        Context context = new Context(server, manager.host()); attached = context;
        HostNode host = context.host;
        UUID hostId = client.getUser().getProfileId();
        Set<UUID> members = manager.state().members().stream().map(LobbyState.Member::id).collect(Collectors.toUnmodifiableSet());
        boolean allowTeleport = manager.config.allowPartyTeleport;
        client.prepareForMultiplayer();
        if (client.getConnection() != null) client.getConnection().prepareKeyPair();
        server.execute(() -> {
            if (context.cancelled.get()) return;
            if (server.isPublished()) { context.stage = "ALREADY_SHARED"; manager.message("already_shared"); return; }
            try {
                InetAddress loopback = InetAddress.getByName("127.0.0.1");
                server.getConnection().startTcpServerListener(loopback, 0);
                context.ownsListener.set(true);
                context.stage = "LISTENING";
                var channels = ((ServerConnectionAccess) server.getConnection()).party$channels();
                InetSocketAddress address;
                synchronized (channels) {
                    address = channels.stream().map(c -> c.channel().localAddress())
                            .filter(a -> a instanceof InetSocketAddress).map(a -> (InetSocketAddress) a)
                            .filter(a -> a.getAddress().isLoopbackAddress()).findFirst().orElseThrow();
                }
                ((IntegratedServerAccess) server).party$setPublishedPort(address.getPort());
                ((IntegratedServerAccess) server).party$setScope(MinecraftServer.MultiplayerScope.LAN);
                server.setCommandsAllowedForOtherPlayers(false);
                if (context.cancelled.get()) { server.unpublishServer(); return; }
                manager.permissions.start(server, hostId, members, allowTeleport);
                if (context.cancelled.get()) { manager.permissions.stop(server); server.unpublishServer(); return; }
                // The same serialized worker also handles detach, so a cancelled publication
                // cannot reopen a world after its close task has completed.
                manager.background(() -> {
                    if (!context.cancelled.get()) {
                        host.openWorld(address, context.worldId); context.stage = "ANNOUNCED"; manager.message("sharing");
                    }
                });
            } catch (Exception e) {
                context.stage = "FAILED"; context.failure = e.getClass().getSimpleName();
                manager.permissions.stop(server);
                if (context.ownsListener.get()) {
                    server.getConnection().stopTcpServerListener();
                    ((IntegratedServerAccess) server).party$setPublishedPort(-1);
                    ((IntegratedServerAccess) server).party$setScope(MinecraftServer.MultiplayerScope.OFF);
                }
                manager.message("share_failed");
            }
        });
    }
    public void detach() {
        Context context = attached; attached = null;
        idleStage = "DETACHED";
        if (context == null) return;
        context.cancelled.set(true);
        manager.permissions.stop(context.server);
        manager.background(() -> context.host.closeWorld(context.worldId));
        if (context.server.isRunning()) context.server.execute(() -> {
            if (context.ownsListener.get()) context.server.unpublishServer();
        });
    }
    public void serverStopping(MinecraftServer server) {
        retired = server;
        if (attached != null && attached.server == server) detach();
    }
    public String diagnostics() {
        Context context = attached;
        return "World bridge stage: " + (context == null ? idleStage : context.stage)
                + "\nWorld bridge failure: " + (context == null ? "NONE" : context.failure)
                + "\nWorld bridge listener owned: " + (context != null && context.ownsListener.get()) + "\n";
    }
    private static final class Context {
        final IntegratedServer server;
        final HostNode host;
        final UUID worldId = UUID.randomUUID();
        final AtomicBoolean cancelled = new AtomicBoolean(), ownsListener = new AtomicBoolean();
        volatile String stage = "PUBLISHING", failure = "NONE";
        Context(IntegratedServer server, HostNode host) { this.server = server; this.host = host; }
    }
}
