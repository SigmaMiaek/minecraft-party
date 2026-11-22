package pl.minecraftparty.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pl.minecraftparty.*;
import java.util.*;
import static pl.minecraftparty.ui.PartyVisuals.*;

/** Code visibility stays local to this screen and resets when the code changes. */
public final class PartyCodesScreen extends Screen {
    private final Screen parent;
    private final PartyManager manager = PartyClient.manager;
    private final PartyCodePresentation presentation = new PartyCodePresentation();
    private final List<EditBox> digits = new ArrayList<>();
    private PartyCodeIconButton eye, copy;
    private int left, span;
    public PartyCodesScreen(Screen parent) { super(tr("codes.title")); this.parent = parent; }
    @Override protected void init() {
        span = Math.min(510, width - 24); left = (width - span) / 2; digits.clear();
        int x = (width - PartyCodeInput.WIDTH - 52) / 2;
        for (int i = 0; i < 6; i++) {
            EditBox digit = addRenderableWidget(new EditBox(font, x + i * (PartyCodeInput.BOX + PartyCodeInput.GAP)
                    + (i >= 3 ? PartyCodeInput.GROUP_GAP : 0), 60, PartyCodeInput.BOX, PartyCodeInput.BOX, tr("code.digit", i + 1)));
            digit.setCentered(true); digit.setEditable(false); digit.active = false; digits.add(digit);
        }
        eye = addRenderableWidget(new PartyCodeIconButton(x + PartyCodeInput.WIDTH + 5, 60, 26, false,
                presentation::visible, presentation::toggle));
        copy = addRenderableWidget(new PartyCodeIconButton(x + PartyCodeInput.WIDTH + 30, 60, 26, true,
                presentation::visible, () -> { minecraft.keyboardHandler.setClipboard(manager.codes.code()); manager.message("copied"); }));
        addRenderableWidget(Button.builder(tr("rotate"), b -> manager.rotateInvite()).bounds(left, 100, span, 20).build())
                .active = manager.host() != null && !manager.leadership.busy();
        addRenderableWidget(Button.builder(tr("back"), b -> onClose()).bounds(left, height - 28, span, 20).build());
        updateCode();
    }
    private void updateCode() {
        String code = manager.codes.code(); presentation.sync(code);
        for (int i = 0; i < digits.size(); i++) digits.get(i).setValue(code.length() == 6 ? presentation.visible() ? code.substring(i, i + 1) : "•" : "");
        eye.active = code.length() == 6; eye.refreshLabel(); copy.active = code.length() == 6 && !manager.leadership.busy();
    }
    @Override public void tick() { updateCode(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        background(g, width, height); centered(g, font, title, width, 12, 0xFFFFFFFF);
        centered(g, font, tr("code.label"), width, 40, 0xFFAAAAAA);
        int start = (width - PartyCodeInput.WIDTH - 52) / 2;
        text(g, font, Component.literal("−"), start + PartyCodeInput.WIDTH / 2 - 3, 69, 0xFFAAAAAA, 10);
        int row = 135;
        for (var line : font.split(tr("codes.auto"), span)) { g.text(font, line, left, row, 0xFFAAAAAA, true); row += font.lineHeight; }
        text(g, font, tr("message." + manager.message(), manager.codes.check()), left, height - 58, 0xFFFFFFFF, span);
        text(g, font, tr("codes.trust"), left, height - 42, 0xFFAAAAAA, span);
        super.extractRenderState(g, x, y, delta);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
