package pl.minecraftparty.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.minecraftparty.PartyWorldPermissions;

@Mixin(MinecraftServer.class)
public abstract class PartyOperatorPermissionsMixin {
    @Inject(method = "getProfilePermissions", at = @At("RETURN"), cancellable = true)
    private void party$operatorLevel(NameAndId profile, CallbackInfoReturnable<LevelBasedPermissionSet> result) {
        if (PartyWorldPermissions.isOperator((MinecraftServer) (Object) this, profile.id())
                && !result.getReturnValue().level().isEqualOrHigherThan(PermissionLevel.GAMEMASTERS))
            result.setReturnValue(LevelBasedPermissionSet.GAMEMASTER);
    }
}
