package pl.minecraftparty.mixin;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IntegratedServer.class)
public interface IntegratedServerAccess {
    @Accessor("publishedPort") void party$setPublishedPort(int port);
    @Accessor("multiplayerScope") void party$setScope(MinecraftServer.MultiplayerScope scope);
}
