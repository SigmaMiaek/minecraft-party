package pl.minecraftparty.mixin;

import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.NameAndId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.minecraftparty.PartyWorldPermissions;

@Mixin(PlayerList.class)
public abstract class PartyOperatorListMixin {
    @Inject(method = "isOp", at = @At("RETURN"), cancellable = true)
    private void party$sessionOperator(NameAndId profile, CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValue() && PartyWorldPermissions.isOperator(((PlayerList) (Object) this).getServer(), profile.id()))
            result.setReturnValue(true);
    }
}
