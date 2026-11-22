package pl.minecraftparty.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PartyCodePresentationTest {
    @Test void codeIsHiddenOnOpeningAndChangingButExplicitRevealIsPreservedForTheSameCode() {
        var view = new PartyCodePresentation(); view.sync("001234");
        assertFalse(view.visible()); assertFalse(view.display().contains("001"));
        view.toggle(); assertEquals("001 - 234", view.display());
        view.sync("001234"); assertTrue(view.visible());
        view.sync("654321"); assertFalse(view.visible()); assertFalse(view.display().contains("654"));
        view.toggle(); view.toggle(); assertFalse(view.visible());
    }
    @Test void unavailableCodeCannotBeRevealed() {
        var view = new PartyCodePresentation(); view.toggle(); assertFalse(view.visible());
        assertEquals("--- - ---", view.display());
    }
}
