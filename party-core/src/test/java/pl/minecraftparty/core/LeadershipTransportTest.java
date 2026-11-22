package pl.minecraftparty.core;

import org.junit.jupiter.api.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** Actual loopback TLS handoff; not an e4mc or running Minecraft test. */
@Tag("network")
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
final class LeadershipTransportTest {
    @Test void handoffMovesRemainingMembersWithoutNewInvitesAndOldHostCanClose() throws Exception {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), carol = UUID.randomUUID();
        AtomicReference<HostNode> nextHost = new AtomicReference<>();
        AtomicReference<GuestNode> bobClient = new AtomicReference<>(), carolMoved = new AtomicReference<>();
        AtomicReference<LeadershipPacket.Ready> ready = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (HostNode old = new HostNode(alice, "Alice", "Friends", 4, List.of("127.0.0.1"), InetAddress.getByName("127.0.0.1"), s -> {})) {
            old.leadershipListener(event -> { if (event.packet() instanceof LeadershipPacket.Ready packet) ready.set(packet); });
            try (GuestNode bobGuest = new GuestNode(old.invite(), bob, "Bob", "", s -> {}, s -> {}, packet -> {
                try {
                    if (packet instanceof LeadershipPacket.Prepare prepare) {
                        HostNode host = HostNode.successor(prepare.plan(), List.of("127.0.0.1"), InetAddress.getByName("127.0.0.1"), s -> {});
                        nextHost.set(host); bobClient.get().leadership(new LeadershipPacket.Ready(packet.transaction(), host.invite()));
                    }
                } catch (Throwable e) { failure.set(e); }
            }); GuestNode carolGuest = new GuestNode(old.invite(), carol, "Carol", "", s -> {}, s -> {}, packet -> {
                try {
                    if (packet instanceof LeadershipPacket.Commit commit) {
                        // The credential belongs to Carol on the original channel, never to an invitation guesser.
                        carolMoved.set(new GuestNode(commit.invite(), carol, "Carol", carolCredential.get(), s -> {}, s -> {}, p -> {}));
                    }
                } catch (Throwable e) { failure.set(e); }
            })) {
                bobClient.set(bobGuest); await(() -> bobGuest.connected() && carolGuest.connected());
                carolCredential.set(carolGuest.migrationCredential()); old.chat("Persistent chat");
                LeadershipPlan plan = old.planLeadership(bob, true); old.beginLeadership(plan);
                await(() -> ready.get() != null || failure.get() != null); assertNull(failure.get());
                old.commitLeadership(plan.transaction(), ready.get().invite()).get(5, TimeUnit.SECONDS);
                await(() -> carolMoved.get() != null && carolMoved.get().connected());
                old.close();
                assertEquals(plan.snapshot().partyId(), nextHost.get().snapshot().partyId());
                assertEquals(Set.of(bob, carol), nextHost.get().snapshot().members().stream().map(LobbyState.Member::id).collect(java.util.stream.Collectors.toSet()));
                assertEquals("Persistent chat", nextHost.get().snapshot().chat().getFirst().text());
                assertTrue(carolMoved.get().connected());
            }
        } finally { if (carolMoved.get() != null) carolMoved.get().close(); if (nextHost.get() != null) nextHost.get().close(); }
    }
    private final AtomicReference<String> carolCredential = new AtomicReference<>();
    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        while (!condition.getAsBoolean()) { if (System.nanoTime() > deadline) fail("handoff timed out"); Thread.sleep(20); }
    }
}
