package pl.minecraftparty.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ShortCodeValidationTest {
    @Test void codesPreserveLeadingZeroesAndCannotEnablePartialOrNonAsciiInvites() {
        assertTrue(ShortCodeClient.isCode(" 001234 "));
        for (String bad : new String[]{"12345", "1234567", "123 456", "１２３４５６", "mp3:", "12345x"})
            assertFalse(ShortCodeClient.isCode(bad));
        assertFalse(ShortCodeClient.isCode(null));
    }
    @Test void serviceAddressCannotRedirectCredentialsToArbitraryHostsOrPaths() {
        assertEquals("https://minecraft-party-codes.adam.workers.dev", ShortCodeClient.endpoint(" https://minecraft-party-codes.adam.workers.dev/ ").toString());
        for (String bad : new String[]{"http://a.b.workers.dev", "https://localhost", "https://a.b.workers.dev.evil.com",
                "https://user@a.b.workers.dev", "https://a.b.workers.dev:443", "https://a.b.workers.dev/path",
                "https://a.b.workers.dev?token=secret", "https://a.b.workers.dev#secret", "https://workers.dev"})
            assertThrows(IllegalArgumentException.class, () -> ShortCodeClient.endpoint(bad));
    }
    @Test void capabilitiesAreStrongAndNeverIncludedInRecordLogging() {
        String first = ShortCodeClient.token(), second = ShortCodeClient.token();
        assertTrue(first.matches("[A-Za-z0-9_-]{43}")); assertNotEquals(first, second);
        assertFalse(new ShortCodeClient.Registration("001234", first, 1).toString().contains(first));
        assertFalse(new ShortCodeClient.Result("approved", "001234", "secret-invite").toString().contains("secret-invite"));
    }
}
