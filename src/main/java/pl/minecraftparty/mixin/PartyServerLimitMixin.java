package pl.minecraftparty.mixin;

import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.minecraftparty.PartyServerLimit;
import pl.minecraftparty.core.LobbyModel;

@Mixin(IntegratedServer.class)
public abstract class PartyServerLimitMixin implements PartyServerLimit {
    @Unique private volatile int party$playerLimit;

    @Override public void party$setPlayerLimit(int limit) {
        if (limit != 0 && (limit < 2 || limit > LobbyModel.MAX_MEMBERS)) throw new IllegalArgumentException("limit");
        party$playerLimit = limit;
    }
    @Inject(method = "getMaxPlayers", at = @At("HEAD"), cancellable = true)
    private void party$sharedLimit(CallbackInfoReturnable<Integer> result) {
        int limit = party$playerLimit;
        if (limit > 0) result.setReturnValue(limit);
    }
}
