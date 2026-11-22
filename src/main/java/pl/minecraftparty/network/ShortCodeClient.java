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

/** HTTPS directory trusted with invitations. Private codes are access credentials; public codes are discoverable. */
public final class ShortCodeClient implements AutoCloseable {
    public record Registration(String code, String owner, long expiresAt) {
        @Override public String toString() { return "Registration[REDACTED]"; }
    }
    public record Pending(String id, String name, String check) { }
    public record PublicPage(int page, int pages, List<PublicParty> lobbies) {
        public PublicPage { lobbies = List.copyOf(lobbies); }
    }
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
        return create(invite, null);
    }
    public Registration create(String invite, LobbyState state) throws IOException {
        Invite parsed = Invite.decode(invite); if (!parsed.relay()) throw new Failure("codes_relay_only");
        JsonObject body = new JsonObject(); body.addProperty("invite", invite); body.addProperty("expiresAt", parsed.expiresAt());
        body.addProperty("autoJoin", true);
        boolean publicLobby = state != null && state.publicLobby();
        body.addProperty("publicLobby", publicLobby);
        if (publicLobby) body.add("listing", listing(state));
        JsonObject response = call("create", body);
        String code = string(response, "code", "[0-9]{6}"), owner = string(response, "owner", "[A-Za-z0-9_-]{43}");
        if (!response.has("autoJoin") || !response.get("autoJoin").getAsBoolean()) {
            try { remove(new Registration(code, owner, 0)); } catch (IOException ignored) { }
            throw new Failure("codes_update_required");
        }
        if (publicLobby && (!response.has("publicLobby") || !response.get("publicLobby").getAsBoolean())) {
            try { remove(new Registration(code, owner, 0)); } catch (IOException ignored) { }
            throw new Failure("public_update_required");
        }
        long expiry = response.get("expiresAt").getAsLong();
        if (expiry <= System.currentTimeMillis()) throw new Failure("codes_unavailable");
        return new Registration(code, owner, expiry);
    }
    public List<Pending> pollHost(Registration room) throws IOException {
        return pollHost(room, null);
    }
    public List<Pending> pollHost(Registration room, LobbyState state) throws IOException {
        JsonObject body = owner(room);
        if (state != null && state.publicLobby()) body.add("listing", listing(state));
        JsonObject response = call("host", body);
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
    private static JsonObject listing(LobbyState state) {
        JsonObject json = new JsonObject();
        json.addProperty("name", state.name());
        json.addProperty("host", state.members().stream().filter(LobbyState.Member::host).findFirst().orElseThrow().name());
        json.addProperty("players", state.members().size()); json.addProperty("limit", state.limit());
        json.addProperty("inWorld", state.worldId() != null);
        json.addProperty("minecraft", "26.2"); json.addProperty("protocol", PartyProtocol.VERSION);
        return json;
    }
    public PublicPage list(int page, int size) throws IOException {
        if (page < 0 || page > 255 || size < 1 || size > 16) throw new IllegalArgumentException("page");
        JsonObject body = new JsonObject(); body.addProperty("page", page); body.addProperty("size", size);
        body.addProperty("protocol", PartyProtocol.VERSION);
        return parsePublicPage(call("list", body), page, size);
    }
    static PublicPage parsePublicPage(JsonObject response, int page, int size) throws Failure {
        try {
            if (!flag(response, "publicLobbies")) throw new Failure("public_update_required");
            if (!response.has("protocol") || integer(response, "protocol") != PartyProtocol.VERSION) throw new Failure("public_update_required");
            int returnedPage = integer(response, "page"), pages = integer(response, "pages");
            JsonArray rows = response.getAsJsonArray("lobbies");
            if (returnedPage != page || pages < 1 || pages > 256 || rows == null || rows.size() > size
                    || page >= pages && !rows.isEmpty()) throw new IllegalArgumentException("page");
            List<PublicParty> result = new ArrayList<>(); Set<String> codes = new HashSet<>();
            for (JsonElement entry : rows) {
                JsonObject item = entry.getAsJsonObject();
                if (!text(item, "minecraft").equals("26.2") || integer(item, "protocol") != PartyProtocol.VERSION) throw new IllegalArgumentException("version");
                PublicParty lobby = new PublicParty(text(item, "code"), text(item, "name"), text(item, "host"),
                        integer(item, "players"), integer(item, "limit"), flag(item, "inWorld"));
                if (!codes.add(lobby.code())) throw new IllegalArgumentException("duplicate code");
                result.add(lobby);
            }
            return new PublicPage(page, pages, result);
        } catch (Failure e) { throw e; }
        catch (RuntimeException e) { throw new Failure("public_unavailable"); }
    }
    private static String text(JsonObject item, String key) {
        JsonPrimitive value = item.getAsJsonPrimitive(key);
        if (!value.isString()) throw new IllegalArgumentException("text");
        return value.getAsString();
    }
    private static int integer(JsonObject item, String key) {
        JsonPrimitive value = item.getAsJsonPrimitive(key);
        if (!value.isNumber()) throw new IllegalArgumentException("number");
        return value.getAsBigDecimal().intValueExact();
    }
    private static boolean flag(JsonObject item, String key) {
        JsonPrimitive value = item.getAsJsonPrimitive(key);
        if (!value.isBoolean()) throw new IllegalArgumentException("boolean");
        return value.getAsBoolean();
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
                    .header("User-Agent", "MinecraftParty/0.9.0 (+https://github.com/SigmaMiaek/minecraft-party)")
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            var response = http.send(request, info -> new LimitedHttpBody(16384));
            if (response.statusCode() != 200) throw new Failure(switch (response.statusCode()) {
                case 404 -> action.equals("list") ? "public_update_required" : "codes_expired";
                case 429 -> "codes_rate"; default -> "codes_unavailable";
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
