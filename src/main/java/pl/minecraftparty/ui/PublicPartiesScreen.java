package pl.minecraftparty.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pl.minecraftparty.PartyClient;
import pl.minecraftparty.PartyManager;
import pl.minecraftparty.network.PublicParty;
import pl.minecraftparty.network.ShortCodeClient;
import java.util.*;
import static pl.minecraftparty.ui.PartyVisuals.*;

/** Paginated advertisements from the existing directory, never peer addresses or raw invites. */
public final class PublicPartiesScreen extends Screen {
    private final Screen parent;
    private final PartyManager manager = PartyClient.manager;
    private List<PublicParty> lobbies = List.of();
    private int page, pages = 1, pageSize, epoch, left, panelWidth, listTop, listBottom;
    private long nextRefresh, manualRefreshAt;
    private boolean loading, inFlight;
    private String status = "public_loading";
    private Button refresh, previous, next;
    private final List<Button> joinButtons = new ArrayList<>();

    public PublicPartiesScreen(Screen parent) { super(tr("public.title")); this.parent = parent; }
    @Override protected void init() {
        panelWidth = Math.min(620, width - 24); left = (width - panelWidth) / 2;
        listTop = 57; listBottom = height - 69;
        int rows = Math.clamp((listBottom - listTop) / 38, 1, 12);
        if (pageSize != rows) {
            ++epoch; loading = inFlight; nextRefresh = manualRefreshAt = 0;
            pageSize = rows; page = 0; lobbies = List.of();
        }
        addRenderableWidget(Button.builder(tr("back"), b -> onClose()).bounds(width - 76, 7, 68, 20).build());
        refresh = addRenderableWidget(Button.builder(tr("public.refresh"), b -> refresh())
                .bounds(left, height - 55, 100, 20).build());
        previous = addRenderableWidget(Button.builder(tr("previous"), b -> { page--; refresh(); })
                .bounds(left + panelWidth - 84, height - 55, 38, 20).build());
        next = addRenderableWidget(Button.builder(tr("next"), b -> { page++; refresh(); })
                .bounds(left + panelWidth - 38, height - 55, 38, 20).build());
        joinButtons.clear();
        for (int index = 0; index < lobbies.size(); index++) {
            PublicParty lobby = lobbies.get(index);
            Button join = addRenderableWidget(Button.builder(tr(lobby.full() ? "public.full" : "join"), b -> join(lobby))
                    .bounds(left + panelWidth - 92, listTop + index * 38 + 7, 84, 20).build());
            joinButtons.add(join);
        }
        updateButtons();
    }
    private void join(PublicParty lobby) {
        if (manager.active() || loading || lobby.full()) return;
        manager.join(lobby.code());
        minecraft.gui.setScreen(parent);
    }
    private void refresh() {
        if (loading || inFlight || System.nanoTime() < manualRefreshAt || manager.active()) return;
        int operation = ++epoch, requestedPage = page, size = pageSize;
        loading = inFlight = true; status = "public_loading";
        manualRefreshAt = System.nanoTime() + 3_000_000_000L;
        nextRefresh = System.nanoTime() + 15_000_000_000L;
        updateButtons(); String url = manager.config.codeServiceUrl;
        manager.background(() -> {
            try (var service = new ShortCodeClient(url)) {
                var result = service.list(requestedPage, size);
                minecraft.execute(() -> {
                    inFlight = false;
                    if (epoch != operation || minecraft.gui.screen() != this) { loading = false; return; }
                    loading = false; pages = result.pages();
                    if (page >= pages) { page = pages - 1; lobbies = List.of(); nextRefresh = 0; manualRefreshAt = 0; }
                    else { lobbies = result.lobbies(); status = lobbies.isEmpty() ? "public_empty" : "public_loaded"; }
                    rebuildWidgets();
                });
            } catch (Exception e) {
                minecraft.execute(() -> {
                    inFlight = false;
                    if (epoch != operation || minecraft.gui.screen() != this) { loading = false; return; }
                    loading = false; lobbies = List.of();
                    status = e instanceof ShortCodeClient.Failure f ? f.key() : "public_unavailable";
                    rebuildWidgets();
                });
            }
        });
    }
    private void updateButtons() {
        if (refresh == null) return;
        boolean ready = !loading && !manager.active() && System.nanoTime() >= manualRefreshAt;
        refresh.active = ready; previous.active = ready && page > 0; next.active = ready && page + 1 < pages;
        for (int i = 0; i < joinButtons.size(); i++) joinButtons.get(i).active = !loading && !manager.active() && !lobbies.get(i).full();
    }
    @Override public void tick() { updateButtons(); if (!loading && System.nanoTime() >= nextRefresh) refresh(); }
    @Override public void removed() { ++epoch; loading = false; nextRefresh = 0; super.removed(); }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        background(g, width, height);
        centered(g, font, title, width, 12, 0xFFFFFFFF);
        text(g, font, tr("public.description"), left, 35, 0xFFAAAAAA, panelWidth);
        panel(g, left, listTop - 3, panelWidth, Math.max(38, listBottom - listTop + 3));
        for (int i = 0; i < lobbies.size(); i++) {
            PublicParty lobby = lobbies.get(i); int y = listTop + i * 38;
            text(g, font, Component.literal(lobby.name()), left + 8, y + 4, 0xFFFFFFFF, panelWidth - 112);
            text(g, font, tr("public.row", lobby.host(), lobby.players(), lobby.limit(), tr(lobby.inWorld() ? "public.in_world" : "public.in_lobby")),
                    left + 8, y + 19, 0xFFAAAAAA, panelWidth - 112);
        }
        if (lobbies.isEmpty()) text(g, font, tr("message." + status), left + 8, listTop + 12, 0xFFAAAAAA, panelWidth - 16);
        if (!lobbies.isEmpty()) text(g, font, tr("message." + status), left, height - 30, 0xFFAAAAAA, panelWidth);
        text(g, font, tr("public.page", page + 1, pages), left + 112, height - 49, 0xFFAAAAAA, Math.max(1, panelWidth - 208));
        text(g, font, tr("public.unverified"), left, height - 17, 0xFF999999, panelWidth);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
}
