package pl.minecraftparty.watch;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Converts supported YouTube links to an ID. No supplied URL is ever navigated or fetched. */
public final class YouTubeSource {
    private YouTubeSource() { }
    public static String videoId(String input) {
        if (input == null || input.length() > 2048) throw new IllegalArgumentException("YouTube link");
        String value = input.strip();
        if (value.matches("[A-Za-z0-9_-]{11}")) return value;
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getPort() != -1)
            throw new IllegalArgumentException("YouTube link");
        String id = "";
        if (uri.getHost().equals("youtu.be") && uri.getPath().length() > 1) id = uri.getPath().substring(1);
        else if (Set.of("youtube.com", "www.youtube.com", "m.youtube.com").contains(uri.getHost())) {
            if (uri.getPath().equals("/watch") && uri.getRawQuery() != null) {
                for (String part : uri.getRawQuery().split("&")) if (part.startsWith("v=")) {
                    if (!id.isEmpty()) throw new IllegalArgumentException("duplicate video");
                    id = URLDecoder.decode(part.substring(2), StandardCharsets.UTF_8);
                }
            } else for (String prefix : new String[]{"/shorts/", "/embed/", "/live/"})
                if (uri.getPath().startsWith(prefix)) id = uri.getPath().substring(prefix.length());
        }
        if (!id.matches("[A-Za-z0-9_-]{11}")) throw new IllegalArgumentException("YouTube link");
        return id;
    }
}
