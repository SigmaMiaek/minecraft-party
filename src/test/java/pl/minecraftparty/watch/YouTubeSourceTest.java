package pl.minecraftparty.watch;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class YouTubeSourceTest {
    @Test void ordinaryLinksShortLinksAndIdsResolveWithoutFetchingAnything() {
        for (String link : new String[]{"M7lc1UVf-VE", "https://www.youtube.com/watch?v=M7lc1UVf-VE&t=20",
                "https://youtu.be/M7lc1UVf-VE?si=abc", "https://m.youtube.com/shorts/M7lc1UVf-VE", "https://youtube.com/embed/M7lc1UVf-VE"})
            assertEquals("M7lc1UVf-VE", YouTubeSource.videoId(link));
    }
    @Test void remoteAddressesScriptsCredentialsDrmSitesAndAmbiguousIdsAreRejected() {
        for (String link : new String[]{"", "javascript:alert(1)", "file:///etc/passwd", "http://127.0.0.1/", "https://www.netflix.com/watch/1234",
                "https://primevideo.com/", "https://youtube.com.evil.test/watch?v=M7lc1UVf-VE", "https://youtube.com@evil.test/watch?v=M7lc1UVf-VE",
                "https://youtube.com:8443/watch?v=M7lc1UVf-VE", "https://youtu.be/../M7lc1UVf-VE", "https://youtu.be/", "https://youtu.be",
                "https://youtube.com/watch?v=M7lc1UVf-VE&v=M7lc1UVf-VE", "https://youtube.com/watch?v=%3Cscript%3E"})
            assertThrows(IllegalArgumentException.class, () -> YouTubeSource.videoId(link), link);
    }
}
