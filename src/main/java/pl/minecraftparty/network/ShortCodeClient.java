package pl.minecraftparty.network;

import com.google.gson.*;
import pl.minecraftparty.core.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

/** Optional HTTPS directory. It is trusted with invitations; six digits alone never authorize release. */
public final class ShortCodeClient implements AutoCloseable {
    public record Registration(String code, String owner, long expiresAt) {
        @Override public String toString() { return "Registration[REDACTED]"; }
    }
    public record Pending(String id, String name, String check) { }
    public record Result(String status, String check, String invite) {
        @Override public String toString() { return "Result[REDACTED]"; }
    }
    public static final class Failure extends IOException {
        private final String key;
        public Failure(String key) { super(key); this.key = key; }
        public String key() { return key; }
    }
    private final URI endpoint;
    private final HttpClient http;
    public ShortCodeClient(String address) {
        endpoint = endpoint(address);
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public static URI endpoint(String value) {
        URI uri = URI.create(value.strip());
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !uri.getHost().matches("[a-z0-9-]+\\.[a-z0-9-]+\\.workers\\.dev")
                || uri.getPort() != -1 || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) throw new IllegalArgumentException("code service address");
        return URI.create("https://" + uri.getHost());
    }
    public static boolean isCode(String value) { return value != null && value.strip().matches("[0-9]{6}"); }
    public static String token() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public Registration create(String invite) throws IOException {
        Invite parsed = Invite.decode(invite); if (!parsed.relay()) throw new Failure("codes_relay_only");
        JsonObject body = new JsonObject(); body.addProperty("invite", invite); body.addProperty("expiresAt", parsed.expiresAt());
        JsonObject response = call("create", body);
        String code = string(response, "code", "[0-9]{6}"), owner = string(response, "owner", "[A-Za-z0-9_-]{43}");
        long expiry = response.get("expiresAt").getAsLong();
        if (expiry <= System.currentTimeMillis()) throw new Failure("codes_unavailable");
        return new Registration(code, owner, expiry);
    }
    public List<Pending> pollHost(Registration room) throws IOException {
        JsonObject response = call("host", owner(room));
        var array = response.getAsJsonArray("pending");
        if (array == null || array.size() > 12) throw new Failure("codes_unavailable");
        var pending = new ArrayList<Pending>();
        for (var entry : array) {
            JsonObject item = entry.getAsJsonObject();
            pending.add(new Pending(string(item, "id", "[A-Za-z0-9_-]{43}"),
                    string(item, "name", "[A-Za-z0-9_]{1,16}"), string(item, "check", "[0-9]{6}")));
        }
        return List.copyOf(pending);
    }
    public void decide(Registration room, String id, boolean allow) throws IOException {
        JsonObject body = owner(room); body.addProperty("id", id); body.addProperty("allow", allow); call("decide", body);
    }
    public void remove(Registration room) throws IOException { call("remove", owner(room)); }
    public Result request(String code, String name, String token, boolean poll) throws IOException {
        JsonObject body = new JsonObject(); body.addProperty("code", code); body.addProperty("name", name); body.addProperty("requestToken", token);
        JsonObject response = call(poll ? "poll" : "request", body);
        String status = string(response, "status", "pending|approved|denied"), check = string(response, "check", "[0-9]{6}");
        String invite = "";
        if (status.equals("approved")) {
            invite = response.get("invite").getAsString();
            if (!Invite.decode(invite).relay()) throw new Failure("codes_unavailable");
        }
        return new Result(status, check, invite);
    }
    private static JsonObject owner(Registration room) {
        JsonObject body = new JsonObject(); body.addProperty("code", room.code()); body.addProperty("owner", room.owner()); return body;
    }
    private JsonObject call(String action, JsonObject body) throws IOException {
        try {
            var request = HttpRequest.newBuilder(endpoint.resolve("/v1/" + action)).timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "MinecraftParty/0.6.1 (+https://github.com/SigmaMiaek/minecraft-party)")
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            var response = http.send(request, info -> new LimitedHttpBody(16384));
            if (response.statusCode() != 200) throw new Failure(switch (response.statusCode()) {
                case 404 -> "codes_expired"; case 429 -> "codes_rate"; default -> "codes_unavailable";
            });
            return JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new Failure("codes_unavailable"); }
        catch (Failure e) { throw e; }
        catch (RuntimeException | IOException e) { throw new Failure("codes_unavailable"); }
    }
    private static String string(JsonObject json, String key, String pattern) throws Failure {
        try { String value = json.get(key).getAsString(); if (value.length() <= 64 && value.matches(pattern)) return value; }
        catch (RuntimeException ignored) { }
        throw new Failure("codes_unavailable");
    }
    @Override public void close() { http.close(); }
}
