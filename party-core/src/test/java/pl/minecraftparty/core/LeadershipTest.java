package pl.minecraftparty.core;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class LeadershipTest {
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
    private LobbyModel lobby() {
        var model = new LobbyModel(a, "Alice", "Friends", 6);
        model.join(b, "Bob", model.inviteSecret(), ""); model.join(c, "Carol", model.inviteSecret(), "");
        model.chat(a, "Keep this conversation"); return model;
    }
    private LeadershipPlan plan(LobbyModel model, boolean leaving) {
        var keys = new HashMap<>(model.migrationCredentials()); keys.remove(b);
        if (!leaving) keys.put(a, Secrets.token());
        return new LeadershipPlan(UUID.randomUUID(), b, leaving, model.snapshot(), keys, model.migrationBans());
    }
    @Test void manualTransferPreservesIdentityChatSettingsAndReconnectCredentials() {
        var original = lobby(); var plan = plan(original, false); var restored = LobbyModel.restore(plan);
        assertEquals(original.snapshot().partyId(), restored.snapshot().partyId());
        assertEquals("Friends", restored.snapshot().name()); assertEquals(6, restored.snapshot().limit());
        assertEquals(original.snapshot().chat(), restored.snapshot().chat());
        assertEquals(b, restored.snapshot().members().stream().filter(LobbyState.Member::host).findFirst().orElseThrow().id());
        String resumed = restored.join(c, "Carol", "", plan.credentials().get(c));
        assertEquals(plan.credentials().get(c), resumed);
        restored.join(a, "Alice", "", plan.credentials().get(a));
        assertThrows(SecurityException.class, () -> restored.join(c, "Carol", restored.inviteSecret(), ""));
        assertEquals(3, restored.snapshot().members().size());
        assertNull(restored.snapshot().worldId());
    }
    @Test void leavingRemovesOnlyTheOldLeaderAndKeepsBans() {
        var original = lobby(); UUID banned = UUID.randomUUID(); original.kick(banned);
        var restored = LobbyModel.restore(plan(original, true));
        assertEquals(Set.of(b, c), restored.snapshot().members().stream().map(LobbyState.Member::id).collect(java.util.stream.Collectors.toSet()));
        assertThrows(SecurityException.class, () -> restored.join(banned, "Intruder", restored.inviteSecret(), ""));
        assertEquals(1, restored.snapshot().members().stream().filter(LobbyState.Member::host).count());
    }
    @Test void plansRoundTripThroughBoundedWireWithoutLoggingSecrets() throws Exception {
        var plan = plan(lobby(), false); var bytes = new ByteArrayOutputStream();
        LeadershipPacket.write(new DataOutputStream(bytes), new LeadershipPacket.Prepare(plan));
        var input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        var decoded = (LeadershipPacket.Prepare) LeadershipPacket.read(input.readUnsignedByte(), input);
        assertEquals(plan, decoded.plan());
        for (String secret : plan.credentials().values()) assertFalse(decoded.toString().contains(secret));
        assertEquals(0, input.available());
    }
    @Test void missingCredentialsSelfPromotionAndActiveWorldAreRejected() {
        var original = lobby(); var valid = plan(original, false);
        assertThrows(IllegalArgumentException.class, () -> new LeadershipPlan(UUID.randomUUID(), b, false, original.snapshot(), Map.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new LeadershipPlan(UUID.randomUUID(), a, false, original.snapshot(), valid.credentials(), Set.of()));
        original.world(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> new LeadershipPlan(UUID.randomUUID(), b, false, original.snapshot(), valid.credentials(), Set.of()));
    }
    @Test void oversizedCredentialTablesAreRejectedBeforeAllocation() throws Exception {
        var plan = plan(lobby(), false); var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
        Wire.uuid(out, plan.transaction()); Wire.uuid(out, b); out.writeBoolean(false); Wire.snapshot(out, plan.snapshot()); out.writeInt(Integer.MAX_VALUE);
        assertThrows(IOException.class, () -> LeadershipPacket.read(20, new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }
}
