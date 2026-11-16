package pl.minecraftparty.core;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class E4mcProtocolTest {
    private Invite invitation() {
        return new Invite(UUID.randomUUID(), List.of("127.0.0.1"), 34567, "a".repeat(64), Secrets.token(), System.currentTimeMillis() + 60000);
    }
    @Test void relayDomainsCannotSelectAnotherServiceOrInjectRoutingData() {
        assertEquals("join-word.de.e4mc.link", E4mcProtocol.domain("Join-word.DE.e4mc.link"));
        for (String value : List.of("e4mc.link", "e4mc.link.evil.example", "localhost", "127.0.0.1", "::1", "https://de.e4mc.link",
                "evil@example.e4mc.link", "host.de.e4mc.link:25565", "host..e4mc.link", "-host.e4mc.link", "x.e4mc.link\u0000host"))
            assertThrows(IllegalArgumentException.class, () -> E4mcProtocol.domain(value), value);
    }
    @Test void publicRoutingPrefaceContainsOnlyTheAssignedDestination() throws Exception {
        String domain = "join-word.de.e4mc.link";
        var in = new DataInputStream(new ByteArrayInputStream(E4mcProtocol.routingPreface(domain)));
        byte[] packet = in.readNBytes(E4mcProtocol.readVarInt(in));
        E4mcProtocol.validateRoutingPacket(packet, domain); assertEquals(0, in.available());
        assertThrows(IOException.class, () -> E4mcProtocol.validateRoutingPacket(packet, "other.de.e4mc.link"));
        assertThrows(IOException.class, () -> E4mcProtocol.validateRoutingPacket(Arrays.copyOf(packet, packet.length - 1), domain));
        packet[packet.length - 1] = 1;
        assertThrows(IOException.class, () -> E4mcProtocol.validateRoutingPacket(packet, domain));
    }
    @Test void malformedAndOversizedRoutingDataIsRejectedBeforeTls() {
        assertThrows(IOException.class, () -> E4mcProtocol.validateRoutingPacket(new byte[513], "x.de.e4mc.link"));
        assertThrows(IOException.class, () -> E4mcProtocol.readVarInt(new DataInputStream(new ByteArrayInputStream(new byte[]{-1,-1,-1,-1,-1,0}))));
        assertThrows(IOException.class, () -> E4mcProtocol.readVarInt(new DataInputStream(new ByteArrayInputStream(new byte[]{-128}))));
    }
    @Test void relayInvitationsRoundTripWithoutAnyHostIpAndRetainTheTlsCapability() throws Exception {
        Invite direct = invitation(), relay = direct.viaRelay("join-word.de.e4mc.link");
        assertTrue(relay.encode().startsWith("mp3:")); assertTrue(relay.candidates().isEmpty());
        assertEquals(25565, relay.port()); assertEquals(direct.secret(), relay.secret()); assertEquals(direct.fingerprint(), relay.fingerprint());
        assertEquals(relay, Invite.decode(relay.encode())); assertEquals("Invite[REDACTED]", relay.toString());
        assertEquals(direct, Invite.decode(direct.encode())); assertTrue(direct.encode().startsWith("mp2:"));
    }
    @Test void theJoinGateAcceptsBothGeneratedTransportInvitationsIncludingClipboardWhitespace() throws Exception {
        Invite direct = invitation();
        for (Invite invite : List.of(direct, direct.viaRelay("join-word.de.e4mc.link"))) {
            String pasted = " \t" + invite.encode() + "\r\n";
            assertTrue(Invite.hasSupportedPrefix(pasted));
            assertEquals(invite, Invite.decode(pasted));
        }
        assertFalse(Invite.hasSupportedPrefix(null));
        for (String text : List.of("", "  ", "mp2:", "mp3:", "mp1:old", "mp4:future", "mp3:" + "a".repeat(Invite.MAX_CODE_LENGTH)))
            assertFalse(Invite.hasSupportedPrefix(text));
    }
    @Test void transportAndVersionCannotBeConfusedOrMixed() throws Exception {
        var direct = invitation(); var relay = direct.viaRelay("join-word.de.e4mc.link");
        assertThrows(IOException.class, () -> Invite.decode("mp2:" + relay.encode().substring(4)));
        assertThrows(IOException.class, () -> Invite.decode("mp3:" + direct.encode().substring(4)));
        assertThrows(IllegalArgumentException.class, () -> new Invite(direct.partyId(), direct.candidates(), 25565,
                direct.fingerprint(), direct.secret(), direct.expiresAt(), "x.de.e4mc.link"));
        assertThrows(IllegalArgumentException.class, () -> new Invite(direct.partyId(), List.of(), 22,
                direct.fingerprint(), direct.secret(), direct.expiresAt(), "x.de.e4mc.link"));
    }
    @Test void relayInvitationsExpireAndCannotSelectAnArbitraryDnsEndpoint() {
        Invite base = invitation();
        var expired = new Invite(base.partyId(), List.of(), 25565, base.fingerprint(), base.secret(), 1, "x.de.e4mc.link");
        assertThrows(IOException.class, () -> Invite.decode(expired.encode()));
        assertThrows(IllegalArgumentException.class, () -> base.viaRelay("evil.example"));
    }
    @Test void httpBodiesAreLimitedDuringReceptionAndCancelTheSubscription() {
        var cancelled = new AtomicBoolean(); var body = new LimitedHttpBody(4);
        body.onSubscribe(new Flow.Subscription() {
            public void request(long count) { }
            public void cancel() { cancelled.set(true); }
        });
        body.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2}), ByteBuffer.wrap(new byte[]{3,4,5})));
        assertTrue(cancelled.get()); assertThrows(CompletionException.class, () -> body.getBody().toCompletableFuture().join());
    }
    @Test void anExactSizeHttpBodyCompletesAndPreservesBytes() {
        var body = new LimitedHttpBody(4);
        body.onSubscribe(new Flow.Subscription() { public void request(long count) { } public void cancel() { fail(); } });
        body.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2}))); body.onNext(List.of(ByteBuffer.wrap(new byte[]{3,4}))); body.onComplete();
        assertArrayEquals(new byte[]{1,2,3,4}, body.getBody().toCompletableFuture().join());
    }
}
