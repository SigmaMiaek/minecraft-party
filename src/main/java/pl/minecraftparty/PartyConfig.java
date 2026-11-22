package pl.minecraftparty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.*;

/** Preferences and the public directory URL only; invitations and credentials are never persisted. */
public final class PartyConfig {
    public enum NetworkMode { RELAY, DIRECT, LAN }
    public int memberLimit = 6;
    public String lobbyName = "Minecraft Party";
    public boolean shareMultiplayer = true;
    public boolean allowPartyTeleport = false;
    public boolean internetParty = true;
    public NetworkMode networkMode = NetworkMode.RELAY;
    public static final String DEFAULT_CODE_SERVICE_URL = "https://minecraft-party.wojciechowskaola57.workers.dev";
    public String codeServiceUrl = DEFAULT_CODE_SERVICE_URL;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("minecraft-party.json"); }
    public static PartyConfig load() {
        try {
            if (!Files.exists(path())) return new PartyConfig();
            String json = Files.readString(path());
            PartyConfig config = GSON.fromJson(json, PartyConfig.class);
            return normalize(config);
        } catch (IOException | RuntimeException e) { return new PartyConfig(); }
    }
    static PartyConfig normalize(PartyConfig config) {
        if (config == null) return new PartyConfig();
        config.networkMode = NetworkMode.RELAY;
        if (config.codeServiceUrl == null || config.codeServiceUrl.isBlank()) config.codeServiceUrl = DEFAULT_CODE_SERVICE_URL;
        try { config.codeServiceUrl = pl.minecraftparty.network.ShortCodeClient.endpoint(config.codeServiceUrl).toString(); }
        catch (IllegalArgumentException e) { config.codeServiceUrl = DEFAULT_CODE_SERVICE_URL; }
        config.memberLimit = Math.clamp(config.memberLimit, 2, pl.minecraftparty.core.LobbyModel.MAX_MEMBERS);
        if (config.lobbyName == null || config.lobbyName.isBlank() || config.lobbyName.length() > 40)
            config.lobbyName = "Minecraft Party";
        return config;
    }
    public void save() {
        try {
            internetParty = networkMode != NetworkMode.LAN;
            Files.createDirectories(path().getParent()); Files.writeString(path(), GSON.toJson(this));
        } catch (IOException e) { PartyClient.LOG.warn("Could not save Party preferences"); }
    }
    public String networkLabel() {
        return switch (networkMode) { case RELAY -> "network.relay"; case DIRECT -> "network.internet"; case LAN -> "network.lan"; };
    }
    public void nextNetworkMode() {
        networkMode = NetworkMode.values()[(networkMode.ordinal() + 1) % NetworkMode.values().length];
    }
}
