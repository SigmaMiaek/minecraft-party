package pl.minecraftparty.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import java.util.function.*;
import static pl.minecraftparty.ui.PartyVisuals.*;
import static org.lwjgl.glfw.GLFW.*;

/** Vanilla EditBoxes, with whole-code paste, automatic advance and keyboard navigation. */
public final class PartyCodeInput {
    public static final int BOX = 26, GAP = 6, GROUP_GAP = 14;
    public static final int WIDTH = BOX * 6 + GAP * 5 + GROUP_GAP;
    private final EditBox[] boxes = new EditBox[6];
    private final PartyCodeDigits digits;
    private final Consumer<EditBox> focus;
    private final Runnable changed, submit;
    public PartyCodeInput(Font font, Function<EditBox, EditBox> add, Consumer<EditBox> focus,
                          PartyCodeDigits digits, Runnable changed, Runnable submit, int x, int y) {
        this.digits = digits; this.focus = focus; this.changed = changed; this.submit = submit;
        for (int i = 0; i < 6; i++) {
            int slot = i;
            EditBox field = new EditBox(font, x + i * (BOX + GAP) + (i >= 3 ? GROUP_GAP : 0), y, BOX, BOX, tr("code.digit", i + 1)) {
                @Override public void insertText(String text) {
                    int next = digits.insert(slot, text);
                    if (next >= 0) { sync(); select(next); changed.run(); }
                }
                @Override public boolean keyPressed(KeyEvent event) {
                    if (event.key() == GLFW_KEY_BACKSPACE) { select(digits.backspace(slot)); sync(); changed.run(); return true; }
                    if (event.key() == GLFW_KEY_DELETE) { digits.delete(slot); sync(); changed.run(); return true; }
                    if (event.isLeft()) { select(Math.max(0, slot - 1)); return true; }
                    if (event.isRight()) { select(Math.min(5, slot + 1)); return true; }
                    if (event.key() == GLFW_KEY_ENTER || event.key() == GLFW_KEY_KP_ENTER) { if (digits.complete()) submit.run(); return true; }
                    if (event.isCopy() || event.isCut()) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(digits.complete() ? digits.value() : digits.digit(slot));
                        if (event.isCut()) { digits.delete(slot); sync(); changed.run(); }
                        return true;
                    }
                    return super.keyPressed(event);
                }
            };
            field.setMaxLength(1); field.setCentered(true); field.setValue(digits.digit(i));
            boxes[i] = add.apply(field);
        }
    }
    private void select(int index) {
        focus.accept(boxes[index]); boxes[index].moveCursorToEnd(false); boxes[index].setHighlightPos(0);
    }
    private void sync() { for (int i = 0; i < 6; i++) boxes[i].setValue(digits.digit(i)); }
}
