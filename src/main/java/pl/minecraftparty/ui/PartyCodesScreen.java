package pl.minecraftparty.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pl.minecraftparty.*;
import java.util.*;
import static pl.minecraftparty.ui.PartyVisuals.*;

/** Party codes and explicit host approvals. Names here are not authenticated Minecraft identities. */
public final class PartyCodesScreen extends Screen {
    private final Screen parent;
    private final PartyManager manager = PartyClient.manager;
    private String rowsKey = "";
    private int page, left, span, rows;
    private final List<Button> decisions = new ArrayList<>();
    public PartyCodesScreen(Screen parent) { super(tr("codes.title")); this.parent = parent; }
    @Override protected void init() {
        span = Math.min(510, width - 24); left = (width - span) / 2;
        rows = Math.max(1, (height - 204) / 25); decisions.clear();
        String code = manager.codes.code();
        for (int i = 0; i < 6; i++) {
            EditBox digit = addRenderableWidget(new EditBox(font, (width - PartyCodeInput.WIDTH) / 2
                    + i * (PartyCodeInput.BOX + PartyCodeInput.GAP) + (i >= 3 ? PartyCodeInput.GROUP_GAP : 0),
                    52, PartyCodeInput.BOX, PartyCodeInput.BOX, tr("code.digit", i + 1)));
            digit.setCentered(true); digit.setEditable(false); digit.active = false;
            digit.setValue(code.length() == 6 ? code.substring(i, i + 1) : "");
        }
        addRenderableWidget(Button.builder(tr("copy"), b -> {
            minecraft.keyboardHandler.setClipboard(manager.codes.code()); manager.message("copied");
        }).bounds(left, 89, span / 2 - 2, 20).build()).active = code.length() == 6;
        addRenderableWidget(Button.builder(tr("rotate"), b -> manager.rotateInvite())
                .bounds(left + span / 2 + 2, 89, span - span / 2 - 2, 20).build()).active = manager.host() != null;
        var pending = manager.codes.pending();
        int pages = Math.max(1, (pending.size() + rows - 1) / rows); page = Math.clamp(page, 0, pages - 1);
        for (int i = 0; i < rows && page * rows + i < pending.size(); i++) {
            var request = pending.get(page * rows + i); int y = 132 + i * 25;
            decisions.add(addRenderableWidget(Button.builder(tr("codes.accept"), b -> manager.codes.decide(request.id(), true))
                    .bounds(left + span - 110, y, 58, 20).build()));
            decisions.add(addRenderableWidget(Button.builder(tr("codes.deny"), b -> manager.codes.decide(request.id(), false))
                    .bounds(left + span - 48, y, 48, 20).build()));
        }
        addRenderableWidget(Button.builder(Component.literal("←"), b -> { page--; rebuildWidgets(); }).bounds(left, height - 61, 30, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal("→"), b -> { page++; rebuildWidgets(); }).bounds(left + 34, height - 61, 30, 20).build()).active = page + 1 < pages;
        addRenderableWidget(Button.builder(tr("back"), b -> onClose()).bounds(left, height - 28, span, 20).build());
        rowsKey = key();
    }
    private String key() { return manager.codes.pending().toString() + ":" + manager.active() + ":" + manager.codes.code(); }
    @Override public void tick() {
        if (!rowsKey.equals(key())) rebuildWidgets();
        decisions.forEach(b -> b.active = !manager.codes.working());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        background(g, width, height); centered(g, font, title, width, 12, 0xFFFFFFFF);
        centered(g, font, tr("code.label"), width, 34, 0xFFAAAAAA);
        centered(g, font, Component.literal("−"), width, 61, 0xFFAAAAAA);
        text(g, font, tr("codes.verify"), left, 119, 0xFFAAAAAA, span);
        var pending = manager.codes.pending();
        for (int i = 0; i < rows && page * rows + i < pending.size(); i++) {
            var request = pending.get(page * rows + i);
            text(g, font, Component.literal(request.name() + " · " + request.check()), left, 138 + i * 25, 0xFFFFFFFF, span - 116);
        }
        text(g, font, tr("message." + manager.message(), manager.codes.check()), left + 70, height - 55, 0xFFFFFFFF, span - 70);
        text(g, font, tr("codes.trust"), left, height - 40, 0xFFAAAAAA, span);
        super.extractRenderState(g, x, y, delta);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
