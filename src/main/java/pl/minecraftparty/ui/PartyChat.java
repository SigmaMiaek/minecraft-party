package pl.minecraftparty.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import pl.minecraftparty.PartyManager;
import pl.minecraftparty.core.LobbyModel;
import java.util.*;
import java.util.function.Consumer;
import static pl.minecraftparty.ui.PartyVisuals.*;

/** Shared actual lobby chat UI for Party and the title-screen sidebar. */
public final class PartyChat {
    private final PartyManager manager;
    private final Font font;
    private final int x, y, width, height;
    private final EditBox input;
    private final Button send, up, down;
    private final Consumer<EditBox> focus;
    private int scroll, maxScroll;

    public PartyChat(Minecraft client, Font font, PartyManager manager, Consumer<AbstractWidget> add,
                     int x, int y, int width, int height, String draft, Consumer<String> onDraft, Consumer<EditBox> focus) {
        this.manager = manager; this.font = font; this.x = x; this.y = y; this.width = width; this.height = height;
        this.focus = focus;
        input = new EditBox(font, x + 6, y + height - 25, width - 54, 20, tr("chat.input")) {
            @Override public boolean keyPressed(KeyEvent event) {
                if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) { submit(); return true; }
                return super.keyPressed(event);
            }
        };
        input.setMaxLength(LobbyModel.MAX_CHAT_LENGTH); input.setHint(tr("chat.input")); input.setValue(draft);
        input.setResponder(onDraft); add.accept(input);
        send = Button.builder(tr("chat.send"), b -> submit()).bounds(x + width - 44, y + height - 25, 38, 20).build(); add.accept(send);
        up = Button.builder(Component.literal("↑"), b -> scroll = Math.min(maxScroll, scroll + 3)).bounds(x + width - 43, y + 4, 18, 18).build(); add.accept(up);
        down = Button.builder(Component.literal("↓"), b -> scroll = Math.max(0, scroll - 3)).bounds(x + width - 24, y + 4, 18, 18).build(); add.accept(down);
        up.setTooltip(Tooltip.create(tr("chat.older"))); down.setTooltip(Tooltip.create(tr("chat.newer")));
    }
    private void submit() {
        if (manager.chat(input.getValue())) { input.setValue(""); focus.accept(input); scroll = 0; }
    }
    public void draw(GuiGraphicsExtractor g) {
        boolean shown = manager.active();
        input.setVisible(shown); send.visible = up.visible = down.visible = shown;
        if (!shown) return;
        panel(g, x, y, width, height);
        text(g, font, tr("chat.title"), x + 7, y + 8, 0xFFFFFFFF, width - 54);
        var state = manager.state();
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (state != null) for (var message : state.chat()) {
            lines.addAll(font.split(Component.literal("<" + message.senderName() + "> " + message.text()), Math.max(1, width - 14)));
        }
        int visible = Math.max(1, (height - 58) / font.lineHeight);
        maxScroll = Math.max(0, lines.size() - visible); scroll = Math.min(scroll, maxScroll);
        int first = Math.max(0, lines.size() - visible - scroll);
        if (lines.isEmpty()) text(g, font, tr("chat.empty"), x + 7, y + 29, 0xFFAAAAAA, width - 14);
        else for (int i = first; i < Math.min(lines.size(), first + visible); i++)
            g.text(font, lines.get(i), x + 7, y + 28 + (i - first) * font.lineHeight, 0xFFFFFFFF, true);
        input.setEditable(manager.canChat()); send.active = manager.canChat() && !input.getValue().isBlank();
        up.active = scroll < maxScroll; down.active = scroll > 0;
    }
}
