package pl.minecraftparty.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import pl.minecraftparty.*;
import pl.minecraftparty.core.LobbyState;
import java.util.*;
import static pl.minecraftparty.ui.PartyVisuals.*;

/** Vanilla buttons and progress, with explicit recipient selection and no custom world objects. */
public final class WorldTransferScreen extends Screen {
    private final Screen parent;
    private final PartyManager party = PartyClient.manager;
    private UUID selected;
    private boolean leave = true;
    private String layout = "";
    private int x, w;
    private Button send, cancel, resume, retry;
    private final Map<UUID, Button> recipients = new LinkedHashMap<>();
    public WorldTransferScreen(Screen parent) { super(tr("gift.title")); this.parent = parent; }
    private Button button(String key, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(tr(key), b -> action.run()).bounds(x, y, width, 20).build());
    }
    private String layoutKey() {
        return party.worlds.busy() + ":" + party.worlds.phase() + ":" + party.worlds.canOffer() + ":" + party.worlds.canResume() + ":" + party.worlds.canRetryOpen()
                + ":" + (party.state() == null ? "" : party.state().members().stream().map(m -> m.id().toString() + m.status()).toList());
    }
    @Override protected void init() {
        w = Math.min(460, width - 24); x = (width - w) / 2; recipients.clear(); send = cancel = resume = retry = null;
        button("back", width - 76, 7, 68, this::onClose);
        if (party.worlds.canOffer()) {
            var members = party.state().members().stream().filter(m -> !m.host()).toList();
            int half = (w - 6) / 2;
            for (int i = 0; i < members.size(); i++) {
                var member = members.get(i);
                Button recipient = addRenderableWidget(Button.builder(Component.literal(member.name()), b -> { selected = member.id(); update(); })
                        .bounds(x + i % 2 * (half + 6), 47 + i / 2 * 22, half, 20).build());
                recipient.active = member.status() != LobbyState.Status.DISCONNECTED;
                recipients.put(member.id(), recipient);
            }
            Button departing = button("gift.leave_after", x, 162, w, () -> { leave = !leave; rebuildWidgets(); });
            departing.setMessage(tr("gift.leave_after", Component.translatable(leave ? "options.on" : "options.off")));
            send = button("gift.send", x, 187, w, () -> {
                if (selected == null) return;
                minecraft.gui.setScreen(new ConfirmScreen(yes -> {
                    minecraft.gui.setScreen(this);
                    if (yes) { party.worlds.offer(selected, leave); rebuildWidgets(); }
                }, tr("gift.confirm_title"), tr("gift.confirm_prompt"), tr("gift.send"), tr("back")));
            });
        } else {
            if (party.worlds.canCancel()) cancel = button("gift.cancel", x, height - 56, w, party.worlds::cancel);
            else if (party.worlds.canResume()) resume = button("gift.resume_original", x, height - 56, w, party.worlds::resumeOriginal);
            else if (party.worlds.canRetryOpen()) retry = button("gift.retry_open", x, height - 56, w, party.worlds::retryOpen);
        }
        layout = layoutKey(); update();
    }
    private void update() {
        recipients.forEach((id, button) -> {
            var member = party.state() == null ? null : party.state().members().stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
            button.setMessage(Component.literal((id.equals(selected) ? "✓ " : "") + (member == null ? "?" : member.name())));
        });
        if (send != null) send.active = selected != null && recipients.containsKey(selected) && recipients.get(selected).active && party.worlds.canOffer();
    }
    @Override public void tick() { if (!layout.equals(layoutKey())) rebuildWidgets(); update(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        background(g, width, height); centered(g, font, title, width, 12, 0xFFFFFFFF);
        if (party.worlds.canOffer()) {
            text(g, font, tr(party.worlds.failure().isEmpty() ? "gift.choose" : "gift.error." + party.worlds.failure()), x, 32,
                    party.worlds.failure().isEmpty() ? 0xFFAAAAAA : 0xFFFFAAAA, w);
            if (recipients.isEmpty()) text(g, font, tr("gift.no_recipients"), x, 72, 0xFFAAAAAA, w);
        } else {
            text(g, font, Component.literal(party.worlds.name()), x, 49, 0xFFFFFFFF, w);
            text(g, font, tr("gift.phase." + party.worlds.phase()), x, 69, 0xFFAAAAAA, w);
            long total = party.worlds.total(), done = party.worlds.bytes(); int y = 95;
            g.fill(x, y, x + w, y + 14, 0xFF444444); g.fill(x + 1, y + 1, x + w - 1, y + 13, 0xFF111111);
            if (total > 0) {
                int filled = (int) (Math.clamp(done / (double) total, 0, 1) * (w - 2));
                g.fill(x + 1, y + 1, x + 1 + filled, y + 13, 0xFF55AA55);
                text(g, font, tr("gift.progress", String.format(Locale.ROOT, "%.1f", done / 1048576.0), String.format(Locale.ROOT, "%.1f", total / 1048576.0)), x, y + 20, 0xFFAAAAAA, w);
            } else if (party.worlds.busy()) {
                int offset = (int) ((Util.getMillis() / 12) % Math.max(1, w - 42));
                g.fill(x + 1 + offset, y + 1, x + 41 + offset, y + 13, 0xFF55AA55);
            }
            if (!party.worlds.failure().isEmpty()) text(g, font, tr("gift.error." + party.worlds.failure()), x, y + 41, 0xFFFFAAAA, w);
            if (!party.worlds.busy() && !party.worlds.canResume() && !party.worlds.canRetryOpen() && !party.worlds.phase().equals("complete"))
                text(g, font, tr("gift.world_required"), x, y + 56, 0xFFAAAAAA, w);
        }
        text(g, font, tr("gift.original_kept"), x, height - 19, 0xFFAAAAAA, w);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent == null || minecraft.level == null && parent instanceof PauseScreen ? new TitleScreen() : parent); }
    @Override public boolean isPauseScreen() { return false; }
}
