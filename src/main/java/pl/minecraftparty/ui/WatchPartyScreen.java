package pl.minecraftparty.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import pl.minecraftparty.PartyClient;
import pl.minecraftparty.PartyManager;
import pl.minecraftparty.core.WatchState;
import pl.minecraftparty.watch.RinkuPlayer;
import java.util.List;
import static pl.minecraftparty.ui.PartyVisuals.*;

/** Native controls around the same live browser surface that is drawn in the world. */
public final class WatchPartyScreen extends Screen {
    private final Screen parent;
    private final PartyManager party = PartyClient.manager;
    private String source = "", seekDraft = "0:00", validation = "";
    private int size = 6, x, w, previewX, previewY, previewW, previewH;
    private Button place, play, backTen, forwardTen, seek, stop, join;
    private boolean pressed;
    public WatchPartyScreen(Screen parent) { super(tr("watch.title")); this.parent = parent; }
    private Button button(String key, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(tr(key), b -> action.run()).bounds(x, y, width, 20).build());
    }
    @Override protected void init() {
        x = (width - Math.min(620, width - 24)) / 2; w = Math.min(620, width - 24);
        button("back", width - 76, 7, 68, this::onClose);
        if (!RinkuPlayer.installed()) button("watch.install", 8, 7, 90,
                () -> ConfirmLinkScreen.confirmLinkNow(this, "https://modrinth.com/mod/rinku/versions?g=26.2&l=fabric"));
        EditBox url = addRenderableWidget(new EditBox(font, x, 44, w - 110, 20, tr("watch.link")));
        url.setMaxLength(2048); url.setValue(source); url.setResponder(value -> source = value);
        place = button("watch.place", x + w - 104, 44, 104, () -> { validation = ""; party.watch.create(source, size); });
        int y = 70;
        play = button("watch.play", x, y, 58, () -> { WatchState s = party.watch.state(); if (s != null) party.watch.play(!s.playing()); });
        backTen = button("watch.back10", x + 62, y, 39, () -> shift(-10_000));
        forwardTen = button("watch.forward10", x + 105, y, 39, () -> shift(10_000));
        Button screenSize = button("watch.size", x + 148, y, 64, () -> { size = size == 12 ? 4 : size + 2; rebuildWidgets(); });
        screenSize.setMessage(tr("watch.size", size));
        stop = button("watch.stop", x + 216, y, Math.max(64, w - 216), party.watch::stop);
        EditBox time = addRenderableWidget(new EditBox(font, x, 96, 62, 20, tr("watch.time")));
        time.setMaxLength(10); time.setValue(seekDraft); time.setResponder(value -> seekDraft = value);
        seek = button("watch.seek", x + 66, 96, 78, () -> {
            try { party.watch.seek(parseTime(seekDraft)); validation = ""; }
            catch (IllegalArgumentException e) { validation = "bad_time"; }
        });
        join = button("watch.join", x + 148, 96, w - 148, () -> {
            validation = ""; if (party.watch.watching()) party.watch.leave(); else party.watch.join();
        });
        previewY = 125;
        previewH = Math.max(36, Math.min((w * 9) / 16, height - previewY - 82));
        previewW = previewH * 16 / 9; previewX = (width - previewW) / 2;
        int controlsY = height - 75;
        addRenderableWidget(new AbstractSliderButton(x, controlsY, w, 20, tr("watch.volume", party.watch.volume()), party.watch.volume() / 100.0) {
            @Override protected void updateMessage() { setMessage(tr("watch.volume", (int) Math.round(value * 100))); }
            @Override protected void applyValue() { party.watch.volume((int) Math.round(value * 100)); }
        });
        url.setTooltip(Tooltip.create(tr("watch.link_hint")));
        place.setTooltip(Tooltip.create(tr("watch.place_hint")));
        screenSize.setTooltip(Tooltip.create(tr("watch.size_hint")));
        join.setTooltip(Tooltip.create(tr("watch.consent")));
        update();
    }
    private void shift(long amount) { WatchState s = party.watch.state(); if (s != null) party.watch.seek(s.positionAt(System.nanoTime()) + amount); }
    public static long parseTime(String value) {
        if (value == null || !value.matches("[0-9]{1,6}(:[0-9]{1,2}){0,2}")) throw new IllegalArgumentException("time");
        String[] parts = value.split(":"); long seconds = 0;
        for (int i = 0; i < parts.length; i++) {
            int component = Integer.parseInt(parts[i]);
            if (i > 0 && component >= 60) throw new IllegalArgumentException("time");
            seconds = seconds * 60 + component;
        }
        if (seconds * 1000 > WatchState.MAX_POSITION_MS) throw new IllegalArgumentException("time");
        return seconds * 1000;
    }
    private void update() {
        WatchState s = party.watch.state(); boolean controls = party.host() != null && s != null && !party.busy();
        for (Button button : List.of(play, backTen, forwardTen, seek, stop)) button.active = controls;
        play.setMessage(tr(s != null && s.playing() ? "watch.pause" : "watch.play"));
        place.active = party.host() != null && minecraft.level != null && party.state() != null && party.state().worldId() != null && !party.busy() && !source.isBlank();
        join.setMessage(tr(party.watch.watching() ? "watch.leave" : "watch.join"));
        join.active = s != null && party.inPartyWorld() && !party.watch.opening() && RinkuPlayer.installed();
    }
    @Override public void tick() { update(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        background(g, width, height); centered(g, font, title, width, 12, 0xFFFFFFFF);
        text(g, font, tr("watch.link"), x, 31, 0xFFAAAAAA, w);
        g.fill(previewX - 1, previewY - 1, previewX + previewW + 1, previewY + previewH + 1, 0xFF555555);
        g.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0xFF000000);
        var texture = party.watch.texture();
        if (texture != null) g.blit(RenderPipelines.GUI_TEXTURED, texture, previewX, previewY, 0, 0, previewW, previewH, previewW, previewH);
        else text(g, font, tr("watch.preview_hint"), previewX + 8, previewY + 12, 0xFFAAAAAA, previewW - 16);
        String status = validation.isEmpty() ? party.watch.status() : validation;
        text(g, font, tr("watch.status." + status), x, height - 48, 0xFFFFFFFF, w);
        text(g, font, tr("watch.consent"), x, height - 32, 0xFFAAAAAA, w);
        text(g, font, tr("watch.controls_hint"), x, height - 19, 0xFFAAAAAA, w);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
    private boolean inPreview(double x, double y) { return x >= previewX && x < previewX + previewW && y >= previewY && y < previewY + previewH; }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (super.mouseClicked(event, twice)) return true;
        if (inPreview(event.x(), event.y()) && party.watch.watching()) {
            pressed = true; party.watch.click((event.x() - previewX) / previewW, (event.y() - previewY) / previewH, event.button()); return true;
        }
        return false;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (pressed) { pressed = false; party.watch.release((event.x() - previewX) / previewW, (event.y() - previewY) / previewH, event.button()); return true; }
        return super.mouseReleased(event);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
