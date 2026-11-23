package pl.minecraftparty.core;

import java.io.*;
import java.util.*;

/** Bounded, versioned binary protocol; no Java deserialization or remote class names. */
final class Wire {
    static final int MAGIC = 0x4d505430 + PartyProtocol.VERSION; // MPT7: vanilla Party and authenticated world handoff.
    static final int CONTROL = 1, TUNNEL = 2, WORLD_FILE = 3;
    static final int WELCOME = 1, SNAPSHOT = 2, ERROR = 3, RETRY = 4;
    static final int PING = 1, STATUS = 2, LEAVE = 3, CHAT = 4;
    private Wire() { }
    static void uuid(DataOutput out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    static UUID uuid(DataInput in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    static String text(DataInput in, int max) throws IOException {
        // Read the prefix first: reject oversized payloads before allocating their bodies.
        int bytes = in.readUnsignedShort();
        if (bytes > max * 3) throw new IOException("text too long");
        byte[] body = new byte[bytes + 2]; body[0] = (byte) (bytes >>> 8); body[1] = (byte) bytes;
        in.readFully(body, 2, bytes);
        String value = new DataInputStream(new ByteArrayInputStream(body)).readUTF();
        if (value.length() > max) throw new IOException("text too long");
        return value;
    }
    static void snapshot(DataOutput out, LobbyState state) throws IOException {
        uuid(out, state.partyId()); out.writeUTF(state.name()); out.writeInt(state.limit()); out.writeLong(state.revision());
        out.writeBoolean(state.publicLobby());
        out.writeBoolean(state.worldId() != null);
        if (state.worldId() != null) uuid(out, state.worldId());
        out.writeBoolean(state.multiplayerTarget() != null);
        if (state.multiplayerTarget() != null) out.writeUTF(state.multiplayerTarget().address());
        out.writeInt(state.members().size());
        for (var m : state.members()) {
            uuid(out, m.id()); out.writeUTF(m.name()); out.writeBoolean(m.host()); out.writeByte(m.status().ordinal());
        }
        out.writeInt(state.chat().size());
        for (var message : state.chat()) {
            out.writeLong(message.sequence()); uuid(out, message.senderId());
            out.writeUTF(message.senderName()); out.writeUTF(message.text());
        }
    }
    static LobbyState snapshot(DataInput in) throws IOException {
        UUID id = uuid(in); String name = text(in, 40); int limit = in.readInt(); long revision = in.readLong();
        boolean publicLobby = in.readBoolean();
        UUID world = in.readBoolean() ? uuid(in) : null;
        MultiplayerTarget target = null;
        if (in.readBoolean()) {
            try { target = new MultiplayerTarget(text(in, 255)); }
            catch (IllegalArgumentException e) { throw new IOException("server address", e); }
            if (world == null) throw new IOException("session required");
        }
        int count = in.readInt();
        if (limit < 2 || limit > LobbyModel.MAX_MEMBERS || count < 1 || count > limit || revision < 0) throw new IOException("state");
        List<LobbyState.Member> members = new ArrayList<>();
        Set<UUID> ids = new HashSet<>(); int hosts = 0;
        for (int i = 0; i < count; i++) {
            UUID member = uuid(in); String playerName = text(in, 16); boolean host = in.readBoolean();
            if (!ids.add(member) || !playerName.matches("[A-Za-z0-9_]{1,16}")) throw new IOException("member");
            if (host) hosts++;
            members.add(new LobbyState.Member(member, playerName, host, status(in)));
        }
        if (hosts != 1) throw new IOException("host");
        try { LobbyModel.validName(name, 40); } catch (IllegalArgumentException e) { throw new IOException("name", e); }
        int chatCount = in.readInt();
        if (chatCount < 0 || chatCount > LobbyModel.MAX_CHAT_MESSAGES) throw new IOException("chat count");
        List<LobbyState.ChatMessage> chat = new ArrayList<>(); long previousSequence = 0;
        for (int i = 0; i < chatCount; i++) {
            long sequence = in.readLong(); UUID sender = uuid(in);
            String senderName = text(in, 16), message = text(in, LobbyModel.MAX_CHAT_LENGTH);
            if (sequence <= previousSequence || !senderName.matches("[A-Za-z0-9_]{1,16}")) throw new IOException("chat sender");
            try { LobbyModel.validName(message, LobbyModel.MAX_CHAT_LENGTH); }
            catch (IllegalArgumentException e) { throw new IOException("chat text", e); }
            chat.add(new LobbyState.ChatMessage(sequence, sender, senderName, message)); previousSequence = sequence;
        }
        return new LobbyState(id, name, limit, revision, world, target, members, chat, publicLobby);
    }
    static LobbyState.Status status(DataInput in) throws IOException {
        int value = in.readUnsignedByte();
        if (value >= LobbyState.Status.values().length) throw new IOException("status");
        return LobbyState.Status.values()[value];
    }
}
