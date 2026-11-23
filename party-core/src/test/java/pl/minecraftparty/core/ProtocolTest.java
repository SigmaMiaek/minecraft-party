package pl.minecraftparty.core;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    @Test void invitationsRoundTripAndRedactSecrets() throws Exception {
        Invite invite = invitation();
        assertEquals(invite, Invite.decode(invite.encode()));
        assertFalse(invite.toString().contains(invite.secret()));
        assertFalse(invite.toString().contains("127.0.0.1"));
    }
    @Test void invalidOrExpiredInvitationsAreRejected() {
        for (String code : List.of("", "mp2:AAAA", "mp1:!", "mp1:" + "A".repeat(5000), "mp1:AAAA"))
            assertThrows(IOException.class, () -> Invite.decode(code));
        var old = new Invite(UUID.randomUUID(), List.of("127.0.0.1"), 12345, "a".repeat(64), Secrets.token(), 1);
        assertThrows(IOException.class, () -> Invite.decode(old.encode()));
        for (String endpoint : List.of("example.com", "0.0.0.0", "255.255.255.255", "224.0.0.1", "999.1.2.3", "fe80::1%eth0", "::::"))
            assertThrows(IllegalArgumentException.class, () -> new Invite(UUID.randomUUID(), List.of(endpoint), 12345,
                    "a".repeat(64), Secrets.token(), Long.MAX_VALUE));
    }
    @Test void snapshotsRoundTripAndRejectEveryTruncation() throws Exception {
        var model = new LobbyModel(UUID.randomUUID(), "Host", "Przygoda", 6);
        model.join(UUID.randomUUID(), "Guest", model.inviteSecret(), ""); model.world(UUID.randomUUID());
        var bytes = new ByteArrayOutputStream(); Wire.snapshot(new DataOutputStream(bytes), model.snapshot());
        byte[] encoded = bytes.toByteArray();
        assertEquals(model.snapshot(), Wire.snapshot(input(encoded)));
        for (int size = 0; size < encoded.length; size++) {
            byte[] shortened = Arrays.copyOf(encoded, size);
            assertThrows(IOException.class, () -> Wire.snapshot(input(shortened)));
        }
    }
    @Test void tenMembersFitTheModelAndWireButTheEleventhIsRejected() throws Exception {
        var model = new LobbyModel(UUID.randomUUID(), "Host", "Ten players", 10, true);
        for (int i = 1; i < 10; i++) model.join(UUID.randomUUID(), "Guest" + i, model.inviteSecret(), "");
        assertEquals(10, model.snapshot().members().size());
        var bytes = new ByteArrayOutputStream(); Wire.snapshot(new DataOutputStream(bytes), model.snapshot());
        assertEquals(model.snapshot(), Wire.snapshot(input(bytes.toByteArray())));
        assertEquals(0x4d505437, Wire.MAGIC);
        assertThrows(SecurityException.class, () -> model.join(UUID.randomUUID(), "Extra", model.inviteSecret(), ""));
        assertThrows(IllegalArgumentException.class, () -> new LobbyModel(UUID.randomUUID(), "Host", "Too many", 11));
    }
    @Test void lengthsAndStateEnumsAreBounded() {
        assertThrows(IOException.class, () -> Wire.text(input(new byte[]{-1, -1}), 16));
        assertThrows(IOException.class, () -> Wire.status(input(new byte[]{100})));
    }
    @Test void malformedSnapshotsCannotIntroduceDuplicateMembersOrHosts() throws Exception {
        UUID id = UUID.randomUUID();
        var member = new LobbyState.Member(id, "Host", true, LobbyState.Status.LOBBY);
        var bad = new LobbyState(UUID.randomUUID(), "Party", 6, 1, null, List.of(member, member));
        var bytes = new ByteArrayOutputStream(); Wire.snapshot(new DataOutputStream(bytes), bad);
        assertThrows(IOException.class, () -> Wire.snapshot(input(bytes.toByteArray())));
    }
    static Invite invitation() {
        return new Invite(UUID.randomUUID(), List.of("127.0.0.1", "192.168.1.5"), 12345,
                "a".repeat(64), Secrets.token(), System.currentTimeMillis() + 60000);
    }
    private static DataInputStream input(byte[] data) { return new DataInputStream(new ByteArrayInputStream(data)); }
}
