package pl.minecraftparty.core;

import java.util.*;

/** Host-owned, in-memory grants for one integrated world; never persisted to ops.json. */
public final class WorldPermissionPolicy implements AutoCloseable {
    private final UUID host;
    private Set<UUID> members;
    private final Set<UUID> operators = new HashSet<>();
    private boolean teleport, closed;

    public WorldPermissionPolicy(UUID host, Set<UUID> members, boolean teleport) {
        this.host = Objects.requireNonNull(host); this.members = Set.copyOf(members); this.teleport = teleport;
        if (!members.contains(host)) throw new IllegalArgumentException("host must be a member");
    }
    public synchronized void members(Set<UUID> next) {
        if (closed) return;
        if (!next.contains(host)) throw new IllegalArgumentException("host must be a member");
        members = Set.copyOf(next); operators.retainAll(next);
    }
    public synchronized void operator(UUID actor, UUID player, boolean value) {
        requireHost(actor);
        if (player.equals(host) || !members.contains(player)) throw new SecurityException("not a guest");
        if (value) operators.add(player); else operators.remove(player);
    }
    public synchronized void teleport(UUID actor, boolean value) { requireHost(actor); teleport = value; }
    public synchronized boolean isOperator(UUID player) { return !closed && members.contains(player) && operators.contains(player); }
    public synchronized boolean canTeleport(UUID player) { return !closed && members.contains(player) && (teleport || operators.contains(player)); }
    public synchronized Set<UUID> operators() { return Set.copyOf(operators); }
    private void requireHost(UUID actor) { if (closed || !host.equals(actor)) throw new SecurityException("host only"); }
    @Override public synchronized void close() { closed = true; operators.clear(); members = Set.of(); teleport = false; }
}
