package pl.minecraftparty.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import pl.minecraftparty.PartyClient;
import java.util.List;
import static pl.minecraftparty.ui.PartyVisuals.*;

/** Explicit diagnostics contain ports and stage counters, never IPs, invitation secrets or account IDs. */
public final class NetworkDiagnosticsScreen extends Screen {
    private final Screen parent;
    private String report;
    private int page;
    public NetworkDiagnosticsScreen(Screen parent) { super(tr("diagnostics.title")); this.parent = parent; }
    @Override protected void init() {
        report = PartyClient.manager.diagnostics();
        int left = Math.max(10, (width - 500) / 2), contentWidth = width - left * 2;
        int third = (contentWidth - 12) / 4;
        addRenderableWidget(Button.builder(tr("diagnostics.copy"), b -> minecraft.keyboardHandler.setClipboard(report))
                .bounds(left, height - 30, third, 20).build());
        addRenderableWidget(Button.builder(tr("diagnostics.refresh"), b -> { report = PartyClient.manager.diagnostics(); page = 0; })
                .bounds(left + third + 4, height - 30, third, 20).build());
        addRenderableWidget(Button.builder(tr("codes.open"), b -> minecraft.gui.setScreen(new PartyCodesScreen(this)))
                .bounds(left + (third + 4) * 2, height - 30, third, 20).build());
        addRenderableWidget(Button.builder(tr("back"), b -> onClose())
                .bounds(left + (third + 4) * 3, height - 30, contentWidth - (third + 4) * 3, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↑"), b -> page = Math.max(0, page - 1))
                .bounds(width - 26, 40, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("↓"), b -> page++)
                .bounds(width - 26, 62, 18, 18).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        background(graphics, width, height); centered(graphics, font, title, width, 12, 0xFFFFFFFF);
        int left = Math.max(10, (width - 500) / 2), contentWidth = width - left * 2 - 28;
        List<FormattedCharSequence> lines = font.split(Component.literal(report), contentWidth);
        int rows = Math.max(1, (height - 94) / font.lineHeight);
        page = Math.clamp(page, 0, Math.max(0, (lines.size() - 1) / rows));
        for (int i = 0; i < rows; i++) {
            int index = page * rows + i; if (index >= lines.size()) break;
            graphics.text(font, lines.get(index), left, 38 + i * font.lineHeight, 0xFFFFFFFF, true);
        }
        text(graphics, font, tr("diagnostics.privacy"), left, height - 48, 0xFFAAAAAA, width - left * 2);
        super.extractRenderState(graphics, x, y, delta);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
