package pl.minecraftparty;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import pl.minecraftparty.core.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Only the local host can change grants. Guest network packets never carry permission mutations. */
public final class PartyWorldPermissions {
    private static volatile Session active;
    private final Consumer<String> message;
    private volatile Set<UUID> operatorView = Set.of();
    private volatile Set<UUID> observedMembers = Set.of();

    PartyWorldPermissions(Consumer<String> message) { this.message = message; }
    public boolean available() { Session session = active; return session != null && session.owner == this; }
    public boolean operator(UUID player) { return operatorView.contains(player); }
    public void start(IntegratedServer server, UUID host, Set<UUID> members, boolean teleport) {
        Session previous = active;
        if (previous != null) previous.policy.close();
        active = new Session(this, server, host, new WorldPermissionPolicy(host, members, teleport));
        operatorView = Set.of(); observedMembers = members;
        refresh(server);
    }
    public void stop(MinecraftServer server) {
        Session session = active;
        if (session == null || session.owner != this || session.server != server) return;
        active = null; session.policy.close(); operatorView = Set.of(); observedMembers = Set.of();
        if (session.server.isRunning()) session.server.execute(() -> refresh(session.server));
    }
    public void update(LobbyState state) {
        Session session = active;
        if (session == null || session.owner != this || state == null) return;
        Set<UUID> members = state.members().stream().map(LobbyState.Member::id).collect(Collectors.toUnmodifiableSet());
        if (members.equals(observedMembers)) return;
        observedMembers = members;
        session.server.execute(() -> {
            if (active != session || !members.contains(session.host)) return;
            session.policy.members(members); operatorView = session.policy.operators(); refresh(session.server);
        });
    }
    public void setOperator(UUID player, boolean grant) {
        Session session = active;
        if (session == null || session.owner != this) return;
        session.server.execute(() -> {
            if (active != session) return;
            ServerPlayer target = session.server.getPlayerList().getPlayer(player);
            if (grant && target == null) { message.accept("permissions_offline"); return; }
            try {
                session.policy.operator(session.host, player, grant);
                operatorView = session.policy.operators(); refresh(session.server);
                message.accept(grant ? "operator_granted" : "operator_revoked");
            } catch (SecurityException e) { message.accept("permissions_unavailable"); }
        });
    }
    public void teleport(boolean value) {
        Session session = active;
        if (session == null || session.owner != this) return;
        session.server.execute(() -> {
            if (active != session) return;
            session.policy.teleport(session.host, value); refresh(session.server);
            message.accept(value ? "teleport_enabled" : "teleport_disabled");
        });
    }
    public void revoke(UUID id) {
        Session session = active;
        if (session == null || session.owner != this) return;
        session.server.execute(() -> {
            if (active != session) return;
            Set<UUID> next = new HashSet<>(observedMembers); next.remove(id);
            if (!next.contains(session.host)) return;
            session.policy.members(next); operatorView = session.policy.operators(); refresh(session.server);
        });
    }
    public static boolean isOperator(MinecraftServer server, UUID player) {
        Session session = active;
        return session != null && session.server == server && session.policy.isOperator(player);
    }
    public static boolean canTeleport(CommandSourceStack source) {
        Session session = active;
        return session != null && session.server == source.getServer() && source.getPlayer() != null
                && session.policy.canTeleport(source.getPlayer().getUUID());
    }
    private static void refresh(IntegratedServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            server.getPlayerList().sendPlayerPermissionLevel(player);
            server.getCommands().sendCommands(player);
        }
    }
    private record Session(PartyWorldPermissions owner, IntegratedServer server, UUID host, WorldPermissionPolicy policy) { }
}
