package pl.minecraftparty.network;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PublicPartyTest {
    private JsonObject page() {
        return JsonParser.parseString("""
                {"publicLobbies":true,"page":0,"pages":1,"lobbies":[
                 {"code":"001234","name":"Przygoda 🎮","host":"Host","players":2,"limit":6,
                  "inWorld":false,"minecraft":"26.2","protocol":4}]}
                """).getAsJsonObject();
    }
    @Test void publicNamesLeadingZeroesAndCapacityAreReadWithoutAddresses() throws Exception {
        var result = ShortCodeClient.parsePublicPage(page(), 0, 8);
        assertEquals("001234", result.lobbies().getFirst().code());
        assertEquals("Przygoda 🎮", result.lobbies().getFirst().name());
        assertFalse(result.lobbies().getFirst().full());
        assertFalse(result.lobbies().getFirst().toString().contains("001234"));
        assertThrows(UnsupportedOperationException.class, () -> result.lobbies().clear());
    }
    @Test void malformedNamesAndCapacitiesAreRejected() {
        for (String name : new String[]{"", " ", "a\nHost", "§kHidden", "\u202eRTL", "a".repeat(41), "\ud800"})
            assertThrows(IllegalArgumentException.class, () -> new PublicParty("001234", name, "Host", 1, 6, false));
        for (int count : new int[]{-1,0,7})
            assertThrows(IllegalArgumentException.class, () -> new PublicParty("001234", "Party", "Host", count, 6, false));
        assertTrue(new PublicParty("001234", "Party", "Host", 6, 6, true).full());
    }
    @Test void oversizedMismatchedDuplicateAndUnsupportedPagesAreRejected() {
        var json = page(); json.addProperty("page", 1);
        assertThrows(ShortCodeClient.Failure.class, () -> ShortCodeClient.parsePublicPage(json, 0, 8));
        for (String key : new String[]{"protocol", "minecraft", "players", "inWorld"}) {
            var bad = page(); bad.getAsJsonArray("lobbies").get(0).getAsJsonObject().addProperty(key, "unexpected");
            assertThrows(ShortCodeClient.Failure.class, () -> ShortCodeClient.parsePublicPage(bad, 0, 8));
        }
        var duplicate = page(); duplicate.getAsJsonArray("lobbies").add(duplicate.getAsJsonArray("lobbies").get(0).deepCopy());
        assertThrows(ShortCodeClient.Failure.class, () -> ShortCodeClient.parsePublicPage(duplicate, 0, 8));
        assertThrows(ShortCodeClient.Failure.class, () -> ShortCodeClient.parsePublicPage(page(), 0, 0));
    }
}
