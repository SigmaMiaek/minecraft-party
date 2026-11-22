package pl.minecraftparty.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pl.minecraftparty.*;
import pl.minecraftparty.network.ShortCodeClient;
import java.util.*;
import static pl.minecraftparty.ui.PartyVisuals.*;

/** Directory settings and explicit host approvals. Names here are not authenticated Minecraft identities. */
public final class PartyCodesScreen extends Screen {
    private final Screen parent;
    private final PartyManager manager = PartyClient.manager;
    private String draft, rowsKey = "";
    private EditBox address;
    private int page, left, span, rows;
    private final List<Button> decisions = new ArrayList<>();
    public PartyCodesScreen(Screen parent) { super(tr("codes.title")); this.parent = parent; draft = manager.config.codeServiceUrl; }
    @Override protected void init() {
        span = Math.min(510, width - 24); left = (width - span) / 2;
        rows = Math.max(1, (height - 204) / 25); decisions.clear();
        address = addRenderableWidget(new EditBox(font, left, 45, span, 20, tr("codes.url")));
        address.setMaxLength(180); address.setValue(draft); address.setResponder(value -> draft = value);
        address.setEditable(!manager.active());
        Button save = addRenderableWidget(Button.builder(tr("codes.save"), b -> save()).bounds(left, 70, span / 2 - 2, 20).build());
        save.active = !manager.active();
        addRenderableWidget(Button.builder(tr("codes.long"), b -> minecraft.keyboardHandler.setClipboard(manager.invite()))
                .bounds(left + span / 2 + 2, 70, span - span / 2 - 2, 20).build()).active = !manager.invite().isEmpty();
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
    private void save() {
        try {
            manager.config.codeServiceUrl = draft.isBlank() ? "" : ShortCodeClient.endpoint(draft).toString();
            draft = manager.config.codeServiceUrl; manager.background(manager.config::save); manager.message("codes_saved");
        } catch (IllegalArgumentException e) { manager.message("codes_bad_url"); }
    }
    private String key() { return manager.codes.pending().toString() + ":" + manager.active(); }
    @Override public void tick() {
        if (!rowsKey.equals(key())) rebuildWidgets();
        decisions.forEach(b -> b.active = !manager.codes.working());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        background(g, width, height); centered(g, font, title, width, 12, 0xFFFFFFFF);
        text(g, font, tr("codes.url"), left, 32, 0xFFAAAAAA, span);
        text(g, font, tr("codes.current", manager.codes.code().isEmpty() ? "—" : manager.codes.code()), left, 99, 0xFFFFFF55, span);
        text(g, font, tr("codes.verify"), left, 115, 0xFFAAAAAA, span);
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
