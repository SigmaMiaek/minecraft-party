package pl.minecraftparty.network;

import net.fabricmc.loader.api.FabricLoader;
import pl.minecraftparty.core.LimitedHttpBody;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Properties;
import io.netty.util.internal.PlatformDependent;

/** Only the exact native build whose digest is pinned in the vendored Netty/e4mc source can be loaded. */
public final class RelayNativeLibrary {
    private static final int MAX_NATIVE = 64 * 1024 * 1024;
    private RelayNativeLibrary() { }
    /** Fetch before Quic's class initializer: a transient download failure must not poison that class for this JVM. */
    public static synchronized void prepare() throws IOException {
        String file = System.mapLibraryName("netty_quiche_" + PlatformDependent.normalizedOs() + "_" + PlatformDependent.normalizedArch() + "_74");
        try {
            var hashes = new Properties();
            try (var resource = RelayNativeLibrary.class.getResourceAsStream("/relay-natives.properties")) {
                if (resource == null) throw new IOException("missing native manifest"); hashes.load(resource);
            }
            String expected = hashes.getProperty(file);
            if (expected == null) throw new IOException("unsupported relay platform");
            cached(file, HexFormat.of().parseHex(expected));
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("native download interrupted"); }
        catch (Exception e) { throw new IOException("native preparation failed"); }
    }
    public static synchronized void load(String file, byte[] expected) {
        try { System.load(cached(file, expected).toString()); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new UnsatisfiedLinkError("relay native download interrupted"); }
        catch (Exception e) { throw new UnsatisfiedLinkError("relay native unavailable (" + e.getClass().getSimpleName() + ")"); }
    }
    private static Path cached(String file, byte[] expected) throws Exception {
        if (expected.length != 32 || !file.matches("(?:lib)?netty_quiche_(?:linux|windows|osx)_[a-z0-9_]+_74\\.(?:so|dll|dylib|jnilib)"))
            throw new UnsatisfiedLinkError("unsupported relay platform");
        Path temporary = null;
        try {
            Path directory = FabricLoader.getInstance().getGameDir().resolve(".minecraft-party/cache").toAbsolutePath();
            Files.createDirectories(directory);
            Path cached = directory.resolve(file);
            if (!valid(cached, expected)) {
                try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NORMAL).build()) {
                    var request = HttpRequest.newBuilder(URI.create("https://natives.e4mc.link/" + file))
                            .timeout(Duration.ofSeconds(45)).GET().build();
                    var response = http.send(request, info -> new LimitedHttpBody(MAX_NATIVE));
                    byte[] bytes = response.body();
                    if (response.statusCode() != 200 || !MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256").digest(bytes)))
                        throw new IOException("relay native integrity");
                    temporary = Files.createTempFile(directory, "native-", ".download");
                    Files.write(temporary, bytes);
                    try { Files.move(temporary, cached, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException e) { Files.move(temporary, cached, StandardCopyOption.REPLACE_EXISTING); }
                    temporary = null;
                }
            }
            if (!valid(cached, expected)) throw new IOException("relay native integrity");
            return cached;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
    private static boolean valid(Path file, byte[] expected) throws Exception {
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_NATIVE) return false;
        return MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
