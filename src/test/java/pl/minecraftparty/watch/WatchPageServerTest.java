package pl.minecraftparty.watch;

import org.junit.jupiter.api.*;
import pl.minecraftparty.core.WatchState;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Real loopback HTTP server; does not access YouTube or load Chromium. */
@Tag("network") @Timeout(10)
final class WatchPageServerTest {
    @Test void onlyTheLocalCapabilityCanReadThePlayerAndItExposesNoMutationApi() throws Exception {
        try (var page = new WatchPageServer(); var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            page.update(new WatchState(UUID.randomUUID(), UUID.randomUUID(), "M7lc1UVf-VE",
                    new WatchState.Placement("minecraft:overworld", 0, 64, 0, 0, 6), 3, 12000, false, 0), 60);
            URI root = URI.create(page.url()); assertEquals("127.0.0.1", root.getHost());
            var html = get(http, root); assertEquals(200, html.statusCode());
            assertEquals("no-store", html.headers().firstValue("Cache-Control").orElseThrow());
            assertTrue(html.headers().firstValue("Content-Security-Policy").orElseThrow().contains("frame-ancestors 'none'"));
            assertTrue(html.body().contains("player.js"));
            var data = get(http, root.resolve(root.getPath() + "state"));
            assertEquals(200, data.statusCode()); assertTrue(data.body().contains("\"position\":12.0"));
            assertFalse(data.body().contains("owner")); assertFalse(data.body().contains("placement"));
            assertEquals(404, get(http, root.resolve("/wrong/state")).statusCode());
            var post = HttpRequest.newBuilder(root.resolve(root.getPath() + "state")).POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .timeout(Duration.ofSeconds(2)).build();
            assertEquals(403, http.send(post, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(400, get(http, root.resolve(root.getPath() + "status?value=anything")).statusCode());
            assertEquals(204, get(http, root.resolve(root.getPath() + "status?value=error_150")).statusCode());
            assertEquals("error_150", page.status());
        }
    }
    private static HttpResponse<String> get(HttpClient http, URI uri) throws Exception {
        return http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
