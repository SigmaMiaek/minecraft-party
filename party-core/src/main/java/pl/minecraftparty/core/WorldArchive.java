package pl.minecraftparty.core;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import java.util.function.*;
import java.util.zip.*;

/** Bounded streaming world archives. The caller must hold the stopped world's directory lock. */
public final class WorldArchive {
    public static final long MAX_COMPRESSED = 8L << 30, MAX_UNPACKED = 32L << 30;
    public static final int MAX_ENTRIES = 200_000;
    public record Info(String name, UUID owner, long bytes, long unpacked, int entries, String sha256) {
        public Info {
            name = LobbyModel.validName(name, 80);
            if (owner == null || bytes <= 0 || bytes > MAX_COMPRESSED || unpacked <= 0 || unpacked > MAX_UNPACKED
                    || entries < 1 || entries > MAX_ENTRIES || sha256 == null || !sha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("world archive");
        }
        @Override public String toString() { return "WorldArchive.Info[REDACTED]"; }
    }
    private record Source(Path path, String name, long size, Object key, long modified) { }
    private WorldArchive() { }
    public static Info pack(Path world, Path output, String name, UUID owner, BooleanSupplier cancelled, LongConsumer progress) throws IOException {
        LobbyModel.validName(name, 80); Objects.requireNonNull(owner);
        Path root = world.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(root.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)) throw new IOException("world directory");
        List<Source> sources = new ArrayList<>(); long[] total = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                checkCancelled(cancelled); if (Files.isSymbolicLink(dir)) throw new IOException("world contains links"); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                checkCancelled(cancelled);
                if (root.relativize(file).toString().equals("session.lock")) return FileVisitResult.CONTINUE;
                if (!attrs.isRegularFile() || attrs.isSymbolicLink()) throw new IOException("world contains links or special files");
                String relative = root.relativize(file).toString().replace(File.separatorChar, '/'); validateName(relative);
                if (sources.size() >= MAX_ENTRIES || attrs.size() > MAX_UNPACKED - total[0]) throw new IOException("world too large");
                total[0] += attrs.size(); sources.add(new Source(file, relative, attrs.size(), attrs.fileKey(), attrs.lastModifiedTime().toMillis()));
                return FileVisitResult.CONTINUE;
            }
        });
        if (total[0] <= 0) throw new IOException("empty world");
        MessageDigest digest = sha256();
        // Opening is outside cleanup: a pre-existing destination belongs to somebody else.
        OutputStream created = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try (var file = created;
             var bounded = new FilterOutputStream(new DigestOutputStream(file, digest)) {
                 long written;
                 @Override public void write(int b) throws IOException { if (++written > MAX_COMPRESSED) throw new IOException("archive too large"); out.write(b); }
                 @Override public void write(byte[] b, int off, int length) throws IOException {
                     if (length > MAX_COMPRESSED - written) throw new IOException("archive too large"); written += length; out.write(b, off, length);
                 }
             };
             var zip = new ZipOutputStream(new BufferedOutputStream(bounded, 65536))) {
            zip.setLevel(1); byte[] buffer = new byte[65536]; long copied = 0;
            for (Source source : sources) {
                checkCancelled(cancelled); ZipEntry entry = new ZipEntry(source.name()); entry.setTime(source.modified()); zip.putNextEntry(entry);
                long size = 0;
                try (InputStream input = Files.newInputStream(source.path(), LinkOption.NOFOLLOW_LINKS)) {
                    for (int count; (count = input.read(buffer)) >= 0;) {
                        checkCancelled(cancelled); if (count > source.size() - size) throw new IOException("world changed during archive");
                        zip.write(buffer, 0, count); size += count; copied += count; progress.accept(copied);
                    }
                }
                BasicFileAttributes after = Files.readAttributes(source.path(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (size != source.size() || !after.isRegularFile() || !Objects.equals(source.key(), after.fileKey())
                        || after.lastModifiedTime().toMillis() != source.modified()) throw new IOException("world changed during archive");
                zip.closeEntry();
            }
            zip.finish(); zip.flush();
        } catch (IOException | RuntimeException | Error e) { Files.deleteIfExists(output); throw e; }
        return new Info(name, owner, Files.size(output), total[0], sources.size(), HexFormat.of().formatHex(digest.digest()));
    }
    public static void verify(Path archive, Info info, BooleanSupplier cancelled) throws IOException {
        if (Files.isSymbolicLink(archive) || Files.size(archive) != info.bytes()) throw new IOException("archive size");
        MessageDigest digest = sha256(); byte[] buffer = new byte[65536];
        try (InputStream input = Files.newInputStream(archive, LinkOption.NOFOLLOW_LINKS)) {
            for (int n; (n = input.read(buffer)) >= 0;) { checkCancelled(cancelled); digest.update(buffer, 0, n); }
        }
        if (!MessageDigest.isEqual(digest.digest(), HexFormat.of().parseHex(info.sha256()))) throw new IOException("archive checksum");
    }
    public static void extract(Path archive, Path destination, Info info, BooleanSupplier cancelled, LongConsumer progress) throws IOException {
        verify(archive, info, cancelled); Files.createDirectory(destination); boolean complete = false;
        try (var zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archive, LinkOption.NOFOLLOW_LINKS), 65536))) {
            Set<String> names = new HashSet<>(); Map<String, String> spelling = new HashMap<>();
            byte[] buffer = new byte[65536]; long total = 0; int entries = 0;
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                checkCancelled(cancelled);
                String name = entry.getName(); if (entry.isDirectory() && name.endsWith("/")) name = name.substring(0, name.length() - 1);
                validateName(name);
                if (!names.add(name.toLowerCase(Locale.ROOT)) || ++entries > info.entries()) throw new IOException("duplicate or excessive archive entries");
                String prefix = "";
                for (String part : name.split("/")) {
                    prefix += (prefix.isEmpty() ? "" : "/") + part; String previous = spelling.putIfAbsent(prefix.toLowerCase(Locale.ROOT), prefix);
                    if (previous != null && !previous.equals(prefix)) throw new IOException("archive case collision");
                }
                Path target = destination.resolve(name).normalize();
                if (!target.startsWith(destination) || target.equals(destination)) throw new IOException("archive path");
                if (entry.isDirectory()) Files.createDirectories(target);
                else {
                    Files.createDirectories(target.getParent());
                    try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                        for (int n; (n = zip.read(buffer)) >= 0;) {
                            checkCancelled(cancelled); if (n > info.unpacked() - total) throw new IOException("archive expands beyond declared size");
                            output.write(buffer, 0, n); total += n; progress.accept(total);
                        }
                    }
                }
                zip.closeEntry();
            }
            if (entries != info.entries() || total != info.unpacked() || !Files.isRegularFile(destination.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS))
                throw new IOException("incomplete world archive");
            complete = true;
        } finally { if (!complete) deleteTree(destination); }
    }
    static void validateName(String name) throws IOException {
        if (name.isEmpty() || name.length() > 512 || name.contains("\\") || name.startsWith("/") || name.contains(":")) throw new IOException("archive path");
        for (String part : name.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ")
                    || part.codePoints().anyMatch(c -> c < 32 || "<>\"|?*".indexOf(c) >= 0)
                    || part.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) throw new IOException("nonportable archive path");
        }
    }
    public static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException { if (failure != null) throw failure; Files.delete(dir); return FileVisitResult.CONTINUE; }
        });
    }
    public static void checkCancelled(BooleanSupplier cancelled) throws InterruptedIOException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("world transfer cancelled");
    }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
