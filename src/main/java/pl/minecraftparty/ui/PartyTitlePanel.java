package pl.minecraftparty.ui;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import pl.minecraftparty.PartyManager;
import static pl.minecraftparty.ui.PartyVisuals.*;

/** Rendered behind its controls; never overlaps the central vanilla menu buttons. */
public final class PartyTitlePanel extends AbstractWidget {
    private final PartyManager manager;
    private final Font font;
    private final int playersHeight;
    private PartyChat chat;
    private static String draft = "";

    private PartyTitlePanel(PartyManager manager, Font font, int x, int y, int width, int height) {
        super(x, y, width, height, tr("title"));
        this.manager = manager; this.font = font; this.playersHeight = Math.min(154, Math.max(72, height - 110));
        active = false; // Background widget does not intercept clicks or keyboard focus.
    }
    public static void install(Minecraft client, Screen screen, PartyManager manager) {
        int available = screen.width - (screen.width / 2 + 140) - 10;
        int width = Math.min(196, available), x = screen.width - width - 10, y = 62;
        int height = Math.min(320, screen.height - y - 35);
        if (!manager.active() || width < 152 || height < 190) return;
        PartyTitlePanel panel = new PartyTitlePanel(manager, client.font, x, y, width, height);
        var widgets = Screens.getWidgets(screen); widgets.add(panel);
        panel.chat = new PartyChat(client, panel.font, manager, widgets::add, x, y + panel.playersHeight + 4,
                width, height - panel.playersHeight - 4, draft, text -> draft = text, screen::setFocused);
    }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (manager.active()) {
            panel(graphics, getX(), getY(), width, playersHeight);
            var state = manager.state();
            text(graphics, font, tr("players", state == null ? 0 : state.members().size(), state == null ? "?" : state.limit()),
                    getX() + 7, getY() + 8, 0xFFFFFFFF, width - 14);
            if (state != null) {
                int count = Math.min(state.members().size(), Math.max(1, (playersHeight - 26) / 16));
                if (count < state.members().size()) count = Math.max(1, (playersHeight - 38) / 16);
                for (int i = 0; i < count; i++) member(graphics, font, state.members().get(i), getX() + 7, getY() + 26 + i * 16, width - 14, false);
                if (count < state.members().size()) text(graphics, font, tr("players.more", state.members().size() - count),
                        getX() + 7, getY() + playersHeight - 12, 0xFFAAAAAA, width - 14);
            }
        }
        if (chat != null) chat.draw(graphics);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput narration) { }
}
