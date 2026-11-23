package pl.minecraftparty.world;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/** Rebinds the imported copy's singleplayer owner without giving the new host somebody else's player data. */
public final class WorldOwnership {
    private static final long NBT_LIMIT = 32L << 20;
    private WorldOwnership() { }
    public static void rebind(Path world, UUID previous, UUID next) throws IOException {
        Path primary = world.resolve("level.dat"); CompoundTag root = read(primary);
        CompoundTag data = root.getCompound("Data").orElseThrow(() -> new IOException("level data missing"));
        Path players = world.resolve(LevelResource.PLAYER_DATA_DIR.id()); Files.createDirectories(players);
        Path oldFile = players.resolve(previous + ".dat");
        if (!Files.isRegularFile(oldFile, LinkOption.NOFOLLOW_LINKS)) {
            Path legacyFile = world.resolve("playerdata").resolve(previous + ".dat");
            if (Files.isRegularFile(legacyFile, LinkOption.NOFOLLOW_LINKS)) Files.copy(legacyFile, oldFile);
            else {
                CompoundTag legacy = data.getCompound("Player").orElseThrow(() -> new IOException("previous player data missing"));
                UUID id = legacy.read("UUID", UUIDUtil.CODEC).orElseThrow(() -> new IOException("legacy player identity missing"));
                if (!previous.equals(id)) throw new IOException("legacy player identity mismatch");
                NbtIo.writeCompressed(legacy, oldFile);
            }
        }
        Path nextFile = players.resolve(next + ".dat");
        if (!Files.isRegularFile(nextFile, LinkOption.NOFOLLOW_LINKS)) {
            Path legacy = world.resolve("playerdata").resolve(next + ".dat");
            if (Files.isRegularFile(legacy, LinkOption.NOFOLLOW_LINKS)) Files.copy(legacy, nextFile);
        }
        // Validate compressed player NBT before publishing; positions, inventories and UUIDs are not rewritten.
        read(oldFile); if (Files.exists(nextFile, LinkOption.NOFOLLOW_LINKS)) read(nextFile);
        rewrite(primary, root, next);
        Path backup = world.resolve("level.dat_old");
        if (Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)) rewrite(backup, read(backup), next);
    }
    private static CompoundTag read(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("world data file");
        try { return NbtIo.readCompressed(path, NbtAccounter.create(NBT_LIMIT)); }
        catch (RuntimeException e) { throw new IOException("invalid world NBT", e); }
    }
    private static void rewrite(Path path, CompoundTag root, UUID next) throws IOException {
        CompoundTag data = root.getCompound("Data").orElseThrow(() -> new IOException("level data missing"));
        data.remove("Player"); data.store("singleplayer_uuid", UUIDUtil.CODEC, next);
        NbtIo.writeCompressed(root, path);
    }
}
