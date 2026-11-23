package pl.minecraftparty.core;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class WatchStateTest {
    private final WatchState.Placement screen = new WatchState.Placement("minecraft:overworld", 10, 70, 20, 180, 6);
    private WatchState state(long now) { return new WatchState(UUID.randomUUID(), UUID.randomUUID(), "M7lc1UVf-VE", screen, 0, 12_000, false, now); }
    @Test void pausePlayAndSeekUseMonotonicTimeWithoutChangingTheScreen() {
        var paused = state(1_000_000_000L);
        assertEquals(12_000, paused.positionAt(999_000_000_000L));
        var playing = paused.control(12_000, true, 2_000_000_000L);
        assertEquals(15_000, playing.positionAt(5_000_000_000L));
        var stopped = playing.control(playing.positionAt(5_000_000_000L), false, 5_000_000_000L);
        assertEquals(15_000, stopped.positionAt(9_000_000_000L));
        var sought = stopped.control(65_000, true, 9_000_000_000L);
        assertEquals(66_000, sought.positionAt(10_000_000_000L));
        assertEquals(paused.sessionId(), sought.sessionId()); assertEquals(screen, sought.placement());
        assertEquals(3, sought.revision());
    }
    @Test void identitiesPositionsAndUntrustedGeometryAreBounded() {
        for (String id : new String[]{"", "https://example.com", "<script>", "M7lc1UVf-VE?"})
            assertThrows(IllegalArgumentException.class, () -> new WatchState(UUID.randomUUID(), UUID.randomUUID(), id, screen, 0, 0, false, 0));
        for (double x : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 30_000_001})
            assertThrows(IllegalArgumentException.class, () -> new WatchState.Placement("minecraft:overworld", x, 0, 0, 0, 6));
        for (float width : new float[]{Float.NaN, 0, 17})
            assertThrows(IllegalArgumentException.class, () -> new WatchState.Placement("minecraft:overworld", 0, 0, 0, 0, width));
        assertThrows(IllegalArgumentException.class, () -> state(0).control(-1, false, 0));
        assertThrows(IllegalArgumentException.class, () -> state(0).control(WatchState.MAX_POSITION_MS + 1, false, 0));
        assertFalse(state(0).toString().contains("M7lc1UVf-VE"));
    }
    @Test void pausedScreenRoundTripsAndTruncatedPacketsAreRejected() throws Exception {
        var original = state(System.nanoTime()); var bytes = new ByteArrayOutputStream();
        WatchWire.write(new DataOutputStream(bytes), original); byte[] payload = bytes.toByteArray();
        var decoded = WatchWire.read(input(payload));
        assertEquals(original.sessionId(), decoded.sessionId()); assertEquals(original.worldId(), decoded.worldId());
        assertEquals(original.placement(), decoded.placement()); assertEquals(original.videoId(), decoded.videoId());
        assertEquals(12_000, decoded.positionMs()); assertFalse(decoded.playing());
        for (int i = 0; i < payload.length; i++) {
            byte[] cut = Arrays.copyOf(payload, i);
            assertThrows(IOException.class, () -> WatchWire.read(input(cut)));
        }
        assertNull(WatchWire.read(input(new byte[]{0})));
    }
    private static DataInputStream input(byte[] bytes) { return new DataInputStream(new ByteArrayInputStream(bytes)); }
}
