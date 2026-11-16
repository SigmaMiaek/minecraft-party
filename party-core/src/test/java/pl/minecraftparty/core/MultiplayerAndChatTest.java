package pl.minecraftparty.core;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MultiplayerAndChatTest {
    private final UUID host = UUID.randomUUID(), guest = UUID.randomUUID();
    private final TestClock clock = new TestClock();
    private LobbyModel model() {
        var model = new LobbyModel(host, "Host", "Party", 6, clock);
        model.join(guest, "Guest", model.inviteSecret(), ""); return model;
    }
    @Test void worldServerAndLobbyTransitionsKeepMembershipAndChat() {
        var model = model();
        model.chat(host, "Cześć!");
        UUID world = UUID.randomUUID(); model.world(world);
        assertNull(model.snapshot().multiplayerTarget());
        var server = new MultiplayerTarget("play.example.org:25580"); UUID session = UUID.randomUUID();
        model.multiplayer(session, server);
        assertEquals(session, model.snapshot().worldId()); assertEquals(server, model.snapshot().multiplayerTarget());
        assertEquals(2, model.snapshot().members().size());
        model.world(null);
        assertNull(model.snapshot().multiplayerTarget()); assertNull(model.snapshot().worldId());
        assertEquals(1, model.snapshot().chat().size()); assertEquals(2, model.snapshot().members().size());
        model.world(UUID.randomUUID()); assertNull(model.snapshot().multiplayerTarget());
    }
    @Test void snapshotRoundTripIncludesDestinationAndChatAndRejectsTruncation() throws Exception {
        var model = model(); model.multiplayer(UUID.randomUUID(), new MultiplayerTarget("play.example.org"));
        model.chat(guest, "Hello! Zażółć gęślą jaźń");
        var bytes = new ByteArrayOutputStream(); Wire.snapshot(new DataOutputStream(bytes), model.snapshot());
        byte[] payload = bytes.toByteArray();
        assertEquals(model.snapshot(), Wire.snapshot(new DataInputStream(new ByteArrayInputStream(payload))));
        for (int size = 0; size < payload.length; size++) {
            byte[] truncated = Arrays.copyOf(payload, size);
            assertThrows(IOException.class, () -> Wire.snapshot(new DataInputStream(new ByteArrayInputStream(truncated))));
        }
    }
    @Test void serverAddressesPreserveDnsSrvAndPortsWithoutResolvingDomains() {
        for (String address : List.of("play.example.org", "PLAY.example.org:25566", "192.168.1.5:25565", "[2001:db8::2]:25580", "przykład.pl:25565"))
            assertEquals(address, new MultiplayerTarget(address).address());
        assertFalse(new MultiplayerTarget("play.example.org").toString().contains("example"));
    }
    @Test void malformedAndLocalOnlyDestinationsAreRejected() {
        for (String address : List.of("", "https://example.org", "host/path", "example.org:0", "example.org:65536", "example.org:",
                "one..example", "-host.example", "[2001:::bad]:1234", "example.org\n", "localhost", "127.0.0.1:25565", "[::1]", "0.0.0.0", "999.1.2.3", "127.1", "local.localhost."))
            assertThrows(IllegalArgumentException.class, () -> new MultiplayerTarget(address), address);
    }
    @Test void chatAuthenticatesSenderRejectsControlCharactersAndLimitsRate() {
        var model = model();
        assertTrue(model.chat(guest, " Hello "));
        var message = model.snapshot().chat().getFirst();
        assertEquals(guest, message.senderId()); assertEquals("Guest", message.senderName()); assertEquals("Hello", message.text());
        assertFalse(model.chat(guest, "too fast"));
        clock.now += 750; assertTrue(model.chat(guest, "Next"));
        assertThrows(IllegalArgumentException.class, () -> model.chat(guest, "§cAdmin"));
        assertThrows(IllegalArgumentException.class, () -> model.chat(guest, "bad\ntext"));
        assertThrows(IllegalArgumentException.class, () -> model.chat(guest, "x".repeat(257)));
        assertThrows(SecurityException.class, () -> model.chat(UUID.randomUUID(), "not a member"));
        model.disconnected(guest); assertThrows(SecurityException.class, () -> model.chat(guest, "disconnected"));
    }
    @Test void chatHistoryIsBoundedImmutableAndSurvivesRevocation() {
        var model = model();
        for (int i = 0; i < 100; i++) { clock.now += 750; assertTrue(model.chat(host, "Message " + i)); }
        var before = model.snapshot(); model.rotateInvite();
        assertEquals(64, before.chat().size()); assertEquals(37, before.chat().getFirst().sequence());
        assertEquals(before.chat(), model.snapshot().chat());
        assertThrows(UnsupportedOperationException.class, () -> before.chat().clear());
    }
    @Test void oldInviteVersionIsExplicitlyRejected() throws Exception {
        var invite = ProtocolTest.invitation();
        assertTrue(invite.encode().startsWith("mp2:"));
        assertThrows(IOException.class, () -> Invite.decode("mp1:" + invite.encode().substring(4)));
    }
    private static final class TestClock extends Clock {
        long now = System.currentTimeMillis();
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now); }
    }
}
