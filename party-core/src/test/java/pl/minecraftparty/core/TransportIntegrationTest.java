package pl.minecraftparty.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import com.sun.management.HotSpotDiagnosticMXBean;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** Actual loopback sockets and TLS; not a substitute for tests with Minecraft clients. */
@Tag("network")
@Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class TransportIntegrationTest {
    @Test void watchScreenPauseSeekLateJoinAndWorldCloseSynchronizeThroughRealTls() throws Exception {
        try (HostNode host = host(4);
             GuestNode guest = new GuestNode(host.invite(), UUID.randomUUID(), "Viewer", s -> {}, text -> {})) {
            await(guest::connected);
            assertThrows(IllegalStateException.class, () -> host.startWatch("M7lc1UVf-VE",
                    new WatchState.Placement("minecraft:overworld", 0, 64, 0, 0, 6)));
            host.openWorld(new InetSocketAddress("127.0.0.1", 25565), UUID.randomUUID());
            host.startWatch("M7lc1UVf-VE", new WatchState.Placement("minecraft:overworld", 10, 70, 20, 180, 6));
            await(() -> guest.watch() != null);
            assertFalse(guest.watch().playing()); assertEquals("M7lc1UVf-VE", guest.watch().videoId());
            host.seekWatch(65_000); host.playWatch(true);
            await(() -> guest.watch().playing() && guest.watch().positionMs() >= 65_000);
            try (GuestNode late = new GuestNode(host.invite(), UUID.randomUUID(), "Late", s -> {}, text -> {})) {
                await(() -> late.watch() != null && late.watch().playing());
                assertEquals(guest.watch().sessionId(), late.watch().sessionId());
                host.playWatch(false); await(() -> !guest.watch().playing() && !late.watch().playing());
                host.stopWatch(); await(() -> guest.watch() == null && late.watch() == null);
                host.startWatch("M7lc1UVf-VE", new WatchState.Placement("minecraft:overworld", 10, 70, 20, 0, 6));
                await(() -> guest.watch() != null);
                host.closeWorld(); await(() -> guest.watch() == null && guest.snapshot().worldId() == null);
                assertTrue(guest.connected()); assertTrue(late.connected());
            }
        }
    }
    @Test void chatAndSharedMultiplayerDestinationSynchronizeOverTls() throws Exception {
        AtomicReference<LobbyState> state = new AtomicReference<>();
        try (HostNode host = host(4);
             GuestNode guest = new GuestNode(host.invite(), UUID.randomUUID(), "Guest", state::set, text -> {})) {
            await(() -> state.get() != null);
            host.chat("From host"); guest.chat("From guest");
            await(() -> state.get().chat().size() == 2);
            assertEquals("Host", state.get().chat().getFirst().senderName());
            assertEquals("Guest", state.get().chat().getLast().senderName());
            var destination = new MultiplayerTarget("play.example.org:25570"); UUID session = UUID.randomUUID();
            host.openMultiplayer(destination, session);
            await(() -> session.equals(state.get().worldId()));
            assertEquals(destination, state.get().multiplayerTarget());
            host.closeWorld(UUID.randomUUID()); // A delayed close from an old integrated world must not clear this session.
            assertEquals(session, host.snapshot().worldId());
            assertThrows(IOException.class, () -> guest.prepareWorld(session));
            host.closeWorld(); await(() -> state.get().worldId() == null);
            assertEquals(2, state.get().members().size()); assertEquals(2, state.get().chat().size());
        }
    }
    @Test void lobbySynchronizesAndRevocationRejectsOldInvites() throws Exception {
        try (HostNode host = host(4)) {
            Invite initial = host.invite();
            AtomicReference<LobbyState> state = new AtomicReference<>();
            try (GuestNode guest = new GuestNode(initial, UUID.randomUUID(), "Guest", state::set, text -> {})) {
                await(() -> state.get() != null && state.get().members().size() == 2);
                host.rotateInvite();
                AtomicReference<String> error = new AtomicReference<>();
                try (GuestNode rejected = new GuestNode(initial, UUID.randomUUID(), "Other", s -> {}, error::set)) {
                    await(() -> "rejected".equals(error.get()));
                }
                assertTrue(guest.connected());
            }
            await(() -> host.snapshot().members().size() == 1);
        }
    }
    @RepeatedTest(3) void rawMinecraftBytesPassThroughTlsAndLobbySurvivesTwoWorlds() throws Exception {
        UUID guestId = UUID.randomUUID(); AtomicReference<LobbyState> state = new AtomicReference<>();
        try (HostNode host = host(4);
             GuestNode guest = new GuestNode(host.invite(), guestId, "Guest", state::set, text -> {})) {
            await(() -> state.get() != null);
            for (int i = 0; i < 2; i++) {
                try (ServerSocket backend = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
                    UUID world = UUID.randomUUID(); byte[] login = MinecraftAdmissionTest.login(guestId, "Guest");
                    byte[] payload = new byte[131072]; new Random(42 + i).nextBytes(payload);
                    CompletableFuture<Void> received = new CompletableFuture<>();
                    // Minecraft's Netty backend runs on platform event-loop threads.
                    Thread.ofPlatform().daemon().name("party-test-backend").start(() -> {
                        try (Socket socket = backend.accept()) {
                            socket.setSoTimeout(10000);
                            assertArrayEquals(login, socket.getInputStream().readNBytes(login.length));
                            assertArrayEquals(payload, socket.getInputStream().readNBytes(payload.length));
                            socket.getOutputStream().write(payload); socket.getOutputStream().flush();
                            received.complete(null);
                        } catch (Throwable e) { received.completeExceptionally(e); }
                    });
                    host.openWorld(new InetSocketAddress("127.0.0.1", backend.getLocalPort()), world);
                    await(() -> world.equals(state.get().worldId()));
                    int port = guest.prepareWorld(world);
                    try (Socket local = new Socket("127.0.0.1", port)) {
                        local.setSoTimeout(5000);
                        local.getOutputStream().write(login); local.getOutputStream().write(payload); local.getOutputStream().flush();
                        try { assertArrayEquals(payload, local.getInputStream().readNBytes(payload.length)); }
                        catch (Exception failure) {
                            dumpThreads(failure);
                            if (received.isCompletedExceptionally()) {
                                try { received.join(); } catch (CompletionException backendFailure) { failure.addSuppressed(backendFailure); }
                            }
                            throw failure;
                        }
                    }
                    received.get(5, TimeUnit.SECONDS);
                    host.closeWorld(); await(() -> state.get().worldId() == null);
                    assertEquals(2, state.get().members().size()); assertTrue(guest.connected());
                }
            }
        }
    }
    @Test void kickDisconnectsMemberAndRotatesInvite() throws Exception {
        UUID id = UUID.randomUUID(); AtomicReference<LobbyState> state = new AtomicReference<>();
        try (HostNode host = host(3);
             GuestNode guest = new GuestNode(host.invite(), id, "Guest", state::set, text -> {})) {
            await(() -> state.get() != null);
            String before = host.invite().encode(); host.kick(id);
            assertNotEquals(before, host.invite().encode());
            assertEquals(1, host.snapshot().members().size());
            await(() -> !guest.connected());
        }
    }
    private static HostNode host(int limit) throws Exception {
        return new HostNode(UUID.randomUUID(), "Host", "Party", limit, List.of("127.0.0.1"),
                InetAddress.getByName("127.0.0.1"), s -> {});
    }
    private static void dumpThreads(Exception failure) {
        Path dump = Path.of(System.getProperty("java.io.tmpdir"), "party-test-" + UUID.randomUUID() + ".json").toAbsolutePath();
        try {
            ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
                    .dumpThreads(dump.toString(), HotSpotDiagnosticMXBean.ThreadDumpFormat.JSON);
            System.err.println("PARTY_TEST_THREADS\n" + Files.readString(dump));
        } catch (Exception diagnosticFailure) { failure.addSuppressed(diagnosticFailure); }
        finally { try { Files.deleteIfExists(dump); } catch (IOException ignored) { } }
    }
    private static void await(BooleanSupplier condition) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > until) fail("Timed out waiting for transport state");
            Thread.sleep(20);
        }
    }
}
