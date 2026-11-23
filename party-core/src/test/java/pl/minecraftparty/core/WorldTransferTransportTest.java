package pl.minecraftparty.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

/** Actual pinned TLS, selective file access, cancellation and resumed bytes; no Minecraft process is simulated. */
@Tag("network") @Timeout(value = 25, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
final class WorldTransferTransportTest {
    @TempDir Path temporary;
    @Test void onlyTheConsentingRecipientCanResumeVerifyAndImportAWorld() throws Exception {
        UUID recipient = UUID.randomUUID(), owner = UUID.randomUUID();
        var recipientMessages = new LinkedBlockingQueue<WorldPacket>(); var otherMessages = new LinkedBlockingQueue<WorldPacket>();
        var events = new LinkedBlockingQueue<HostNode.WorldEvent>();
        try (HostNode host = new HostNode(owner, "Host", "Party", 4, List.of("127.0.0.1"), InetAddress.getByName("127.0.0.1"), s -> {});
             GuestNode guest = new GuestNode(host.invite(), recipient, "Receiver", "", s -> {}, s -> {}, p -> {}, recipientMessages::offer);
             GuestNode other = new GuestNode(host.invite(), UUID.randomUUID(), "Other", "", s -> {}, s -> {}, p -> {}, otherMessages::offer)) {
            await(() -> guest.connected() && other.connected()); host.worldListener(events::offer);
            Path source = Files.createDirectory(temporary.resolve("source")); Files.writeString(source.resolve("level.dat"), "test world metadata");
            byte[] chunks = new byte[524288]; new Random(42).nextBytes(chunks); Files.write(source.resolve("r.0.0.mca"), chunks);
            Path archive = temporary.resolve("world.zip"); var info = WorldArchive.pack(source, archive, "Survival", owner, () -> false, n -> {});
            var offer = host.offerWorld(recipient, "Survival"); assertEquals(offer, packet(recipientMessages, WorldPacket.Offer.class));
            assertTrue(otherMessages.isEmpty());
            assertThrows(IllegalStateException.class, () -> host.worldArchive(offer.transaction(), archive, info));
            guest.worldPacket(new WorldPacket.Accept(offer.transaction()));
            assertInstanceOf(WorldPacket.Accept.class, events.poll(3, TimeUnit.SECONDS).packet());
            host.worldArchive(offer.transaction(), archive, info);
            assertInstanceOf(WorldPacket.Archive.class, packet(recipientMessages, WorldPacket.Archive.class));
            assertThrows(SecurityException.class, () -> other.downloadWorld(offer.transaction(), info, temporary.resolve("denied"), () -> false, n -> {}));
            assertThrows(SecurityException.class, () -> guest.downloadWorld(UUID.randomUUID(), info, temporary.resolve("wrong-tx"), () -> false, n -> {}));
            Path partial = temporary.resolve("download.part"); byte[] zip = Files.readAllBytes(archive); Files.write(partial, Arrays.copyOf(zip, 40000));
            AtomicBoolean cancel = new AtomicBoolean();
            assertThrows(InterruptedIOException.class, () -> guest.downloadWorld(offer.transaction(), info, partial, cancel::get, n -> { if (n > 100000) cancel.set(true); }));
            assertTrue(Files.size(partial) >= 100000); assertTrue(guest.connected());
            guest.downloadWorld(offer.transaction(), info, partial, () -> false, n -> {});
            assertArrayEquals(zip, Files.readAllBytes(partial));
            Path imported = temporary.resolve("imported"); WorldArchive.extract(partial, imported, info, () -> false, n -> {});
            assertArrayEquals(chunks, Files.readAllBytes(imported.resolve("r.0.0.mca")));
            guest.worldPacket(new WorldPacket.Imported(offer.transaction()));
            assertInstanceOf(WorldPacket.Imported.class, events.poll(3, TimeUnit.SECONDS).packet());
            host.finishWorldTransfer(offer.transaction());
            assertDoesNotThrow(() -> host.planLeadership(recipient, true));
            assertTrue(otherMessages.isEmpty()); assertTrue(Files.exists(source.resolve("level.dat")));
        }
    }
    @Test void revokingAnOfferPreventsFileAccessWithoutDestroyingTheLobby() throws Exception {
        UUID recipient = UUID.randomUUID(), owner = UUID.randomUUID(); var events = new LinkedBlockingQueue<HostNode.WorldEvent>();
        try (HostNode host = new HostNode(owner, "Host", "Party", 3, List.of("127.0.0.1"), InetAddress.getByName("127.0.0.1"), s -> {});
             GuestNode guest = new GuestNode(host.invite(), recipient, "Receiver", "", s -> {}, s -> {}, p -> {}, p -> {})) {
            await(guest::connected); host.worldListener(events::offer);
            var offer = host.offerWorld(recipient, "World"); guest.worldPacket(new WorldPacket.Accept(offer.transaction())); events.poll(3, TimeUnit.SECONDS);
            host.cancelWorldTransfer(offer.transaction());
            var info = new WorldArchive.Info("World", owner, 100, 100, 1, "a".repeat(64));
            assertThrows(SecurityException.class, () -> guest.downloadWorld(offer.transaction(), info, temporary.resolve("revoked"), () -> false, n -> {}));
            assertTrue(guest.connected()); assertEquals(2, host.snapshot().members().size());
        }
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) { if (System.nanoTime() >= deadline) fail("TLS lobby did not connect"); Thread.sleep(10); }
    }
    private static <T extends WorldPacket> T packet(BlockingQueue<WorldPacket> packets, Class<T> type) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            WorldPacket packet = packets.poll(100, TimeUnit.MILLISECONDS);
            if (type.isInstance(packet)) return type.cast(packet);
        }
        fail("Expected world transfer signal: " + type.getSimpleName()); return null;
    }
}
