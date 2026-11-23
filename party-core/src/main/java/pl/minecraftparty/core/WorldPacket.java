package pl.minecraftparty.core;

import java.io.*;
import java.util.UUID;

/** World-transfer consent and progress signaling; file bytes use a separate authenticated stream. */
public sealed interface WorldPacket {
    UUID transaction();
    record Offer(UUID transaction, String name) implements WorldPacket {
        public Offer { if (transaction == null) throw new IllegalArgumentException("transaction"); name = LobbyModel.validName(name, 80); }
    }
    record Accept(UUID transaction) implements WorldPacket { }
    record Archive(UUID transaction, WorldArchive.Info info) implements WorldPacket { }
    record Imported(UUID transaction) implements WorldPacket { }
    record Cancel(UUID transaction) implements WorldPacket { }
    record Saving(UUID transaction) implements WorldPacket { }
    static boolean isKind(int kind) { return kind >= 40 && kind <= 45; }
    static void write(DataOutput out, WorldPacket packet) throws IOException {
        int kind = packet instanceof Offer ? 40 : packet instanceof Accept ? 41 : packet instanceof Archive ? 42
                : packet instanceof Imported ? 43 : packet instanceof Cancel ? 44 : 45;
        out.writeByte(kind); Wire.uuid(out, packet.transaction());
        if (packet instanceof Offer p) out.writeUTF(p.name());
        if (packet instanceof Archive p) {
            var info = p.info(); out.writeUTF(info.name()); Wire.uuid(out, info.owner()); out.writeLong(info.bytes());
            out.writeLong(info.unpacked()); out.writeInt(info.entries()); out.writeUTF(info.sha256());
        }
    }
    static WorldPacket read(int kind, DataInput in) throws IOException {
        UUID tx = Wire.uuid(in);
        try {
            return switch (kind) {
                case 40 -> new Offer(tx, Wire.text(in, 80)); case 41 -> new Accept(tx);
                case 42 -> new Archive(tx, new WorldArchive.Info(Wire.text(in, 80), Wire.uuid(in), in.readLong(), in.readLong(), in.readInt(), Wire.text(in, 64)));
                case 43 -> new Imported(tx); case 44 -> new Cancel(tx); case 45 -> new Saving(tx);
                default -> throw new IOException("world transfer command");
            };
        } catch (IllegalArgumentException e) { throw new IOException("world transfer format", e); }
    }
}
