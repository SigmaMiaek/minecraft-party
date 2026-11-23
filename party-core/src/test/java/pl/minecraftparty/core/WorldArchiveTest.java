package pl.minecraftparty.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

final class WorldArchiveTest {
    @TempDir Path temporary;
    @Test void closedWorldFilesRoundTripAndTheSessionLockIsNotCopied() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("world"));
        Files.write(source.resolve("level.dat"), new byte[]{1,2,3}); Files.writeString(source.resolve("session.lock"), "locked");
        Path region = Files.createDirectories(source.resolve("dimensions/custom/moon/region"));
        byte[] chunks = new byte[131072]; new Random(42).nextBytes(chunks); Files.write(region.resolve("r.0.0.mca"), chunks);
        Path zip = temporary.resolve("world.zip"); var info = WorldArchive.pack(source, zip, "Survival", UUID.randomUUID(), () -> false, n -> {});
        assertEquals(2, info.entries()); assertEquals(131075, info.unpacked());
        Path copy = temporary.resolve("copy"); WorldArchive.extract(zip, copy, info, () -> false, n -> {});
        assertArrayEquals(chunks, Files.readAllBytes(copy.resolve("dimensions/custom/moon/region/r.0.0.mca")));
        assertFalse(Files.exists(copy.resolve("session.lock"))); assertTrue(Files.exists(source.resolve("session.lock")));
        assertFalse(info.toString().contains(info.sha256()));
    }
    @Test void traversalAbsoluteWindowsAndDevicePathsNeverEscapeTheStagingDirectory() throws Exception {
        for (String name : List.of("../escape", "/absolute", "C:/escape", "dir\\escape", "a/../../escape", "dir/CON.txt", "name. ", "a//b")) {
            Path zip = archive(List.of(new Entry("level.dat", new byte[]{1}), new Entry(name, new byte[]{2})));
            Path stage = temporary.resolve("stage-" + UUID.randomUUID());
            assertThrows(IOException.class, () -> WorldArchive.extract(zip, stage, info(zip, 2, 2), () -> false, n -> {}), name);
            assertFalse(Files.exists(stage)); assertFalse(Files.exists(temporary.resolve("escape")));
        }
    }
    @Test void duplicatesAndCaseInsensitiveParentCollisionsAreRejectedOnEveryOs() throws Exception {
        for (List<Entry> entries : List.of(List.of(new Entry("level.dat", new byte[]{1}), new Entry("LEVEL.DAT", new byte[]{2})),
                List.of(new Entry("level.dat", new byte[]{1}), new Entry("Folder/a", new byte[]{2}), new Entry("folder/b", new byte[]{3})))) {
            Path zip = archive(entries); Path stage = temporary.resolve("case-" + UUID.randomUUID());
            assertThrows(IOException.class, () -> WorldArchive.extract(zip, stage, info(zip, entries.size(), entries.size()), () -> false, n -> {}));
            assertFalse(Files.exists(stage));
        }
    }
    @Test void expansionLimitsChecksumsAndExistingWorldsAreEnforced() throws Exception {
        Path zip = archive(List.of(new Entry("level.dat", new byte[]{1}), new Entry("big", new byte[65536])));
        Path stage = temporary.resolve("bomb");
        assertThrows(IOException.class, () -> WorldArchive.extract(zip, stage, info(zip, 2, 2), () -> false, n -> {}));
        assertFalse(Files.exists(stage));
        var good = info(zip, 65537, 2); byte[] bytes = Files.readAllBytes(zip); bytes[bytes.length / 2] ^= 1; Files.write(zip, bytes);
        assertThrows(IOException.class, () -> WorldArchive.extract(zip, stage, good, () -> false, n -> {})); assertFalse(Files.exists(stage));
        Path existing = Files.createDirectory(temporary.resolve("existing")); Files.writeString(existing.resolve("level.dat"), "original");
        Path valid = archive(List.of(new Entry("level.dat", new byte[]{1})));
        assertThrows(FileAlreadyExistsException.class, () -> WorldArchive.extract(valid, existing, info(valid, 1, 1), () -> false, n -> {}));
        assertEquals("original", Files.readString(existing.resolve("level.dat")));
    }
    @Test void cancelledPackingRemovesOnlyItsOwnPartialArchive() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("cancel-world")); Files.writeString(source.resolve("level.dat"), "saved");
        Files.write(source.resolve("chunks"), new byte[131072]); AtomicBoolean cancelled = new AtomicBoolean(); Path zip = temporary.resolve("cancel.zip");
        assertThrows(InterruptedIOException.class, () -> WorldArchive.pack(source, zip, "World", UUID.randomUUID(), cancelled::get, n -> cancelled.set(true)));
        assertFalse(Files.exists(zip)); assertEquals("saved", Files.readString(source.resolve("level.dat")));
        Files.writeString(zip, "existing archive");
        assertThrows(FileAlreadyExistsException.class, () -> WorldArchive.pack(source, zip, "World", UUID.randomUUID(), () -> false, n -> {}));
        assertEquals("existing archive", Files.readString(zip));
    }
    @Test void symbolicLinksCannotExposeFilesOutsideTheWorld() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("linked-world")); Files.writeString(source.resolve("level.dat"), "saved");
        Path outside = temporary.resolve("private.txt"); Files.writeString(outside, "private");
        try { Files.createSymbolicLink(source.resolve("link"), outside); }
        catch (IOException | UnsupportedOperationException e) { Assumptions.abort("OS does not permit test symlinks"); }
        assertThrows(IOException.class, () -> WorldArchive.pack(source, temporary.resolve("link.zip"), "World", UUID.randomUUID(), () -> false, n -> {}));
    }
    private record Entry(String name, byte[] bytes) { }
    private Path archive(List<Entry> entries) throws IOException {
        Path path = temporary.resolve(UUID.randomUUID() + ".zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Entry e : entries) { zip.putNextEntry(new ZipEntry(e.name())); zip.write(e.bytes()); zip.closeEntry(); }
        }
        return path;
    }
    private static WorldArchive.Info info(Path zip, long unpacked, int entries) throws Exception {
        return new WorldArchive.Info("World", UUID.randomUUID(), Files.size(zip), unpacked, entries,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(zip))));
    }
}
