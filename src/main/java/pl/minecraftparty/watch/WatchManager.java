package pl.minecraftparty.watch;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import pl.minecraftparty.PartyManager;
import pl.minecraftparty.core.*;
import java.util.UUID;

/** Client-thread coordinator; the native browser is optional and each viewer explicitly opts in. */
public final class WatchManager {
    private final Minecraft client;
    private final PartyManager party;
    private WatchState state;
    private UUID accepted;
    private RinkuPlayer player;
    private WatchPageServer page;
    private boolean opening;
    private int generation, volume = 60;
    private String error = "";
    public WatchManager(Minecraft client, PartyManager party) { this.client = client; this.party = party; }
    public WatchState state() { return state; }
    public boolean watching() { return player != null; }
    public boolean opening() { return opening; }
    public int volume() { return volume; }
    public void volume(int value) { volume = Math.clamp(value, 0, 100); }
    public String status() {
        if (!error.isEmpty()) return error;
        if (!RinkuPlayer.installed()) return "missing";
        if (opening) return "loading";
        if (page != null) return page.status();
        return state == null ? "idle" : "offered";
    }
    public void create(String source, float width) {
        HostNode host = party.host();
        if (host == null || client.player == null || client.level == null || party.busy()) return;
        final String id;
        try { id = YouTubeSource.videoId(source); }
        catch (IllegalArgumentException e) { error = "invalid_link"; return; }
        double yaw = Math.toRadians(client.player.getYRot());
        var p = new WatchState.Placement(client.level.dimension().identifier().toString(),
                client.player.getX() - Math.sin(yaw) * 6, client.player.getEyeY() + 0.5,
                client.player.getZ() + Math.cos(yaw) * 6, Mth.wrapDegrees(180 - client.player.getYRot()), width);
        int operation = generation;
        party.background(() -> {
            try {
                host.startWatch(id, p); WatchState started = host.watch();
                client.execute(() -> { if (generation == operation && party.host() == host) { state = started; join(); } });
            } catch (IllegalStateException | IllegalArgumentException e) {
                client.execute(() -> { if (generation == operation) error = "world_required"; });
            }
        });
    }
    public void play(boolean playing) {
        HostNode host = party.host(); if (host == null || state == null || party.busy()) return;
        party.background(() -> { try { host.playWatch(playing); } catch (IllegalStateException ignored) { } });
    }
    public void seek(long positionMs) {
        HostNode host = party.host(); if (host == null || state == null || party.busy()) return;
        long bounded = Math.clamp(positionMs, 0, WatchState.MAX_POSITION_MS);
        party.background(() -> { try { host.seekWatch(bounded); } catch (IllegalStateException ignored) { } });
    }
    public void stop() { HostNode host = party.host(); if (host != null && !party.busy()) party.background(host::stopWatch); }
    public void join() {
        if (state == null || !party.inPartyWorld()) { error = "world_required"; return; }
        if (!RinkuPlayer.installed()) { error = "missing"; return; }
        closeLocal(); accepted = state.sessionId(); error = "";
        open();
    }
    public void leave() { accepted = null; error = ""; closeLocal(); }
    public void reset() { leave(); state = null; }
    private void open() {
        if (opening || player != null || state == null || !state.sessionId().equals(accepted)) return;
        opening = true; int operation = generation; WatchState starting = state;
        party.background(() -> {
            WatchPageServer server = null;
            try {
                server = new WatchPageServer(); server.update(starting, 0);
                WatchPageServer ready = server;
                client.execute(() -> {
                    if (operation != generation || state == null || !starting.sessionId().equals(accepted) || !party.inPartyWorld()) {
                        party.background(ready::close); return;
                    }
                    opening = false;
                    try { player = new RinkuPlayer(ready.url()); page = ready; }
                    catch (ReflectiveOperationException | RuntimeException | LinkageError e) { error = "browser_error"; party.background(ready::close); }
                });
            } catch (Exception e) {
                if (server != null) server.close();
                client.execute(() -> { if (operation == generation) { opening = false; error = "browser_error"; } });
            }
        });
    }
    private void closeLocal() {
        ++generation; opening = false;
        if (player != null) { player.close(); player = null; }
        if (page != null) { WatchPageServer old = page; page = null; party.background(old::close); }
    }
    public boolean sameDimension() {
        return state != null && party.inPartyWorld() && client.player != null
                && state.placement().dimension().equals(client.level.dimension().identifier().toString());
    }
    public Identifier texture() { return player == null ? null : player.texture(); }
    public void click(double u, double v, int button) { if (player != null) player.click(u, v, button); }
    public void release(double u, double v, int button) { if (player != null) player.release(u, v, button); }
    public void move(double u, double v) { if (player != null) player.move(u, v); }
    public void wheel(double u, double v, double amount, int modifiers) { if (player != null) player.wheel(u, v, amount, modifiers); }
    public void unfocus() { if (player != null) player.unfocus(); }
    public void tick() {
        WatchState next = party.host() != null ? party.host().watch() : party.guest() != null && party.guest().connected() ? party.guest().watch() : null;
        if (next != null && (party.state() == null || !next.worldId().equals(party.state().worldId()))) next = null;
        state = next;
        if (state == null || !party.inPartyWorld() || !state.sessionId().equals(accepted)) {
            if (player != null || opening) closeLocal();
            if (!party.active()) accepted = null;
            return;
        }
        if (player == null && !opening && error.isEmpty()) open();
        if (page != null) {
            var p = state.placement();
            double distance = sameDimension() ? Math.sqrt(client.player.distanceToSqr(p.x(), p.y(), p.z())) : 1000;
            double fade = Math.clamp((48 - distance) / 32, 0, 1);
            int audible = (int) (volume * fade * client.options.getSoundSourceVolume(SoundSource.MASTER));
            page.update(state, audible);
        }
    }
}
