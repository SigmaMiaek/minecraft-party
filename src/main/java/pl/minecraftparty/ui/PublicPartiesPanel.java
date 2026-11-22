package pl.minecraftparty.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pl.minecraftparty.PartyManager;
import pl.minecraftparty.network.PublicParty;
import pl.minecraftparty.network.ShortCodeClient;
import java.util.*;
import java.util.function.*;
import static pl.minecraftparty.ui.PartyVisuals.*;

/** Real directory browser embedded in the Party screen; one request can be in flight. */
public final class PublicPartiesPanel {
    private final Minecraft client;
    private final PartyManager manager;
    private final Screen owner;
    private final BooleanSupplier shown;
    private List<PublicParty> lobbies = List.of();
    private int page, pages = 1, pageSize, epoch, x, y, width, height;
    private long nextRefresh, manualRefreshAt;
    private boolean loading, inFlight, visible;
    private String status = "public_loading";
    private Button refresh, previous, next;
    private final List<Button> joinButtons = new ArrayList<>();

    public PublicPartiesPanel(Minecraft client, PartyManager manager, Screen owner, BooleanSupplier shown) {
        this.client = client; this.manager = manager; this.owner = owner; this.shown = shown;
    }
    public void layout(PartyLandingLayout.Rect bounds, Consumer<Button> add) {
        x = bounds.x(); y = bounds.y(); width = bounds.width(); height = bounds.height(); visible = true;
        int rows = Math.clamp((height - 72) / 36, 1, 12);
        if (pageSize != rows) {
            ++epoch; loading = inFlight; nextRefresh = manualRefreshAt = 0;
            pageSize = rows; page = 0; lobbies = List.of();
        }
        refresh = Button.builder(tr("public.refresh"), b -> refresh()).bounds(x + 8, y + height - 38, 68, 20).build(); add.accept(refresh);
        previous = Button.builder(tr("previous"), b -> { page--; refresh(); }).bounds(x + width - 56, y + height - 38, 22, 20).build(); add.accept(previous);
        next = Button.builder(tr("next"), b -> { page++; refresh(); }).bounds(x + width - 30, y + height - 38, 22, 20).build(); add.accept(next);
        joinButtons.clear();
        for (int i = 0; i < pageSize; i++) {
            int row = i;
            Button join = Button.builder(tr("join"), b -> {
                if (!manager.active() && !manager.busy() && !loading && row < lobbies.size() && !lobbies.get(row).full())
                    manager.join(lobbies.get(row).code());
            }).bounds(x + width - 86, y + 34 + i * 36, 78, 20).build();
            add.accept(join); joinButtons.add(join);
        }
        updateButtons();
    }
    public void suspend() {
        if (!visible) return;
        visible = false; ++epoch; loading = false; nextRefresh = 0;
    }
    private void refresh() {
        if (loading || inFlight || !shown.getAsBoolean() || System.nanoTime() < manualRefreshAt || manager.active()) return;
        int operation = ++epoch, requestedPage = page, size = pageSize;
        loading = inFlight = true; status = "public_loading";
        manualRefreshAt = System.nanoTime() + 3_000_000_000L; nextRefresh = System.nanoTime() + 15_000_000_000L;
        updateButtons(); String url = manager.config.codeServiceUrl;
        manager.background(() -> {
            try (var service = new ShortCodeClient(url)) {
                var result = service.list(requestedPage, size);
                client.execute(() -> {
                    inFlight = false;
                    if (epoch != operation || client.gui.screen() != owner || !shown.getAsBoolean()) { loading = false; return; }
                    loading = false; pages = result.pages();
                    if (page >= pages) { page = pages - 1; lobbies = List.of(); nextRefresh = manualRefreshAt = 0; }
                    else { lobbies = result.lobbies(); status = lobbies.isEmpty() ? "public_empty" : "public_loaded"; }
                    updateButtons();
                });
            } catch (Exception e) {
                client.execute(() -> {
                    inFlight = false;
                    if (epoch != operation || client.gui.screen() != owner || !shown.getAsBoolean()) { loading = false; return; }
                    loading = false; lobbies = List.of(); status = e instanceof ShortCodeClient.Failure f ? f.key() : "public_unavailable";
                    updateButtons();
                });
            }
        });
    }
    private void updateButtons() {
        if (refresh == null) return;
        boolean ready = !loading && !inFlight && !manager.active() && System.nanoTime() >= manualRefreshAt;
        refresh.active = ready; previous.active = ready && page > 0; next.active = ready && page + 1 < pages;
        for (int i = 0; i < joinButtons.size(); i++) {
            Button button = joinButtons.get(i); button.visible = i < lobbies.size();
            button.active = button.visible && !loading && !manager.busy() && !manager.active() && !lobbies.get(i).full();
            if (button.visible) button.setMessage(tr(lobbies.get(i).full() ? "public.full" : "join"));
        }
    }
    public void tick() { if (!visible) return; updateButtons(); if (!loading && System.nanoTime() >= nextRefresh) refresh(); }
    public void draw(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font) {
        panel(g, x, y, width, height);
        text(g, font, tr("public.title"), x + 8, y + 9, 0xFFFFFFFF, width - 16);
        for (int i = 0; i < lobbies.size(); i++) {
            PublicParty lobby = lobbies.get(i); int rowY = y + 28 + i * 36;
            text(g, font, Component.literal(lobby.name()), x + 8, rowY + 2, 0xFFFFFFFF, width - 102);
            text(g, font, tr("public.row", lobby.host(), lobby.players(), lobby.limit(), tr(lobby.inWorld() ? "public.in_world" : "public.in_lobby")),
                    x + 8, rowY + 17, 0xFFAAAAAA, width - 102);
        }
        if (lobbies.isEmpty()) text(g, font, tr("message." + status), x + 8, y + 33, 0xFFAAAAAA, width - 16);
        text(g, font, tr("public.page", page + 1, pages), x + 84, y + height - 32, 0xFFAAAAAA, width - 148);
        text(g, font, tr("message." + status), x + 8, y + height - 13, 0xFF999999, width - 16);
    }
}
