package pl.minecraftparty.world;

import pl.minecraftparty.core.WorldArchive;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import java.util.function.*;

/** Makes a verified save visible only after extraction and ownership repair both succeed. */
public final class WorldImport {
    private WorldImport() { }
    public static String install(Path archive, Path saves, UUID transaction, WorldArchive.Info info, UUID owner,
                                 BooleanSupplier cancelled, LongConsumer progress) throws IOException {
        String folder = "Party-" + transaction; Path destination = saves.resolve(folder);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(folder);
        Path imports = saves.resolve(".minecraft-party-imports"); Files.createDirectories(imports);
        if (Files.isSymbolicLink(imports)) throw new IOException("import directory link");
        Path stage = imports.resolve(transaction.toString()); boolean owned = false;
        try {
            WorldArchive.extract(archive, stage, info, cancelled, progress); owned = true;
            WorldOwnership.rebind(stage, info.owner(), owner); WorldArchive.checkCancelled(cancelled);
            // Same parent filesystem, unique destination, no merging or replacement of existing worlds.
            Files.move(stage, destination, StandardCopyOption.ATOMIC_MOVE); owned = false;
            return folder;
        } finally { if (owned) WorldArchive.deleteTree(stage); }
    }
}
