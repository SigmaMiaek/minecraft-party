package pl.minecraftparty.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.resources.Identifier;
import java.util.function.BooleanSupplier;
import static pl.minecraftparty.ui.PartyVisuals.*;

/** Native button background with tiny pixel eye/copy icons; labels remain available to narration. */
public final class PartyCodeIconButton extends ImageButton {
    private static final WidgetSprites SPRITES = new WidgetSprites(Identifier.withDefaultNamespace("widget/button"),
            Identifier.withDefaultNamespace("widget/button_disabled"), Identifier.withDefaultNamespace("widget/button_highlighted"));
    private final boolean copy;
    private final BooleanSupplier revealed;
    public PartyCodeIconButton(int x, int y, int height, boolean copy, BooleanSupplier revealed, Runnable action) {
        super(x, y, 22, height, SPRITES, b -> action.run(), tr(copy ? "copy" : "codes.show"));
        this.copy = copy; this.revealed = revealed; refreshLabel();
    }
    public void refreshLabel() {
        var label = tr(copy ? "copy" : revealed.getAsBoolean() ? "codes.hide" : "codes.show");
        setMessage(label); setTooltip(Tooltip.create(label));
    }
    @Override public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractContents(g, mouseX, mouseY, delta);
        int x = getX() + 4, y = getY() + (getHeight() - 8) / 2, color = active ? 0xFFFFFFFF : 0xFF888888;
        if (copy) { outline(g, x + 4, y - 2, 8, 9, color); outline(g, x + 1, y + 1, 8, 9, color); }
        else {
            g.fill(x + 3, y, x + 11, y + 1, color); g.fill(x + 3, y + 7, x + 11, y + 8, color);
            g.fill(x + 1, y + 1, x + 3, y + 3, color); g.fill(x, y + 3, x + 1, y + 5, color);
            g.fill(x + 1, y + 5, x + 3, y + 7, color); g.fill(x + 11, y + 1, x + 13, y + 3, color);
            g.fill(x + 13, y + 3, x + 14, y + 5, color); g.fill(x + 11, y + 5, x + 13, y + 7, color);
            g.fill(x + 6, y + 2, x + 8, y + 6, color);
            if (!revealed.getAsBoolean()) for (int i = 0; i < 12; i++) g.fill(x + i + 1, y - 2 + i, x + i + 2, y + i, color);
        }
    }
    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color); g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color); g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
