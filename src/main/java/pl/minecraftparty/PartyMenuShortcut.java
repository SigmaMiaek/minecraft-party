package pl.minecraftparty;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import pl.minecraftparty.ui.NetworkDiagnosticsScreen;
import pl.minecraftparty.ui.PartyCodesScreen;
import pl.minecraftparty.ui.PartyScreen;
import pl.minecraftparty.ui.WatchPartyScreen;

import static org.lwjgl.glfw.GLFW.*;

/** Menu input is handled by Fabric's screen hook, not the in-world key click queue. */
public final class PartyMenuShortcut {
    private PartyMenuShortcut() { }

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("minecraftparty", "party"));
        KeyMapping openParty = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.minecraftparty.open_party", InputConstants.Type.KEYSYM, GLFW_KEY_P, category));
        // Install on every screen: Lunar/Dawn may replace TitleScreen with an unrelated subclass.
        ScreenEvents.BEFORE_INIT.register((client, screen, width, height) ->
                ScreenKeyboardEvents.allowKeyPress(screen).register((current, event) -> {
                    if (openParty.isUnbound() || !openParty.matches(event) || !allowsModifiers(event)
                            || client.gui.screen() != current || client.gui.overlay() != null
                            || client.level != null || client.getSingleplayerServer() != null || client.getConnection() != null
                            || !allowsScreen(current.getClass()) || textInputFocused(current.getFocused())) return true;
                    client.gui.setScreen(new PartyScreen(current));
                    return false; // Consume this key before the replaced menu handles it as well.
                }));
    }

    static boolean allowsScreen(Class<? extends Screen> type) {
        if (!ClientSessionManager.canAutoJoinFrom(type) || OptionsScreen.class.isAssignableFrom(type)
                || OptionsSubScreen.class.isAssignableFrom(type) || PartyScreen.class.isAssignableFrom(type)
                || PartyCodesScreen.class.isAssignableFrom(type) || NetworkDiagnosticsScreen.class.isAssignableFrom(type)
                || WatchPartyScreen.class.isAssignableFrom(type)) return false;
        // Do not interrupt vanilla account, confirmation, server-list or resource-pack screens.
        return TitleScreen.class.isAssignableFrom(type) || !type.getName().startsWith("net.minecraft.");
    }

    static boolean allowsModifiers(KeyEvent event) {
        // Leave Ctrl/Alt/Super shortcuts to the menu, while permitting a modifier itself to be rebound.
        return (event.modifiers() & (GLFW_MOD_CONTROL | GLFW_MOD_ALT | GLFW_MOD_SUPER)) == 0
                || event.key() >= GLFW_KEY_LEFT_SHIFT && event.key() <= GLFW_KEY_RIGHT_SUPER;
    }

    private static boolean textInputFocused(GuiEventListener focused) {
        for (int depth = 0; focused != null && depth < 32; depth++) {
            if (focused instanceof EditBox || focused instanceof MultiLineEditBox) return true;
            if (!(focused instanceof ContainerEventHandler container)) return false;
            focused = container.getFocused();
        }
        return focused != null; // A malformed/cyclic focus tree must not take over keyboard input.
    }
}
