package pl.minecraftparty.core;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class WorldPacketTest {
    @Test void consentArchiveAndCancellationPacketsRoundTripAndRejectTruncation() throws Exception {
        UUID tx = UUID.randomUUID(); var info = new WorldArchive.Info("Survival", UUID.randomUUID(), 100, 1000, 2, "a".repeat(64));
        for (WorldPacket packet : List.of(new WorldPacket.Offer(tx, "Survival"), new WorldPacket.Accept(tx), new WorldPacket.Archive(tx, info),
                new WorldPacket.Imported(tx), new WorldPacket.Cancel(tx), new WorldPacket.Saving(tx))) {
            var bytes = new ByteArrayOutputStream(); WorldPacket.write(new DataOutputStream(bytes), packet); byte[] data = bytes.toByteArray();
            var input = new DataInputStream(new ByteArrayInputStream(data)); assertEquals(packet, WorldPacket.read(input.readUnsignedByte(), input));
            for (int i = 0; i < data.length; i++) {
                byte[] truncated = Arrays.copyOf(data, i);
                assertThrows(IOException.class, () -> { var in = new DataInputStream(new ByteArrayInputStream(truncated)); WorldPacket.read(in.readUnsignedByte(), in); });
            }
        }
    }
    @Test void archiveMetadataIsBoundedBeforeAnyFileIsCreated() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new WorldArchive.Info("World", id, WorldArchive.MAX_COMPRESSED + 1, 100, 1, "a".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new WorldArchive.Info("World", id, 100, WorldArchive.MAX_UNPACKED + 1, 1, "a".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new WorldArchive.Info("World", id, 100, 100, WorldArchive.MAX_ENTRIES + 1, "a".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new WorldArchive.Info("World", id, 100, 100, 1, "not a hash"));
    }
}
