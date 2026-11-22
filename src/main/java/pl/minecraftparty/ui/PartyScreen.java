package pl.minecraftparty.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import pl.minecraftparty.*;
import pl.minecraftparty.core.*;
import pl.minecraftparty.network.ShortCodeClient;
import java.util.*;
import static pl.minecraftparty.ui.PartyVisuals.*;

public final class PartyScreen extends Screen {
    private final Screen parent;
    private final PartyManager manager = PartyClient.manager;
    private String draftName, chatDraft = "";
    private final PartyCodeDigits draftDigits = new PartyCodeDigits();
    private int limit, page, tab, landingTab;
    private String layoutKey = "";
    private int left, contentWidth, top, bottom, playersWidth, settingsWidth, chatX, chatWidth, settingsX;
    private boolean wide, publicLobby, wasActive;
    private EditBox name;
    private PartyLandingLayout landing;
    private PublicPartiesPanel browser;
    private PartyChat chat;
    private Button create, join, copy, retry, follow, teleport;
    private Button codes;
    private PartyCodeIconButton eye;
    private final PartyCodePresentation codePresentation = new PartyCodePresentation();
    private final Map<UUID, Button> leadershipButtons = new HashMap<>();
    private final Map<UUID, Button> operatorButtons = new HashMap<>();

    public PartyScreen(Screen parent) {
        super(tr("title")); this.parent = parent;
        draftName = manager.config.lobbyName; limit = manager.config.memberLimit;
    }
    private Button button(String key, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(tr(key), b -> action.run()).bounds(x, y, w, 20).build());
    }
    @Override protected void init() {
        contentWidth = Math.min(800, width - 24); left = (width - contentWidth) / 2;
        top = 48; bottom = height - 58; wide = width >= 520 && height >= 300;
        create = join = copy = retry = follow = teleport = null; name = null; chat = null; operatorButtons.clear();
        codes = null;
        eye = null; leadershipButtons.clear();
        button("back", width - 76, 7, 68, this::onClose);
        button("diagnostics.open", 8, 7, 84, () -> minecraft.gui.setScreen(new NetworkDiagnosticsScreen(this)));
        if (!manager.active()) { if (wasActive) publicLobby = false; initCreate(); }
        else {
            if (browser != null) browser.suspend();
            if (!wide) {
                top = 71;
                int tabWidth = (contentWidth - 8) / 3;
                for (int i = 0; i < 3; i++) {
                    int target = i;
                    Button button = button("tab." + i, left + i * (tabWidth + 4), 44, tabWidth, () -> { tab = target; rebuildWidgets(); });
                    button.active = tab != i;
                }
            }
            playersWidth = wide ? Math.max(176, contentWidth * 28 / 100) : contentWidth;
            settingsWidth = wide ? Math.max(142, contentWidth * 25 / 100) : contentWidth;
            settingsX = wide ? left + contentWidth - settingsWidth : left;
            chatX = wide ? left + playersWidth + 6 : left;
            chatWidth = wide ? settingsX - chatX - 6 : contentWidth;
            if (wide || tab == 0) initPlayers();
            if (wide || tab == 1) chat = new PartyChat(minecraft, font, manager, this::addRenderableWidget,
                    chatX, top, chatWidth, bottom - top, chatDraft, value -> chatDraft = value, this::setFocused);
            if (wide || tab == 2) initSettings();
        }
        wasActive = manager.active(); layoutKey = layoutKey(); updateButtons();
    }
    private void initCreate() {
        landing = PartyLandingLayout.of(width, height);
        contentWidth = landing.content().width(); left = landing.content().x();
        if (browser == null) browser = new PublicPartiesPanel(minecraft, manager, this, this::showPublic);
        if (!landing.columns()) {
            int tabWidth = (contentWidth - 8) / 3;
            for (int i = 0; i < 3; i++) {
                int target = i;
                Button button = button("landing.tab." + i, left + i * (tabWidth + 4), 44, tabWidth,
                        () -> { landingTab = target; rebuildWidgets(); });
                button.active = landingTab != i;
            }
        }
        if (landing.columns() || landingTab == 0) initCreation(landing.create());
        if (landing.columns() || landingTab == 1) initCodeJoin(landing.join());
        if (showPublic()) browser.layout(landing.publicList(), this::addRenderableWidget);
        else browser.suspend();
    }
    private boolean showPublic() { return !manager.active() && landing != null && (landing.columns() || landingTab == 2); }
    private void initCreation(PartyLandingLayout.Rect area) {
        int x = area.x() + 8, w = area.width() - 16, y = area.y();
        name = addRenderableWidget(new EditBox(font, x, y + 23, w, 20, tr("name")));
        name.setMaxLength(40); name.setValue(draftName); name.setResponder(value -> draftName = value);
        addRenderableWidget(new PartyCapacitySlider(x, y + 49, w, limit, value -> limit = value));
        int half = (w - 6) / 2;
        create = button("create", x, y + 75, half, () -> manager.create(draftName.strip(), limit, publicLobby));
        Button visibility = button(publicLobby ? "visibility.public" : "visibility.private", x + half + 6, y + 75, w - half - 6,
                () -> { publicLobby = !publicLobby; rebuildWidgets(); });
        visibility.setTooltip(Tooltip.create(tr(publicLobby ? "visibility.public_hint" : "visibility.private_hint")));
    }
    private void initCodeJoin(PartyLandingLayout.Rect area) {
        new PartyCodeInput(font, this::addRenderableWidget, this::setFocused, draftDigits, this::updateButtons,
                this::joinParty, area.x() + (area.width() - PartyCodeInput.WIDTH) / 2, area.y() + 27);
        join = button("join", area.x() + 8, area.y() + 59, area.width() - 16, this::joinParty);
    }
    private void joinParty() { if (!manager.busy() && draftDigits.complete()) manager.join(draftDigits.value()); }
    private int rows() { return Math.max(1, (bottom - top - 54) / 26); }
    private void initPlayers() {
        LobbyState state = manager.state(); if (state == null) return;
        int maxPage = Math.max(0, (state.members().size() - 1) / rows()); page = Math.clamp(page, 0, maxPage);
        for (int i = 0; i < rows(); i++) {
            int index = page * rows() + i; if (index >= state.members().size()) break;
            var member = state.members().get(index);
            if (manager.host() != null && !member.host()) {
                Button kick = addRenderableWidget(Button.builder(Component.literal("×"), b -> manager.kick(member.id()))
                        .bounds(left + playersWidth - 25, top + 27 + i * 26, 18, 18).build());
                kick.setTooltip(Tooltip.create(tr("kick_hint", member.name())));
                if (state.worldId() == null) {
                    Button leader = addRenderableWidget(Button.builder(Component.literal("★"), b -> manager.leadership.transfer(member.id()))
                            .bounds(left + playersWidth - 49, top + 27 + i * 26, 20, 18).build());
                    leader.setTooltip(Tooltip.create(tr("leader.transfer_hint", member.name())));
                    leadershipButtons.put(member.id(), leader);
                }
                if (manager.permissions.available()) {
                    Button op = addRenderableWidget(Button.builder(Component.literal("OP"), b -> manager.toggleOperator(member.id()))
                            .bounds(left + playersWidth - 55, top + 27 + i * 26, 26, 18).build());
                    operatorButtons.put(member.id(), op);
                }
            }
        }
        if (maxPage > 0) {
            Button previous = button("previous", left + 7, bottom - 25, 38, () -> { page--; rebuildWidgets(); });
            Button next = button("next", left + playersWidth - 45, bottom - 25, 38, () -> { page++; rebuildWidgets(); });
            previous.active = page > 0; next.active = page < maxPage;
        }
    }
    private void initSettings() {
        int x = settingsX + 7, w = settingsWidth - 14;
        if (manager.host() != null) {
            if (wide) {
                eye = addRenderableWidget(new PartyCodeIconButton(x + w - 47, top + 27, 20, false, codePresentation::visible, codePresentation::toggle));
                copy = addRenderableWidget(new PartyCodeIconButton(x + w - 22, top + 27, 20, true, codePresentation::visible, this::copyInvite));
                button("rotate", x, top + 51, w, manager::rotateInvite);
                follow = button("follow", x, top + 75, w, manager::toggleMultiplayerSharing);
                teleport = button("teleport", x, top + 99, w, manager::toggleTeleport);
                retry = button("resume", x, bottom - 49, w, () -> minecraft.gui.setScreen(null));
            } else {
                eye = addRenderableWidget(new PartyCodeIconButton(x + w - 47, top + 19, 20, false, codePresentation::visible, codePresentation::toggle));
                copy = addRenderableWidget(new PartyCodeIconButton(x + w - 22, top + 19, 20, true, codePresentation::visible, this::copyInvite));
                int half = (w - 4) / 2;
                button("rotate", x, top + 42, half, manager::rotateInvite);
                follow = button("follow", x + half + 4, top + 42, half, manager::toggleMultiplayerSharing);
                teleport = button("teleport", x, top + 65, w, manager::toggleTeleport);
            }
        } else {
            retry = button("retry", x, bottom - 49, w, manager::retryWorld);
        }
        if (follow != null) follow.setTooltip(Tooltip.create(tr("follow_hint")));
        if (teleport != null) teleport.setTooltip(Tooltip.create(tr("teleport_hint")));
        button("leave", x, bottom - 25, w, manager::leave);
    }
    private void copyInvite() {
        if (ShortCodeClient.isCode(manager.codes.code())) { minecraft.keyboardHandler.setClipboard(manager.codes.code()); manager.message("copied"); }
    }
    private String layoutKey() {
        var state = manager.state();
        return manager.active() + ":" + (manager.host() != null) + ":" + manager.permissions.available() + ":" +
                (state == null || state.worldId() == null) + ":" +
                (state == null ? "" : state.members().stream().map(m -> m.id().toString()).toList());
    }
    @Override public void tick() {
        if (!layoutKey.equals(layoutKey())) rebuildWidgets();
        updateButtons();
        if (browser != null && showPublic()) browser.tick();
    }
    @Override public void removed() { if (browser != null) browser.suspend(); super.removed(); }
    private void updateButtons() {
        if (create != null) create.active = !manager.busy() && !draftName.isBlank();
        if (join != null) join.active = !manager.busy() && draftDigits.complete();
        codePresentation.sync(manager.codes.code());
        if (codes != null && manager.host() != null) codes.setMessage(tr("codes.open"));
        if (copy != null) copy.active = ShortCodeClient.isCode(manager.codes.code()) && !manager.leadership.busy();
        if (eye != null) { eye.active = ShortCodeClient.isCode(manager.codes.code()); eye.refreshLabel(); }
        leadershipButtons.forEach((id, button) -> button.active = !manager.leadership.busy() && manager.state() != null
                && manager.state().members().stream().anyMatch(m -> m.id().equals(id) && m.status() == LobbyState.Status.LOBBY));
        if (follow != null) follow.setMessage(tr("follow", Component.translatable(manager.config.shareMultiplayer ? "options.on" : "options.off")));
        if (teleport != null) {
            teleport.setMessage(tr("teleport", Component.translatable(manager.config.allowPartyTeleport ? "options.on" : "options.off")));
            teleport.active = manager.permissions.available();
        }
        operatorButtons.forEach((id, button) -> {
            boolean op = manager.permissions.operator(id);
            button.setMessage(Component.literal(op ? "OP−" : "OP+"));
            button.setTooltip(Tooltip.create(tr(op ? "operator.revoke_hint" : "operator.grant_hint")));
            button.active = manager.permissions.available() && (op || manager.state() != null && manager.state().members().stream()
                    .anyMatch(member -> member.id().equals(id) && member.status() == LobbyState.Status.IN_WORLD));
        });
        if (retry != null) retry.active = manager.host() != null ? minecraft.level != null : manager.guest() != null
                && manager.guest().connected() && manager.state() != null && manager.state().worldId() != null && minecraft.level == null;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        background(graphics, width, height);
        centered(graphics, font, title, width, 12, 0xFFFFFFFF);
        LobbyState state = manager.state();
        centered(graphics, font, !manager.codes.check().isEmpty() && manager.host() == null ? tr("codes.check", manager.codes.check())
                : state == null ? tr("subtitle") : Component.literal(state.name()).append(" · ").append(tr(state.publicLobby() ? "visibility.public" : "visibility.private")), width, 29, 0xFFAAAAAA);
        if (!manager.active()) {
            if (landing.columns() || landingTab == 0) {
                var area = landing.create(); panel(graphics, area.x(), area.y(), area.width(), area.height());
                text(graphics, font, tr("name"), area.x() + 8, area.y() + 9, 0xFFFFFFFF, area.width() - 16);
            }
            if (landing.columns() || landingTab == 1) {
                var area = landing.join(); panel(graphics, area.x(), area.y(), area.width(), area.height());
                text(graphics, font, tr("code.label"), area.x() + 8, area.y() + 9, 0xFFFFFFFF, area.width() - 16);
                text(graphics, font, Component.literal("−"), area.x() + area.width() / 2 - font.width("−") / 2,
                        area.y() + 36, 0xFFAAAAAA, 12);
            }
            if (showPublic()) browser.draw(graphics, font);
        } else {
            if (wide || tab == 0) drawPlayers(graphics, state);
            if (chat != null) chat.draw(graphics);
            if (wide || tab == 2) drawSettings(graphics, state);
        }
        text(graphics, font, tr("message." + manager.message(), manager.codes.check()), left, height - 49, 0xFFFFFFFF, contentWidth);
        text(graphics, font, tr("codes.privacy"), left, height - 32, 0xFFAAAAAA, contentWidth);
        text(graphics, font, tr("privacy_invite"), left, height - 20, 0xFFAAAAAA, contentWidth);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
    private void drawPlayers(GuiGraphicsExtractor graphics, LobbyState state) {
        panel(graphics, left, top, playersWidth, bottom - top);
        text(graphics, font, tr("players", state == null ? 0 : state.members().size(), state == null ? "?" : state.limit()), left + 7, top + 8, 0xFFFFFFFF, playersWidth - 14);
        if (state != null) for (int i = 0; i < rows(); i++) {
            int index = page * rows() + i; if (index >= state.members().size()) break;
            var member = state.members().get(index);
            member(graphics, font, member, left + 7, top + 28 + i * 26,
                    playersWidth - (operatorButtons.containsKey(member.id()) || leadershipButtons.containsKey(member.id()) ? 68 : manager.host() != null && !member.host() ? 38 : 14), true);
        }
        if (state != null && state.members().size() > rows())
            text(graphics, font, Component.literal((page + 1) + " / " + ((state.members().size() - 1) / rows() + 1)), left + 52, bottom - 18, 0xFFAAAAAA, playersWidth - 104);
    }
    private void drawSettings(GuiGraphicsExtractor graphics, LobbyState state) {
        panel(graphics, settingsX, top, settingsWidth, bottom - top);
        text(graphics, font, tr(manager.host() != null ? "settings.host" : "settings.party"), settingsX + 7, top + 7, 0xFFFFFFFF, settingsWidth - 14);
        if (manager.host() != null) text(graphics, font, Component.literal(codePresentation.display()), settingsX + 7,
                top + (wide ? 33 : 25), 0xFFFFFFAA, settingsWidth - 64);
        if (manager.host() == null) {
            text(graphics, font, tr("guest"), settingsX + 7, top + 28, 0xFFAAAAAA, settingsWidth - 14);
            text(graphics, font, tr(state == null || state.worldId() == null ? "destination.lobby" : state.multiplayerTarget() == null
                    ? "destination.world" : "destination.server"), settingsX + 7, top + 42, 0xFFAAAAAA, settingsWidth - 14);
        }
    }
    @Override public void onClose() { minecraft.gui.setScreen(minecraft.level == null && parent instanceof PauseScreen ? new TitleScreen() : parent); }
    @Override public boolean isPauseScreen() { return false; }
}
