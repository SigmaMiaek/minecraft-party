package pl.minecraftparty.world;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;
import net.minecraft.world.level.storage.LevelResource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import pl.minecraftparty.core.WorldArchive;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class WorldImportTest {
    @TempDir Path temporary;
    @Test void newSaveIsPublishedWithTheNewOwnerAndTheOriginalIsNeverModified() throws Exception {
        UUID old = UUID.randomUUID(), next = UUID.randomUUID(), tx = UUID.randomUUID();
        Path source = Files.createDirectory(temporary.resolve("original"));
        CompoundTag root = new CompoundTag(), data = new CompoundTag(); data.store("singleplayer_uuid", UUIDUtil.CODEC, old); root.put("Data", data);
        NbtIo.writeCompressed(root, source.resolve("level.dat"));
        Path players = Files.createDirectories(source.resolve(LevelResource.PLAYER_DATA_DIR.id()));
        CompoundTag player = new CompoundTag(); player.putInt("TestPosition", 123); NbtIo.writeCompressed(player, players.resolve(old + ".dat"));
        byte[] original = Files.readAllBytes(source.resolve("level.dat"));
        Path archive = temporary.resolve("copy.zip"); var info = WorldArchive.pack(source, archive, "World", old, () -> false, n -> {});
        Path saves = Files.createDirectory(temporary.resolve("saves")); Path existing = Files.createDirectory(saves.resolve("Existing")); Files.writeString(existing.resolve("marker"), "keep");
        String folder = WorldImport.install(archive, saves, tx, info, next, () -> false, n -> {});
        var imported = NbtIo.readCompressed(saves.resolve(folder).resolve("level.dat"), NbtAccounter.defaultQuota());
        assertEquals(next, imported.getCompoundOrEmpty("Data").read("singleplayer_uuid", UUIDUtil.CODEC).orElseThrow());
        assertArrayEquals(original, Files.readAllBytes(source.resolve("level.dat"))); assertEquals("keep", Files.readString(existing.resolve("marker")));
        byte[] received = Files.readAllBytes(saves.resolve(folder).resolve("level.dat"));
        assertThrows(FileAlreadyExistsException.class, () -> WorldImport.install(archive, saves, tx, info, next, () -> false, n -> {}));
        assertArrayEquals(received, Files.readAllBytes(saves.resolve(folder).resolve("level.dat")));
    }
    @Test void invalidWorldDataLeavesNoVisibleSaveAndDoesNotDeleteAnotherStagingDirectory() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("invalid")); Files.writeString(source.resolve("level.dat"), "not NBT");
        Path archive = temporary.resolve("bad.zip"); var info = WorldArchive.pack(source, archive, "World", UUID.randomUUID(), () -> false, n -> {});
        Path saves = Files.createDirectory(temporary.resolve("saves")); UUID tx = UUID.randomUUID();
        assertThrows(java.io.IOException.class, () -> WorldImport.install(archive, saves, tx, info, UUID.randomUUID(), () -> false, n -> {}));
        assertFalse(Files.exists(saves.resolve("Party-" + tx))); assertFalse(Files.exists(saves.resolve(".minecraft-party-imports").resolve(tx.toString())));
        Path stage = Files.createDirectory(saves.resolve(".minecraft-party-imports").resolve(tx.toString())); Files.writeString(stage.resolve("marker"), "another import");
        assertThrows(FileAlreadyExistsException.class, () -> WorldImport.install(archive, saves, tx, info, UUID.randomUUID(), () -> false, n -> {}));
        assertEquals("another import", Files.readString(stage.resolve("marker")));
    }
}
