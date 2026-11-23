package pl.minecraftparty.core;

import java.util.UUID;

/** One host-controlled YouTube screen. Times use a local monotonic clock, never a peer's clock. */
public record WatchState(UUID sessionId, UUID worldId, String videoId, Placement placement, long revision,
                         long positionMs, boolean playing, long sampledAtNanos) {
    public static final long MAX_POSITION_MS = 7L * 24 * 60 * 60 * 1000;
    public WatchState {
        if (sessionId == null || worldId == null || placement == null || videoId == null
                || !videoId.matches("[A-Za-z0-9_-]{11}") || revision < 0 || positionMs < 0 || positionMs > MAX_POSITION_MS)
            throw new IllegalArgumentException("watch state");
    }
    public record Placement(String dimension, double x, double y, double z, float yaw, float width) {
        public Placement {
            if (dimension == null || dimension.length() > 128 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 4096
                    || !Float.isFinite(yaw) || yaw < -180 || yaw > 180
                    || !Float.isFinite(width) || width < 2 || width > 16) throw new IllegalArgumentException("screen placement");
        }
        public float height() { return width * 9 / 16; }
    }
    public long positionAt(long now) {
        long elapsed = playing ? Math.max(0, (now - sampledAtNanos) / 1_000_000) : 0;
        return Math.min(MAX_POSITION_MS, positionMs + Math.min(MAX_POSITION_MS, elapsed));
    }
    public WatchState sample(long now) { return new WatchState(sessionId, worldId, videoId, placement, revision, positionAt(now), playing, now); }
    public WatchState control(long position, boolean play, long now) {
        return new WatchState(sessionId, worldId, videoId, placement, Math.incrementExact(revision), position, play, now);
    }
    @Override public String toString() { return "WatchState[REDACTED]"; }
}
