package pl.minecraftparty.watch;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import pl.minecraftparty.core.WatchState;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Loopback-only read-only player page. No video bytes, accounts, remote commands or public listener. */
public final class WatchPageServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService worker = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), Thread.ofPlatform().daemon().name("party-watch-page-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    private final String path = "/" + capability() + "/";
    private final byte[] html = resource("player.html"), script = resource("player.js");
    private volatile View view;
    private volatile String status = "loading";
    public record View(WatchState watch, int volume) { }
    public WatchPageServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8);
        server.setExecutor(worker); server.createContext("/", exchange -> {
            try (exchange) {
                String host = "127.0.0.1:" + server.getAddress().getPort();
                if (!"GET".equals(exchange.getRequestMethod()) || !host.equals(exchange.getRequestHeaders().getFirst("Host"))) {
                    exchange.sendResponseHeaders(403, -1); return;
                }
                String endpoint = exchange.getRequestURI().getRawPath();
                byte[] body; String type;
                if (endpoint.equals(path)) { body = html; type = "text/html"; }
                else if (endpoint.equals(path + "player.js")) { body = script; type = "text/javascript"; }
                else if (endpoint.equals(path + "state")) {
                    View current = view;
                    if (current == null) { exchange.sendResponseHeaders(410, -1); return; }
                    WatchState watch = current.watch(); JsonObject json = new JsonObject();
                    json.addProperty("video", watch.videoId()); json.addProperty("revision", watch.revision());
                    json.addProperty("position", watch.positionAt(System.nanoTime()) / 1000.0);
                    json.addProperty("playing", watch.playing()); json.addProperty("volume", current.volume());
                    body = json.toString().getBytes(StandardCharsets.UTF_8); type = "application/json";
                } else if (endpoint.equals(path + "status")) {
                    String query = exchange.getRequestURI().getRawQuery();
                    if (query == null || !query.matches("value=(loading|ready|playing|paused|buffering|blocked|ended|error(_(2|5|100|101|150|153))?)")) {
                        exchange.sendResponseHeaders(400, -1); return;
                    }
                    status = query.substring(6); exchange.sendResponseHeaders(204, -1); return;
                } else { exchange.sendResponseHeaders(404, -1); return; }
                var headers = exchange.getResponseHeaders();
                headers.set("Content-Type", type + "; charset=utf-8"); headers.set("Cache-Control", "no-store");
                headers.set("X-Content-Type-Options", "nosniff"); headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
                headers.set("Content-Security-Policy", "default-src 'none'; script-src 'self' https://www.youtube.com https://s.ytimg.com; style-src 'unsafe-inline'; connect-src 'self'; frame-src https://www.youtube.com https://www.youtube-nocookie.com; base-uri 'none'; object-src 'none'; frame-ancestors 'none'");
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
            } catch (IOException ignored) { /* Closed or cancelled browser request; never log the capability URL. */ }
        });
        server.start();
    }
    public String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + path; }
    public void update(WatchState watch, int volume) { view = new View(watch, Math.clamp(volume, 0, 100)); }
    public String status() { return status; }
    private static byte[] resource(String name) {
        try (InputStream input = WatchPageServer.class.getResourceAsStream("/assets/minecraftparty/watch/" + name)) {
            if (input == null) throw new IllegalStateException("missing watch resource");
            return input.readAllBytes();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private static String capability() {
        byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    @Override public void close() { view = null; server.stop(0); worker.shutdownNow(); }
}
