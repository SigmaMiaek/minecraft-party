package pl.minecraftparty.ui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

import static pl.minecraftparty.ui.PartyVisuals.tr;

/** Vanilla square button, appended to the title menu's existing row of icons. */
public final class PartyMenuButton extends ImageButton {
    private static final int SIZE = 20, GAP = 4;
    private static final WidgetSprites SPRITES = new WidgetSprites(
            Identifier.withDefaultNamespace("widget/button"),
            Identifier.withDefaultNamespace("widget/button_disabled"),
            Identifier.withDefaultNamespace("widget/button_highlighted"));

    private PartyMenuButton(Minecraft client, TitleScreen parent) {
        super(0, 0, SIZE, SIZE, SPRITES,
                button -> client.gui.setScreen(new PartyScreen(parent)), tr("menu.open"));
        setTooltip(Tooltip.create(tr("menu.open")));
    }

    public static void install(Minecraft client, TitleScreen screen) {
        var widgets = Screens.getWidgets(screen);
        if (widgets.stream().anyMatch(PartyMenuButton.class::isInstance)) return;
        var button = new PartyMenuButton(client, screen);
        widgets.add(button);
        button.place(screen);
        // Other mods can add their icon later in AFTER_INIT. Resolve the completed
        // row before extraction, including after a resize or a GUI scale change.
        ScreenEvents.beforeExtract(screen).register((current, graphics, mouseX, mouseY, delta) -> button.place(screen));
    }

    private void place(TitleScreen screen) {
        var widgets = Screens.getWidgets(screen);
        AbstractWidget anchor = null, options = null;
        for (var widget : widgets) {
            if (!widget.visible || widget == this) continue;
            if (widget.getMessage().getContents() instanceof TranslatableContents text) {
                if (text.getKey().equals("options.language") || text.getKey().equals("options.accessibility")) anchor = widget;
                if (text.getKey().equals("menu.options")) options = widget;
            }
        }
        int rowY = anchor != null ? anchor.getY() : options != null ? options.getY() - SIZE - GAP : screen.height / 4 + 120;
        int left = anchor != null ? anchor.getX() : screen.width / 2 - 34;
        int right = anchor != null ? anchor.getRight() : screen.width / 2 + 34;
        // Follow adjacent squares, including Mod Menu, without grabbing unrelated
        // buttons elsewhere on the same horizontal line.
        boolean expanded;
        do {
            expanded = false;
            for (var widget : widgets) {
                if (widget == this || !widget.visible || widget.getY() != rowY
                        || widget.getWidth() != widget.getHeight() || widget.getHeight() < 16 || widget.getHeight() > 24
                        || widget.getX() > right + GAP || widget.getRight() < left - GAP) continue;
                int nextLeft = Math.min(left, widget.getX()), nextRight = Math.max(right, widget.getRight());
                if (nextLeft != left || nextRight != right) { left = nextLeft; right = nextRight; expanded = true; }
            }
        } while (expanded);

        int x = right + GAP;
        if (!fits(screen, x, rowY)) x = left - SIZE - GAP;
        visible = fits(screen, x, rowY);
        if (visible) setPosition(x, rowY);
    }

    private boolean fits(TitleScreen screen, int x, int y) {
        if (x < GAP || x + SIZE > screen.width - GAP || y < GAP || y + SIZE > screen.height - GAP) return false;
        for (var widget : Screens.getWidgets(screen)) {
            if (widget != this && widget.visible && (widget.active || widget instanceof AbstractButton)
                    && x < widget.getRight() && x + SIZE > widget.getX()
                    && y < widget.getBottom() && y + SIZE > widget.getY()) return false;
        }
        return true;
    }

    @Override public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractContents(graphics, mouseX, mouseY, delta);
        int x = getX() + 2, y = getY() + 2;
        // Three block-shaped players: pale teammates behind an emerald leader.
        int outline = tint(0x292724), shadow = tint(0xA4A29C), face = tint(0xF0ECE2);
        int shirt = tint(isHoveredOrFocused() ? 0x75E5AB : 0x4FC18B), shirtShadow = tint(0x27875B);
        graphics.fill(x, y + 4, x + 5, y + 9, outline);
        graphics.fill(x + 1, y + 5, x + 4, y + 8, shadow);
        graphics.fill(x, y + 10, x + 6, y + 15, outline);
        graphics.fill(x + 1, y + 11, x + 5, y + 14, shadow);
        graphics.fill(x + 11, y + 4, x + 16, y + 9, outline);
        graphics.fill(x + 12, y + 5, x + 15, y + 8, shadow);
        graphics.fill(x + 10, y + 10, x + 16, y + 15, outline);
        graphics.fill(x + 11, y + 11, x + 15, y + 14, shadow);
        graphics.fill(x + 5, y + 1, x + 11, y + 8, outline);
        graphics.fill(x + 6, y + 2, x + 10, y + 7, face);
        graphics.fill(x + 6, y + 4, x + 7, y + 5, outline);
        graphics.fill(x + 9, y + 4, x + 10, y + 5, outline);
        graphics.fill(x + 4, y + 9, x + 12, y + 15, outline);
        graphics.fill(x + 5, y + 10, x + 11, y + 14, shirt);
        graphics.fill(x + 5, y + 13, x + 11, y + 14, shirtShadow);
    }

    private int tint(int rgb) { return (Math.clamp(Math.round(getAlpha() * 255), 0, 255) << 24) | rgb; }
}
