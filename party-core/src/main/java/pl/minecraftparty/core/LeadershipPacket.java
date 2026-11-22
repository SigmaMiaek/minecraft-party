package pl.minecraftparty.core;

import java.io.*;
import java.util.*;

/** Bounded signaling messages inside Party TLS; no credentials are sent to the code service. */
public sealed interface LeadershipPacket {
    UUID transaction();
    record Prepare(LeadershipPlan plan) implements LeadershipPacket { public UUID transaction() { return plan.transaction(); } }
    record Ready(UUID transaction, Invite invite) implements LeadershipPacket { @Override public String toString() { return "Ready[REDACTED]"; } }
    record Commit(UUID transaction, UUID successor, Invite invite) implements LeadershipPacket { @Override public String toString() { return "Commit[REDACTED]"; } }
    record Abort(UUID transaction) implements LeadershipPacket { }
    record Failed(UUID transaction) implements LeadershipPacket { }
    record Ack(UUID transaction) implements LeadershipPacket { }
    record Starting(UUID transaction) implements LeadershipPacket { }
    static void write(DataOutput out, LeadershipPacket packet) throws IOException {
        int kind = packet instanceof Prepare ? 20 : packet instanceof Ready ? 21 : packet instanceof Commit ? 22
                : packet instanceof Abort ? 23 : packet instanceof Failed ? 24 : packet instanceof Ack ? 25 : 26;
        out.writeByte(kind); Wire.uuid(out, packet.transaction());
        switch (packet) {
            case Prepare p -> {
                LeadershipPlan plan = p.plan(); Wire.uuid(out, plan.successor()); out.writeBoolean(plan.leaving()); Wire.snapshot(out, plan.snapshot());
                out.writeInt(plan.credentials().size());
                for (var entry : plan.credentials().entrySet()) { Wire.uuid(out, entry.getKey()); out.writeUTF(entry.getValue()); }
                out.writeInt(plan.banned().size()); for (UUID id : plan.banned()) Wire.uuid(out, id);
            }
            case Ready p -> out.writeUTF(p.invite().encode());
            case Commit p -> { Wire.uuid(out, p.successor()); out.writeUTF(p.invite().encode()); }
            default -> { }
        }
    }
    static LeadershipPacket read(int kind, DataInput in) throws IOException {
        UUID transaction = Wire.uuid(in);
        try {
            return switch (kind) {
                case 20 -> {
                    UUID successor = Wire.uuid(in); boolean leaving = in.readBoolean(); LobbyState state = Wire.snapshot(in);
                    int count = in.readInt(); if (count < 0 || count > LobbyModel.MAX_MEMBERS) throw new IOException("credentials count");
                    Map<UUID, String> credentials = new HashMap<>();
                    for (int i = 0; i < count; i++) if (credentials.put(Wire.uuid(in), Wire.text(in, 43)) != null) throw new IOException("duplicate credential");
                    int bans = in.readInt(); if (bans < 0 || bans > 1024) throw new IOException("ban count");
                    Set<UUID> banned = new HashSet<>(); for (int i = 0; i < bans; i++) banned.add(Wire.uuid(in));
                    yield new Prepare(new LeadershipPlan(transaction, successor, leaving, state, credentials, banned));
                }
                case 21 -> new Ready(transaction, Invite.decode(Wire.text(in, Invite.MAX_CODE_LENGTH)));
                case 22 -> new Commit(transaction, Wire.uuid(in), Invite.decode(Wire.text(in, Invite.MAX_CODE_LENGTH)));
                case 23 -> new Abort(transaction);
                case 24 -> new Failed(transaction);
                case 25 -> new Ack(transaction);
                case 26 -> new Starting(transaction);
                default -> throw new IOException("migration command");
            };
        } catch (IllegalArgumentException e) { throw new IOException("migration format", e); }
    }
}
