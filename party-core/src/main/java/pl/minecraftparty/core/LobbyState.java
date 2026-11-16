package pl.minecraftparty.core;

import java.util.List;
import java.util.UUID;

/** Immutable snapshot. No peer addresses or credentials; the host may publish a shared Minecraft server destination. */
public record LobbyState(UUID partyId, String name, int limit, long revision, UUID worldId,
                         MultiplayerTarget multiplayerTarget, List<Member> members, List<ChatMessage> chat) {
    public LobbyState {
        members = List.copyOf(members); chat = List.copyOf(chat);
        if (worldId == null && multiplayerTarget != null) throw new IllegalArgumentException("session required");
    }
    public LobbyState(UUID partyId, String name, int limit, long revision, UUID worldId, List<Member> members) {
        this(partyId, name, limit, revision, worldId, null, members, List.of());
    }
    public record Member(UUID id, String name, boolean host, Status status) { }
    public record ChatMessage(long sequence, UUID senderId, String senderName, String text) { }
    public enum Status { LOBBY, CONNECTING, IN_WORLD, DISCONNECTED }
}
