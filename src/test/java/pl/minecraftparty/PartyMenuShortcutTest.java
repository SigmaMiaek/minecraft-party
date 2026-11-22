package pl.minecraftparty;

import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import pl.minecraftparty.ui.PartyScreen;
import pl.minecraftparty.ui.PartyCodesScreen;
import pl.minecraftparty.ui.NetworkDiagnosticsScreen;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Checks menu classification without starting a renderer or pretending Lunar/Dawn is installed. */
final class PartyMenuShortcutTest {
    private static final class ReplacedMainMenu extends Screen {
        private ReplacedMainMenu() { super(Component.empty()); }
    }
    private static final class ReplacedTitleScreen extends TitleScreen { }
    private static final class ReplacedControls extends KeyBindsScreen {
        private ReplacedControls() { super(null, null); }
    }
    @Test void vanillaAndUnrelatedClientMainMenusCanOpenParty() {
        assertTrue(PartyMenuShortcut.allowsScreen(TitleScreen.class));
        assertTrue(PartyMenuShortcut.allowsScreen(ReplacedTitleScreen.class));
        assertTrue(PartyMenuShortcut.allowsScreen(ReplacedMainMenu.class));
    }
    @Test void settingsConnectionsLoadingAndExistingPartyScreensKeepTheirKeys() {
        for (Class<? extends Screen> type : java.util.List.of(OptionsScreen.class, KeyBindsScreen.class,
                ReplacedControls.class, ConnectScreen.class, ProgressScreen.class, LevelLoadingScreen.class,
                PauseScreen.class, SelectWorldScreen.class, JoinMultiplayerScreen.class,
                ConfirmScreen.class, PartyScreen.class, PartyCodesScreen.class, NetworkDiagnosticsScreen.class))
            assertFalse(PartyMenuShortcut.allowsScreen(type), type.getSimpleName());
        assertFalse(PartyMenuShortcut.allowsScreen(null));
    }
    @Test void modifiedMenuCommandsAreNotTakenOver() {
        assertTrue(PartyMenuShortcut.allowsModifiers(new KeyEvent(GLFW_KEY_P, 0, 0)));
        assertTrue(PartyMenuShortcut.allowsModifiers(new KeyEvent(GLFW_KEY_P, 0, GLFW_MOD_SHIFT)));
        for (int modifiers : new int[]{GLFW_MOD_CONTROL, GLFW_MOD_ALT, GLFW_MOD_SUPER, GLFW_MOD_CONTROL | GLFW_MOD_SHIFT})
            assertFalse(PartyMenuShortcut.allowsModifiers(new KeyEvent(GLFW_KEY_P, 0, modifiers)));
        assertTrue(PartyMenuShortcut.allowsModifiers(new KeyEvent(GLFW_KEY_LEFT_CONTROL, 0, GLFW_MOD_CONTROL)));
    }
}
