package pl.minecraftparty.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Inspect only the two cleartext login packets, then relay the untouched vanilla stream. */
final class MinecraftAdmission {
    private MinecraftAdmission() { }
    static void forward(InputStream source, OutputStream target, UUID member, String expectedName) throws IOException {
        var input = new DataInputStream(source);
        byte[] handshake = packet(input, 2048);
        var hello = new DataInputStream(new ByteArrayInputStream(handshake));
        if (varInt(hello) != 0) throw new IOException("handshake");
        varInt(hello); // Protocol version is validated by Minecraft itself.
        string(hello, 255); hello.readUnsignedShort();
        if (varInt(hello) != 2 || hello.available() != 0) throw new IOException("login required");
        // The server sends nothing before login-start; both packets can safely be validated first.
        byte[] login = packet(input, 128);
        var loginIn = new DataInputStream(new ByteArrayInputStream(login));
        if (varInt(loginIn) != 0 || !string(loginIn, 16).equals(expectedName)
                || !Wire.uuid(loginIn).equals(member) || loginIn.available() != 0) throw new IOException("identity mismatch");
        var out = new DataOutputStream(target);
        varInt(out, handshake.length); out.write(handshake);
        varInt(out, login.length); out.write(login); out.flush();
    }
    private static byte[] packet(DataInputStream in, int max) throws IOException {
        int length = varInt(in);
        if (length < 1 || length > max) throw new IOException("packet size");
        byte[] bytes = new byte[length]; in.readFully(bytes); return bytes;
    }
    private static String string(DataInputStream in, int max) throws IOException {
        int length = varInt(in);
        if (length < 0 || length > max * 3) throw new IOException("string size");
        byte[] bytes = new byte[length]; in.readFully(bytes);
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (value.length() > max) throw new IOException("string size");
        return value;
    }
    static int varInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            int next = in.readUnsignedByte();
            if (i == 4 && (next & 0xF0) != 0) throw new IOException("varint overflow");
            value |= (next & 127) << (i * 7);
            if ((next & 128) == 0) return value;
        }
        throw new IOException("varint");
    }
    static void varInt(DataOutputStream out, int value) throws IOException {
        do { int next = value & 127; value >>>= 7; out.writeByte(value == 0 ? next : next | 128); } while (value != 0);
    }
}
