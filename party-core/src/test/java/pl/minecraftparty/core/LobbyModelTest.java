package pl.minecraftparty.core;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LobbyModelTest {
    private final UUID host = UUID.randomUUID();
    private final UUID guest = UUID.randomUUID();
    private LobbyModel model(int limit) { return new LobbyModel(host, "Host", "Party", limit); }
    @Test void inviteAndCapacityAreEnforced() {
        var m = model(2);
        assertThrows(SecurityException.class, () -> m.join(guest, "Guest", "wrong", ""));
        m.join(guest, "Guest", m.inviteSecret(), "");
        assertEquals(2, m.snapshot().members().size());
        assertThrows(SecurityException.class, () -> m.join(UUID.randomUUID(), "Other", m.inviteSecret(), ""));
        assertThrows(SecurityException.class, () -> m.join(host, "Host", m.inviteSecret(), ""));
    }
    @Test void rotationDoesNotInvalidateResumeAndKickDoes() {
        var m = model(4);
        String oldInvite = m.inviteSecret();
        String credential = m.join(guest, "Guest", oldInvite, "");
        m.rotateInvite();
        assertThrows(SecurityException.class, () -> m.join(UUID.randomUUID(), "Other", oldInvite, ""));
        m.disconnected(guest);
        assertFalse(m.authenticate(guest, credential));
        assertEquals(credential, m.join(guest, "Guest", "", credential));
        assertTrue(m.authenticate(guest, credential));
        m.kick(guest);
        assertFalse(m.authenticate(guest, credential));
        assertThrows(SecurityException.class, () -> m.join(guest, "Guest", m.inviteSecret(), credential));
    }
    @Test void worldTransitionsKeepMembersAndSnapshotsAreImmutable() {
        var m = model(3);
        m.join(guest, "Guest", m.inviteSecret(), "");
        var before = m.snapshot();
        m.world(UUID.randomUUID());
        m.status(guest, LobbyState.Status.IN_WORLD);
        m.world(null);
        assertEquals(2, m.snapshot().members().size());
        assertNull(m.snapshot().worldId());
        assertTrue(m.snapshot().revision() > before.revision());
        assertThrows(UnsupportedOperationException.class, () -> before.members().clear());
    }
    @Test void lostMembersExpireAndCredentialsCannotHijackAnotherIdentity() {
        var clock = new MutableClock();
        var m = new LobbyModel(host, "Host", "Party", 2, clock);
        String token = m.join(guest, "Guest", m.inviteSecret(), "");
        assertThrows(SecurityException.class, () -> m.join(guest, "Guest", m.inviteSecret(), "wrong"));
        assertThrows(SecurityException.class, () -> m.join(guest, "Host", m.inviteSecret(), token));
        m.disconnected(guest);
        clock.now += 120001;
        assertEquals(1, m.snapshot().members().size());
        assertFalse(m.authenticate(guest, token));
        clock.now += 3600000;
        assertThrows(SecurityException.class, () -> m.join(guest, "Guest", m.inviteSecret(), ""));
    }
    @Test void invalidNamesAndDuplicateNamesAreRejected() {
        var m = model(4);
        assertThrows(IllegalArgumentException.class, () -> m.join(guest, "\n", m.inviteSecret(), ""));
        assertThrows(SecurityException.class, () -> m.join(guest, "host", m.inviteSecret(), ""));
        assertThrows(IllegalArgumentException.class, () -> m.join(guest, "§admin", m.inviteSecret(), ""));
    }
    private static final class MutableClock extends Clock {
        long now = 1000;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now); }
    }
}
