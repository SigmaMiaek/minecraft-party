package pl.minecraftparty.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.TeleportCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import pl.minecraftparty.PartyWorldPermissions;
import java.util.function.Predicate;

/** Changes only the two teleport command roots, preserving vanilla predicates elsewhere. */
@Mixin(TeleportCommand.class)
public abstract class PartyTeleportMixin {
    @ModifyArg(method = "register", at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;requires(Ljava/util/function/Predicate;)Lcom/mojang/brigadier/builder/ArgumentBuilder;"), index = 0, require = 2)
    private static Predicate<CommandSourceStack> party$allowTeleport(Predicate<CommandSourceStack> vanilla) {
        return source -> vanilla.test(source) || PartyWorldPermissions.canTeleport(source);
    }
}
