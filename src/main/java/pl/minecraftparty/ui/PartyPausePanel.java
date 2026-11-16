package pl.minecraftparty.ui;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.PauseScreen;
import pl.minecraftparty.PartyManager;
import java.util.List;
import static pl.minecraftparty.ui.PartyVisuals.*;

/** Pause menu contains the roster only. The chat remains in Party and the title menu. */
public final class PartyPausePanel extends AbstractWidget {
    private final PartyManager manager;
    private final Font font;
    private Button previous, next;
    private int page;

    private PartyPausePanel(PartyManager manager, Font font, int x, int y, int width, int height) {
        super(x, y, width, height, tr("players.label"));
        this.manager = manager; this.font = font; active = false;
    }
    public static void install(Minecraft client, PauseScreen screen, PartyManager manager) {
        if (!manager.active() || screen.width < 400 || screen.height < 210) return;
        var widgets = Screens.getWidgets(screen);
        // Exclude PauseScreen(false), used by overlays without the normal pause menu.
        if (widgets.stream().noneMatch(widget -> widget.getWidth() >= 190 && widget.getY() > 50)) return;
        int width = Math.min(176, Math.max(124, screen.width / 3));
        int x = screen.width - width - 12, y = 56, height = Math.min(262, screen.height - y - 26);
        int delta = (x - 16) / 2 - screen.width / 2;
        // Shift vanilla menu, its title and centered mod buttons together, keeping the right column clear.
        for (var widget : List.copyOf(widgets)) {
            if (widget.getY() >= 35 && widget.getX() >= screen.width / 2 - 130
                    && widget.getRight() <= screen.width / 2 + 130) widget.setX(widget.getX() + delta);
        }
        PartyPausePanel panel = new PartyPausePanel(manager, client.font, x, y, width, height); widgets.add(panel);
        panel.previous = Button.builder(tr("previous"), b -> panel.page--).bounds(x + 7, y + height - 25, 38, 20).build();
        panel.next = Button.builder(tr("next"), b -> panel.page++).bounds(x + width - 45, y + height - 25, 38, 20).build();
        widgets.add(panel.previous); widgets.add(panel.next);
    }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        var state = manager.state();
        boolean shown = manager.active(); previous.visible = next.visible = shown;
        if (!shown) return;
        panel(graphics, getX(), getY(), width, height);
        text(graphics, font, tr("players", state == null ? 0 : state.members().size(), state == null ? "?" : state.limit()),
                getX() + 7, getY() + 8, 0xFFFFFFFF, width - 14);
        int rows = Math.max(1, (height - 48) / 26), maxPage = state == null ? 0 : Math.max(0, (state.members().size() - 1) / rows);
        page = Math.clamp(page, 0, maxPage);
        if (state != null) for (int i = 0; i < rows; i++) {
            int index = page * rows + i; if (index >= state.members().size()) break;
            member(graphics, font, state.members().get(index), getX() + 7, getY() + 28 + i * 26, width - 14, true);
        }
        previous.visible = next.visible = maxPage > 0;
        previous.active = page > 0; next.active = page < maxPage;
        if (maxPage > 0) text(graphics, font, net.minecraft.network.chat.Component.literal((page + 1) + " / " + (maxPage + 1)),
                getX() + 52, getY() + height - 18, 0xFFAAAAAA, width - 104);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput narration) { }
}
