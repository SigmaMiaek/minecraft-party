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
    private String draftCode = "", draftName, chatDraft = "";
    private int limit, page, tab;
    private String layoutKey = "";
    private int left, contentWidth, top, bottom, playersWidth, settingsWidth, chatX, chatWidth, settingsX;
    private boolean wide;
    private EditBox name, code;
    private PartyChat chat;
    private Button create, join, copy, retry, follow, teleport;
    private Button codes;
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
        create = join = copy = retry = follow = teleport = null; name = code = null; chat = null; operatorButtons.clear();
        codes = null;
        button("back", width - 76, 7, 68, this::onClose);
        button("diagnostics.open", 8, 7, 84, () -> minecraft.gui.setScreen(new NetworkDiagnosticsScreen(this)));
        if (!manager.active()) initCreate();
        else {
            if (!wide) {
                top = 71;
                int tabWidth = (contentWidth - 8) / 3;
                for (int i = 0; i < 3; i++) {
                    int target = i;
                    Button button = button("tab." + i, left + i * (tabWidth + 4), 44, tabWidth, () -> { tab = target; rebuildWidgets(); });
                    button.active = tab != i;
                }
            }
            playersWidth = wide ? Math.max(140, contentWidth * 26 / 100) : contentWidth;
            settingsWidth = wide ? Math.max(142, contentWidth * 25 / 100) : contentWidth;
            settingsX = wide ? left + contentWidth - settingsWidth : left;
            chatX = wide ? left + playersWidth + 6 : left;
            chatWidth = wide ? settingsX - chatX - 6 : contentWidth;
            if (wide || tab == 0) initPlayers();
            if (wide || tab == 1) chat = new PartyChat(minecraft, font, manager, this::addRenderableWidget,
                    chatX, top, chatWidth, bottom - top, chatDraft, value -> chatDraft = value, this::setFocused);
            if (wide || tab == 2) initSettings();
        }
        layoutKey = layoutKey(); updateButtons();
    }
    private void initCreate() {
        int formWidth = Math.min(380, contentWidth - 16), x = (width - formWidth) / 2;
        name = addRenderableWidget(new EditBox(font, x, 66, formWidth - 112, 20, tr("name")));
        name.setMaxLength(40); name.setValue(draftName); name.setResponder(value -> draftName = value);
        Button capacity = button("limit", x + formWidth - 106, 66, 106, () -> {
            limit = limit == LobbyModel.MAX_MEMBERS ? 2 : limit + 1; rebuildWidgets();
        });
        capacity.setMessage(tr("limit", limit));
        int half = (formWidth - 6) / 2;
        Button mode = button(manager.config.networkLabel(), x, 94, half, () -> {
            manager.config.nextNetworkMode(); rebuildWidgets();
        });
        mode.setTooltip(Tooltip.create(tr("network.hint")));
        create = button("create", x + half + 6, 94, formWidth - half - 6, () -> manager.create(draftName.strip(), limit));
        code = addRenderableWidget(new EditBox(font, x, 132, formWidth, 20, tr("invite")));
        code.setMaxLength(Invite.MAX_CODE_LENGTH); code.setHint(tr("paste")); code.setValue(draftCode);
        code.setResponder(value -> { draftCode = value; updateButtons(); });
        join = button("join", x, 160, formWidth, () -> { manager.join(draftCode); draftCode = ""; });
        if (height >= 270) codes = button("codes.open", x, 188, formWidth, () -> minecraft.gui.setScreen(new PartyCodesScreen(this)));
    }
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
            if (height >= 290) codes = button("codes.open", x, bottom - 73, w, () -> minecraft.gui.setScreen(new PartyCodesScreen(this)));
            if (wide) {
                copy = button("copy", x, top + 27, w, this::copyInvite);
                button("rotate", x, top + 51, w, manager::rotateInvite);
                follow = button("follow", x, top + 75, w, manager::toggleMultiplayerSharing);
                teleport = button("teleport", x, top + 99, w, manager::toggleTeleport);
                retry = button("resume", x, bottom - 49, w, () -> minecraft.gui.setScreen(null));
            } else {
                int half = (w - 4) / 2;
                copy = button("copy", x, top + 16, half, this::copyInvite);
                button("rotate", x + half + 4, top + 16, half, manager::rotateInvite);
                follow = button("follow", x, top + 40, w, manager::toggleMultiplayerSharing);
                teleport = button("teleport", x, top + 64, w, manager::toggleTeleport);
            }
        } else {
            retry = button("retry", x, bottom - 49, w, manager::retryWorld);
        }
        if (follow != null) follow.setTooltip(Tooltip.create(tr("follow_hint")));
        if (teleport != null) teleport.setTooltip(Tooltip.create(tr("teleport_hint")));
        button("leave", x, bottom - 25, w, manager::leave);
    }
    private void copyInvite() { minecraft.keyboardHandler.setClipboard(manager.codes.code().isEmpty() ? manager.invite() : manager.codes.code()); manager.message("copied"); }
    private String layoutKey() {
        var state = manager.state();
        return manager.active() + ":" + (manager.host() != null) + ":" + manager.permissions.available() + ":" +
                (state == null ? "" : state.members().stream().map(m -> m.id().toString()).toList());
    }
    @Override public void tick() { if (!layoutKey.equals(layoutKey())) rebuildWidgets(); updateButtons(); }
    private void updateButtons() {
        if (create != null) create.active = !manager.busy() && !draftName.isBlank();
        if (join != null) join.active = !manager.busy() && (Invite.hasSupportedPrefix(draftCode) || ShortCodeClient.isCode(draftCode));
        if (codes != null && manager.host() != null) codes.setMessage(tr("codes.manage", manager.codes.code().isEmpty() ? "—" : manager.codes.code(), manager.codes.pending().size()));
        if (copy != null) copy.active = !manager.invite().isEmpty();
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
                : state == null ? tr("subtitle") : Component.literal(state.name()), width, 29, 0xFFAAAAAA);
        if (!manager.active()) {
            int formWidth = Math.min(380, contentWidth - 16), x = (width - formWidth) / 2;
            text(graphics, font, tr("name"), x, 53, 0xFFFFFFFF, formWidth);
            text(graphics, font, tr("invite"), x, 119, 0xFFFFFFFF, formWidth);
        } else {
            if (wide || tab == 0) drawPlayers(graphics, state);
            if (chat != null) chat.draw(graphics);
            if (wide || tab == 2) drawSettings(graphics, state);
        }
        text(graphics, font, tr("message." + manager.message(), manager.codes.check()), left, height - 49, 0xFFFFFFFF, contentWidth);
        text(graphics, font, tr("privacy"), left, height - 32, 0xFFAAAAAA, contentWidth);
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
                    playersWidth - (operatorButtons.containsKey(member.id()) ? 68 : manager.host() != null && !member.host() ? 38 : 14), true);
        }
        if (state != null && state.members().size() > rows())
            text(graphics, font, Component.literal((page + 1) + " / " + ((state.members().size() - 1) / rows() + 1)), left + 52, bottom - 18, 0xFFAAAAAA, playersWidth - 104);
    }
    private void drawSettings(GuiGraphicsExtractor graphics, LobbyState state) {
        panel(graphics, settingsX, top, settingsWidth, bottom - top);
        text(graphics, font, tr(manager.host() != null ? "settings.host" : "settings.party"), settingsX + 7, top + 7, 0xFFFFFFFF, settingsWidth - 14);
        if (manager.host() == null) {
            text(graphics, font, tr("guest"), settingsX + 7, top + 28, 0xFFAAAAAA, settingsWidth - 14);
            text(graphics, font, tr(state == null || state.worldId() == null ? "destination.lobby" : state.multiplayerTarget() == null
                    ? "destination.world" : "destination.server"), settingsX + 7, top + 42, 0xFFAAAAAA, settingsWidth - 14);
        }
    }
    @Override public void onClose() { minecraft.gui.setScreen(minecraft.level == null && parent instanceof PauseScreen ? new TitleScreen() : parent); }
    @Override public boolean isPauseScreen() { return false; }
}
