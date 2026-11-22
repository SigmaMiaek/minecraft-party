package pl.minecraftparty;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PartyConfigTest {
    @Test void oldConfigWithEmptyServiceWorksWithoutEnteringAUrlAndKeepsWorldPreferences() {
        var old = new Gson().fromJson("""
                {"codeServiceUrl":"", "networkMode":"LAN", "lobbyName":"Friends", "memberLimit":5,
                 "shareMultiplayer":false, "allowPartyTeleport":true}
                """, PartyConfig.class);
        var migrated = PartyConfig.normalize(old);
        assertEquals(PartyConfig.DEFAULT_CODE_SERVICE_URL, migrated.codeServiceUrl);
        assertEquals(PartyConfig.NetworkMode.RELAY, migrated.networkMode);
        assertEquals("Friends", migrated.lobbyName); assertEquals(5, migrated.memberLimit);
        assertFalse(migrated.shareMultiplayer); assertTrue(migrated.allowPartyTeleport);
    }
    @Test void newMissingAndNullServicePreferencesAllUseTheDeployedDefault() {
        assertEquals(PartyConfig.DEFAULT_CODE_SERVICE_URL, new PartyConfig().codeServiceUrl);
        for (String json : new String[]{"{}", "{\"codeServiceUrl\":null}", "{\"codeServiceUrl\":\"  \"}"})
            assertEquals(PartyConfig.DEFAULT_CODE_SERVICE_URL, PartyConfig.normalize(new Gson().fromJson(json, PartyConfig.class)).codeServiceUrl);
    }
    @Test void validAdministratorOverrideSurvivesAndInsecureServiceFallsBackToDefault() {
        var config = new PartyConfig(); config.codeServiceUrl = "https://party.custom.workers.dev/";
        assertEquals("https://party.custom.workers.dev", PartyConfig.normalize(config).codeServiceUrl);
        config.codeServiceUrl = "http://untrusted.example";
        assertEquals(PartyConfig.DEFAULT_CODE_SERVICE_URL, PartyConfig.normalize(config).codeServiceUrl);
    }
}
