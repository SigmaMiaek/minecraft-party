package pl.minecraftparty.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldPermissionPolicyTest {
    private final UUID host = UUID.randomUUID(), guest = UUID.randomUUID(), other = UUID.randomUUID();
    private WorldPermissionPolicy policy() { return new WorldPermissionPolicy(host, Set.of(host, guest), false); }

    @Test void guestsStartWithoutOperatorOrTeleport() {
        var policy = policy();
        assertFalse(policy.isOperator(guest)); assertFalse(policy.canTeleport(guest));
        assertFalse(policy.canTeleport(other)); assertTrue(policy.operators().isEmpty());
    }
    @Test void onlyHostCanGrantOrRevokePermissions() {
        var policy = policy();
        assertThrows(SecurityException.class, () -> policy.operator(guest, guest, true));
        assertThrows(SecurityException.class, () -> policy.operator(other, guest, true));
        assertThrows(SecurityException.class, () -> policy.teleport(guest, true));
        assertThrows(SecurityException.class, () -> policy.operator(host, other, true));
        assertThrows(SecurityException.class, () -> policy.operator(host, host, true));
        policy.operator(host, guest, true);
        assertTrue(policy.isOperator(guest)); assertTrue(policy.canTeleport(guest));
        policy.operator(host, guest, false);
        assertFalse(policy.isOperator(guest)); assertFalse(policy.canTeleport(guest));
    }
    @Test void teleportGrantDoesNotMakeGuestsOperatorsOrIncludeOutsiders() {
        var policy = policy(); policy.teleport(host, true);
        assertTrue(policy.canTeleport(guest)); assertFalse(policy.isOperator(guest));
        assertFalse(policy.canTeleport(other)); assertFalse(policy.isOperator(other));
        policy.teleport(host, false); assertFalse(policy.canTeleport(guest));
    }
    @Test void disablingTeleportPreservesExplicitOperators() {
        var policy = policy(); policy.teleport(host, true); policy.operator(host, guest, true);
        policy.teleport(host, false);
        assertTrue(policy.isOperator(guest)); assertTrue(policy.canTeleport(guest));
    }
    @Test void leavingOrKickingRemovesGrantsAndRejoiningDoesNotRestoreOperator() {
        var policy = policy(); policy.operator(host, guest, true); policy.teleport(host, true);
        policy.members(Set.of(host));
        assertFalse(policy.isOperator(guest)); assertFalse(policy.canTeleport(guest));
        policy.members(Set.of(host, guest));
        assertFalse(policy.isOperator(guest)); assertTrue(policy.canTeleport(guest));
    }
    @Test void closedWorldCannotRetainOrAcceptGrants() {
        var policy = policy(); policy.operator(host, guest, true); policy.teleport(host, true); policy.close();
        assertFalse(policy.isOperator(guest)); assertFalse(policy.canTeleport(guest));
        assertTrue(policy.operators().isEmpty());
        assertThrows(SecurityException.class, () -> policy.operator(host, guest, true));
        assertThrows(SecurityException.class, () -> policy.teleport(host, true));
        policy.members(Set.of(host, guest)); assertFalse(policy.canTeleport(guest));
    }
    @Test void consecutiveWorldsHaveIndependentOperatorGrants() {
        var first = policy(); first.operator(host, guest, true);
        var second = policy(); assertFalse(second.isOperator(guest));
        first.close(); second.teleport(host, true);
        assertFalse(first.canTeleport(guest)); assertTrue(second.canTeleport(guest));
    }
    @Test void callerCannotMutateMembershipOrOperatorSnapshot() {
        var mutable = new HashSet<>(Set.of(host, guest));
        var policy = new WorldPermissionPolicy(host, mutable, false); mutable.add(other);
        assertThrows(SecurityException.class, () -> policy.operator(host, other, true));
        policy.operator(host, guest, true);
        assertThrows(UnsupportedOperationException.class, () -> policy.operators().clear());
        assertThrows(IllegalArgumentException.class, () -> policy.members(Set.of(guest)));
    }
}
