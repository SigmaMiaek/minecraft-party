package pl.minecraftparty;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import pl.minecraftparty.ui.PartyScreen;
import pl.minecraftparty.ui.PartyTitlePanel;
import pl.minecraftparty.ui.PartyPausePanel;
import pl.minecraftparty.ui.PartyMenuButton;

public final class PartyClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("MinecraftParty");
    public static PartyManager manager;
    @Override public void onInitializeClient() {
        manager = new PartyManager(Minecraft.getInstance());
        PartyMenuShortcut.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> manager.tick());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> manager.shutdown());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> manager.playJoined());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> manager.playDisconnected());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> manager.serverStopping(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> Minecraft.getInstance().execute(() -> manager.worlds.serverStopped(server)));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> manager.serverPlayer(handler.player.getUUID(), true));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> manager.serverPlayer(handler.player.getUUID(), false));
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof TitleScreen title) {
                PartyMenuButton.install(client, title);
                PartyTitlePanel.install(client, screen, manager);
            } else if (screen instanceof PauseScreen pause) {
                PartyPausePanel.install(client, pause, manager);
                Screens.getWidgets(screen).add(Button.builder(Component.literal("Party"),
                        button -> client.gui.setScreen(new PartyScreen(screen)))
                        .bounds(width - 76, 6, 70, 20).build());
            }
        });
    }
}
