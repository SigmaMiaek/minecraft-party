package pl.minecraftparty.core;

import java.util.*;

/** Delivered only to the selected successor over the authenticated TLS control channel. */
public record LeadershipPlan(UUID transaction, UUID successor, boolean leaving, LobbyState snapshot,
                             Map<UUID, String> credentials, Set<UUID> banned) {
    public LeadershipPlan {
        Objects.requireNonNull(transaction); Objects.requireNonNull(successor); Objects.requireNonNull(snapshot);
        credentials = Map.copyOf(credentials); banned = Set.copyOf(banned);
        if (snapshot.worldId() != null || snapshot.members().size() > LobbyModel.MAX_MEMBERS || banned.size() > 1024)
            throw new IllegalArgumentException("migration state");
        if (snapshot.members().stream().filter(LobbyState.Member::host).count() != 1
                || snapshot.members().stream().noneMatch(m -> m.id().equals(successor) && !m.host() && m.status() != LobbyState.Status.DISCONNECTED))
            throw new IllegalArgumentException("successor");
        Set<UUID> expected = new HashSet<>();
        snapshot.members().stream().filter(m -> !m.id().equals(successor) && !(leaving && m.host())).forEach(m -> expected.add(m.id()));
        if (!expected.equals(credentials.keySet()) || credentials.values().stream().anyMatch(c -> !c.matches("[A-Za-z0-9_-]{43}")))
            throw new IllegalArgumentException("migration credentials");
    }
    public UUID previousLeader() { return snapshot.members().stream().filter(LobbyState.Member::host).findFirst().orElseThrow().id(); }
    @Override public String toString() { return "LeadershipPlan[REDACTED]"; }
}
