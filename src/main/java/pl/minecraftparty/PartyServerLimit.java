package pl.minecraftparty;

/** Zero restores Minecraft's normal limit; a positive value is scoped to Party sharing. */
public interface PartyServerLimit {
    void party$setPlayerLimit(int limit);
}
