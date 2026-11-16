package pl.minecraftparty.core;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftAdmissionTest {
    @Test void vanillaLoginPrefixIsPreservedExactly() throws Exception {
        UUID id = UUID.randomUUID(); byte[] prefix = login(id, "Guest");
        var output = new ByteArrayOutputStream();
        MinecraftAdmission.forward(new ByteArrayInputStream(prefix), output, id, "Guest");
        assertArrayEquals(prefix, output.toByteArray());
    }
    @Test void authenticatedGuestCannotImpersonateHostOrAnotherUuid() throws Exception {
        UUID id = UUID.randomUUID();
        assertThrows(IOException.class, () -> MinecraftAdmission.forward(new ByteArrayInputStream(login(id, "Host")),
                new ByteArrayOutputStream(), id, "Guest"));
        assertThrows(IOException.class, () -> MinecraftAdmission.forward(new ByteArrayInputStream(login(UUID.randomUUID(), "Guest")),
                new ByteArrayOutputStream(), id, "Guest"));
    }
    @Test void oversizedPacketsAndVarintsAreRejected() {
        assertThrows(IOException.class, () -> MinecraftAdmission.forward(new ByteArrayInputStream(new byte[]{-1,-1,-1,-1,127}),
                new ByteArrayOutputStream(), UUID.randomUUID(), "Guest"));
        assertThrows(IOException.class, () -> MinecraftAdmission.varInt(new DataInputStream(new ByteArrayInputStream(new byte[]{-1,-1,-1,-1,127}))));
    }
    static byte[] login(UUID id, String name) throws IOException {
        var handshake = new ByteArrayOutputStream(); var h = new DataOutputStream(handshake);
        MinecraftAdmission.varInt(h, 0); MinecraftAdmission.varInt(h, 999);
        utf8(h, "localhost"); h.writeShort(25565); MinecraftAdmission.varInt(h, 2);
        var login = new ByteArrayOutputStream(); var l = new DataOutputStream(login);
        MinecraftAdmission.varInt(l, 0); utf8(l, name); Wire.uuid(l, id);
        var output = new ByteArrayOutputStream(); var out = new DataOutputStream(output);
        MinecraftAdmission.varInt(out, handshake.size()); out.write(handshake.toByteArray());
        MinecraftAdmission.varInt(out, login.size()); out.write(login.toByteArray()); return output.toByteArray();
    }
    private static void utf8(DataOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8); MinecraftAdmission.varInt(out, bytes.length); out.write(bytes);
    }
}
