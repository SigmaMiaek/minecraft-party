package pl.minecraftparty.world;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;
import net.minecraft.world.level.storage.LevelResource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class WorldOwnershipTest {
    @TempDir Path world;
    private final UUID previous = UUID.randomUUID(), next = UUID.randomUUID();
    @Test void modernOwnerPointerChangesWhileBothInventoriesAndStatsRemainUntouched() throws Exception {
        CompoundTag root = level(); NbtIo.writeCompressed(root, world.resolve("level.dat")); NbtIo.writeCompressed(root.copy(), world.resolve("level.dat_old"));
        Path players = Files.createDirectories(world.resolve(LevelResource.PLAYER_DATA_DIR.id()));
        Path a = players.resolve(previous + ".dat"), b = players.resolve(next + ".dat");
        NbtIo.writeCompressed(player(previous, "old inventory"), a); NbtIo.writeCompressed(player(next, "new inventory"), b);
        byte[] oldBytes = Files.readAllBytes(a), newBytes = Files.readAllBytes(b);
        Path stats = Files.createDirectories(world.resolve(LevelResource.PLAYER_STATS_DIR.id())); Files.writeString(stats.resolve(previous + ".json"), "old stats");
        WorldOwnership.rebind(world, previous, next);
        for (String file : new String[]{"level.dat", "level.dat_old"}) {
            var loaded = NbtIo.readCompressed(world.resolve(file), NbtAccounter.defaultQuota());
            assertEquals(next, loaded.getCompoundOrEmpty("Data").read("singleplayer_uuid", UUIDUtil.CODEC).orElseThrow());
            assertEquals(42, loaded.getCompoundOrEmpty("Data").getIntOr("UnrelatedWorldData", 0));
        }
        assertArrayEquals(oldBytes, Files.readAllBytes(a)); assertArrayEquals(newBytes, Files.readAllBytes(b));
        assertEquals("old stats", Files.readString(stats.resolve(previous + ".json")));
    }
    @Test void legacyEmbeddedOwnerDataIsPreservedAndNeverGivenToTheNewOwner() throws Exception {
        CompoundTag root = level(); root.getCompoundOrEmpty("Data").put("Player", player(previous, "latest old inventory"));
        NbtIo.writeCompressed(root, world.resolve("level.dat"));
        var backup = root.copy(); backup.getCompoundOrEmpty("Data").put("Player", player(previous, "stale inventory")); NbtIo.writeCompressed(backup, world.resolve("level.dat_old"));
        WorldOwnership.rebind(world, previous, next);
        Path players = world.resolve(LevelResource.PLAYER_DATA_DIR.id());
        var old = NbtIo.readCompressed(players.resolve(previous + ".dat"), NbtAccounter.defaultQuota());
        assertEquals("latest old inventory", old.getStringOr("TestInventory", ""));
        assertFalse(Files.exists(players.resolve(next + ".dat")));
        var loaded = NbtIo.readCompressed(world.resolve("level.dat"), NbtAccounter.defaultQuota()).getCompoundOrEmpty("Data");
        assertFalse(loaded.contains("Player")); assertEquals(next, loaded.read("singleplayer_uuid", UUIDUtil.CODEC).orElseThrow());
    }
    @Test void missingPreviousPlayerDataStopsImportInsteadOfSilentlyResettingIt() throws Exception {
        NbtIo.writeCompressed(level(), world.resolve("level.dat"));
        assertThrows(java.io.IOException.class, () -> WorldOwnership.rebind(world, previous, next));
        assertEquals(previous, NbtIo.readCompressed(world.resolve("level.dat"), NbtAccounter.defaultQuota())
                .getCompoundOrEmpty("Data").read("singleplayer_uuid", UUIDUtil.CODEC).orElseThrow());
    }
    private CompoundTag level() { var root = new CompoundTag(); var data = new CompoundTag(); data.store("singleplayer_uuid", UUIDUtil.CODEC, previous); data.putInt("UnrelatedWorldData", 42); root.put("Data", data); return root; }
    private static CompoundTag player(UUID id, String inventory) { var player = new CompoundTag(); player.store("UUID", UUIDUtil.CODEC, id); player.putString("TestInventory", inventory); return player; }
}
