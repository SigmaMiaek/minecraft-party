package pl.minecraftparty;

import net.minecraft.client.gui.screens.*;
import org.junit.jupiter.api.Test;
import pl.minecraftparty.ui.NetworkDiagnosticsScreen;
import pl.minecraftparty.ui.PartyScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import static org.junit.jupiter.api.Assertions.*;

/** Class-only checks avoid starting Minecraft or creating a network connection. */
class PartyAutoJoinScreenTest {
    private static final class ForeignMainMenu extends Screen {
        private ForeignMainMenu() { super(Component.empty()); }
    }
    @Test void ordinaryAndThirdPartyMenusDoNotBlockANewHostWorld() {
        for (Class<? extends Screen> menu : java.util.List.of(TitleScreen.class, PartyScreen.class,
                NetworkDiagnosticsScreen.class, DisconnectedScreen.class, OptionsScreen.class, ForeignMainMenu.class))
            assertTrue(ClientSessionManager.canAutoJoinFrom(menu), menu.getSimpleName());
    }
    @Test void anotherConnectionLoadingPauseOrWorldSelectionMustNotBeReplacedByAutoJoin() {
        for (Class<? extends Screen> screen : java.util.List.of(ConnectScreen.class, PauseScreen.class, ProgressScreen.class,
                LevelLoadingScreen.class, CreateWorldScreen.class, SelectWorldScreen.class))
            assertFalse(ClientSessionManager.canAutoJoinFrom(screen), screen.getSimpleName());
        assertFalse(ClientSessionManager.canAutoJoinFrom(null));
    }
}
