package pl.minecraftparty;

import net.minecraft.client.Minecraft;
import pl.minecraftparty.network.ShortCodeClient;
import pl.minecraftparty.core.Invite;
import java.util.*;
import java.util.function.Consumer;

/** Optional directory lifecycle. Network work is serialized on the Party worker; UI changes on the client. */
public final class PartyCodes {
    private final PartyManager manager;
    private final Minecraft client;
    private volatile int epoch;
    private ShortCodeClient.Registration room;
    private String roomUrl = "", guestCode = "", guestName = "", guestToken = "", check = "";
    private List<ShortCodeClient.Pending> pending = List.of();
    private long next, deadline;
    private boolean working;
    private Consumer<String> approved, failed;
    PartyCodes(PartyManager manager, Minecraft client) { this.manager = manager; this.client = client; }
    public String code() { return room == null ? "" : room.code(); }
    public String check() { return check; }
    public List<ShortCodeClient.Pending> pending() { return pending; }
    public boolean working() { return working; }
    public void reset() {
        ++epoch;
        var old = room; String url = roomUrl;
        room = null; roomUrl = ""; pending = List.of(); guestCode = guestToken = check = "";
        approved = failed = null; working = false; next = 0;
        if (old != null) manager.background(() -> remove(url, old));
    }
    private static void remove(String url, ShortCodeClient.Registration room) {
        try (var service = new ShortCodeClient(url)) { service.remove(room); } catch (Exception ignored) { }
    }
    public void join(String code, String name, Consumer<String> approved, Consumer<String> failed) {
        reset(); guestCode = code.strip(); guestName = name; guestToken = ShortCodeClient.token();
        this.approved = approved; this.failed = failed; deadline = System.nanoTime() + 90_000_000_000L;
    }
    public void tick() {
        if (working || System.nanoTime() < next || manager.config.codeServiceUrl.isBlank()) return;
        if (!guestCode.isEmpty()) { pollGuest(); return; }
        if (manager.host() == null || !manager.invite().startsWith("mp3:")) return;
        try {
            if (Invite.decode(manager.invite()).expiresAt() - System.currentTimeMillis() < 60000) { manager.rotateInvite(); return; }
        } catch (java.io.IOException e) { manager.rotateInvite(); return; }
        int operation = epoch; String url = manager.config.codeServiceUrl, invite = manager.invite();
        var current = room; working = true; next = System.nanoTime() + 10_000_000_000L;
        manager.background(() -> {
            try (var service = new ShortCodeClient(url)) {
                var registered = current == null ? service.create(invite) : current;
                var requests = service.pollHost(registered);
                client.execute(() -> {
                    if (epoch != operation) { if (current == null) manager.background(() -> remove(url, registered)); return; }
                    room = registered; roomUrl = url; pending = requests; working = false;
                });
            } catch (Exception e) {
                client.execute(() -> {
                    if (epoch != operation) return;
                    working = false; pending = List.of(); room = null;
                    manager.message(e instanceof ShortCodeClient.Failure f ? f.key() : "codes_unavailable");
                });
            }
        });
    }
    private void pollGuest() {
        if (System.nanoTime() >= deadline) { reject("codes_expired"); return; }
        int operation = epoch; String url = manager.config.codeServiceUrl, code = guestCode, name = guestName, token = guestToken;
        boolean poll = !check.isEmpty(); working = true; next = System.nanoTime() + 3_000_000_000L;
        manager.background(() -> {
            try (var service = new ShortCodeClient(url)) {
                var result = service.request(code, name, token, poll);
                client.execute(() -> {
                    if (epoch != operation) return;
                    working = false; check = result.check();
                    if (result.status().equals("denied")) { reject("codes_denied"); return; }
                    if (result.status().equals("approved")) {
                        var callback = approved; guestCode = guestToken = ""; approved = failed = null;
                        callback.accept(result.invite());
                    } else manager.message("codes_waiting");
                });
            } catch (Exception e) {
                client.execute(() -> { if (epoch == operation) reject(e instanceof ShortCodeClient.Failure f ? f.key() : "codes_unavailable"); });
            }
        });
    }
    private void reject(String key) {
        var callback = failed; guestCode = guestToken = check = ""; working = false; approved = failed = null;
        if (callback != null) callback.accept(key);
    }
    public void decide(String id, boolean allow) {
        if (room == null || working) return;
        int operation = epoch; var current = room; String url = roomUrl; working = true;
        manager.background(() -> {
            try (var service = new ShortCodeClient(url)) {
                service.decide(current, id, allow);
                client.execute(() -> {
                    if (epoch != operation) return;
                    pending = pending.stream().filter(p -> !p.id().equals(id)).toList(); working = false; next = 0;
                });
            } catch (Exception e) { client.execute(() -> { if (epoch == operation) { working = false; manager.message("codes_unavailable"); } }); }
        });
    }
}
