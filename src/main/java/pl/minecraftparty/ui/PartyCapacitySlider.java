package pl.minecraftparty.ui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import pl.minecraftparty.core.LobbyModel;
import java.util.function.IntConsumer;
import static pl.minecraftparty.ui.PartyVisuals.*;

/** Native slider with discrete player counts and one-player keyboard steps. */
public final class PartyCapacitySlider extends AbstractSliderButton {
    private final IntConsumer changed;
    private int players;
    public PartyCapacitySlider(int x, int y, int width, int initial, IntConsumer changed) {
        super(x, y, width, 20, tr("limit", initial), position(initial));
        this.changed = changed; players = Math.clamp(initial, 2, LobbyModel.MAX_MEMBERS);
        setTooltip(Tooltip.create(tr("limit_hint"))); updateMessage();
    }
    static int count(double position) {
        if (!Double.isFinite(position)) return 2;
        return 2 + (int) Math.round(Math.clamp(position, 0, 1) * (LobbyModel.MAX_MEMBERS - 2));
    }
    static double position(int players) { return (Math.clamp(players, 2, LobbyModel.MAX_MEMBERS) - 2.0) / (LobbyModel.MAX_MEMBERS - 2); }
    @Override protected void updateMessage() { setMessage(tr("limit", players)); }
    @Override protected void applyValue() { players = count(value); value = position(players); changed.accept(players); }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.isLeft() || event.isRight()) {
            players = Math.clamp(players + (event.isLeft() ? -1 : 1), 2, LobbyModel.MAX_MEMBERS);
            value = position(players); changed.accept(players); updateMessage(); return true;
        }
        return super.keyPressed(event);
    }
}
