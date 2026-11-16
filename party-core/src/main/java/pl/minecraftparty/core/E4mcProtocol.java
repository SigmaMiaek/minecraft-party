package pl.minecraftparty.core;

import java.io.*;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Routing preface for the public e4mc Minecraft gateway, followed by Party TLS, not a game login. */
public final class E4mcProtocol {
    public static final int PORT = 25565, MAX_ROUTING_PACKET = 512, MAX_CONTROL = 8192;
    private E4mcProtocol() { }
    public static String domain(String input) {
        if (input == null || input.length() > 253) throw new IllegalArgumentException("relay domain");
        String value = IDN.toASCII(input, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        if (value.length() > 253 || !value.endsWith(".e4mc.link")) throw new IllegalArgumentException("relay domain");
        for (String label : value.split("\\.", -1))
            if (!label.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) throw new IllegalArgumentException("relay domain");
        return value;
    }
    public static byte[] routingPreface(String host) throws IOException {
        byte[] address = domain(host).getBytes(StandardCharsets.US_ASCII);
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
        writeVarInt(out, 0); writeVarInt(out, 1); // routing handshake; real game protocol is inside TLS
        writeVarInt(out, address.length); out.write(address); out.writeShort(PORT); writeVarInt(out, 2);
        byte[] packet = bytes.toByteArray(); bytes.reset(); writeVarInt(out, packet.length); out.write(packet);
        return bytes.toByteArray();
    }
    public static void validateRoutingPacket(byte[] packet, String expectedHost) throws IOException {
        if (packet.length > MAX_ROUTING_PACKET) throw new IOException("routing packet too long");
        var in = new DataInputStream(new ByteArrayInputStream(packet));
        if (readVarInt(in) != 0 || readVarInt(in) < 0) throw new IOException("routing handshake");
        int length = readVarInt(in);
        if (length < 1 || length > 253 || length > in.available()) throw new IOException("routing domain");
        String host = new String(in.readNBytes(length), StandardCharsets.US_ASCII);
        if (!host.equals(expectedHost) || in.readUnsignedShort() != PORT || readVarInt(in) != 2 || in.available() != 0)
            throw new IOException("routing destination");
    }
    public static void writeVarInt(DataOutput out, int value) throws IOException {
        if (value < 0) throw new IOException("negative length");
        do { int next = value & 127; value >>>= 7; out.writeByte(value == 0 ? next : next | 128); } while (value != 0);
    }
    public static int readVarInt(DataInput in) throws IOException {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            int next = in.readUnsignedByte();
            if (i == 4 && (next & 0xF0) != 0) throw new IOException("invalid varint");
            value |= (next & 127) << (7 * i); if ((next & 128) == 0) return value;
        }
        throw new IOException("varint too long");
    }
}
