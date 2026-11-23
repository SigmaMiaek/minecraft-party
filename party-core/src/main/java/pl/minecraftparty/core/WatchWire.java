package pl.minecraftparty.core;

import java.io.*;

/** Only a validated video ID and bounded screen geometry cross the lobby connection. */
final class WatchWire {
    private WatchWire() { }
    static void write(DataOutput out, WatchState value) throws IOException {
        out.writeBoolean(value != null);
        if (value == null) return;
        WatchState state = value.sample(System.nanoTime());
        Wire.uuid(out, state.sessionId()); Wire.uuid(out, state.worldId()); out.writeUTF(state.videoId());
        var p = state.placement(); out.writeUTF(p.dimension());
        out.writeDouble(p.x()); out.writeDouble(p.y()); out.writeDouble(p.z()); out.writeFloat(p.yaw()); out.writeFloat(p.width());
        out.writeLong(state.revision()); out.writeLong(state.positionMs()); out.writeBoolean(state.playing());
    }
    static WatchState read(DataInput in) throws IOException {
        if (!in.readBoolean()) return null;
        try {
            var session = Wire.uuid(in); var world = Wire.uuid(in); String video = Wire.text(in, 11);
            var placement = new WatchState.Placement(Wire.text(in, 128), in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat(), in.readFloat());
            return new WatchState(session, world, video, placement, in.readLong(), in.readLong(), in.readBoolean(), System.nanoTime());
        } catch (IllegalArgumentException e) { throw new IOException("watch state", e); }
    }
}
