package pl.minecraftparty.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PartyCodeDigitsTest {
    @Test void typingAdvancesWithoutDroppingLeadingZeroes() {
        var code = new PartyCodeDigits();
        for (int i = 0; i < 6; i++) assertEquals(Math.min(5, i + 1), code.insert(i, "001234".substring(i, i + 1)));
        assertTrue(code.complete()); assertEquals("001234", code.value());
    }
    @Test void pastingAFormattedCompleteCodeReplacesAllSlotsFromAnyFocus() {
        var code = new PartyCodeDigits(); code.insert(0, "987654");
        assertEquals(5, code.insert(4, " 001-234\n")); assertEquals("001234", code.value());
    }
    @Test void aHoleCannotEnableJoinOrShiftTheRemainingDigits() {
        var code = new PartyCodeDigits(); code.insert(0, "123456"); code.delete(2);
        assertFalse(code.complete()); assertEquals("", code.digit(2)); assertEquals("4", code.digit(3));
        code.insert(2, "0"); assertTrue(code.complete()); assertEquals("120456", code.value());
    }
    @Test void backspaceAcrossAnEmptySlotRemovesThePreviousDigit() {
        var code = new PartyCodeDigits(); code.insert(0, "12");
        assertEquals(1, code.backspace(2)); assertEquals("1", code.value());
        assertEquals(0, code.backspace(1)); assertEquals("", code.value());
        assertEquals(0, code.backspace(0));
    }
    @Test void legacyInvitationsLettersUnicodeAndOversizedPastesNeverBecomeCodes() {
        var code = new PartyCodeDigits(); code.insert(0, "123456");
        for (String text : new String[]{"mp3:123456", "mp2:123456", "abc123", "１２３４５６", "1234567", "", "---", " ".repeat(100)}) {
            assertEquals(-1, code.insert(0, text)); assertEquals("123456", code.value());
        }
        assertEquals(-1, code.insert(5, "12")); assertEquals("123456", code.value());
    }
    @Test void clearingStartsAFreshIncompleteInvitation() {
        var code = new PartyCodeDigits(); code.insert(0, "000000"); code.clear();
        assertFalse(code.complete()); assertEquals("", code.value());
    }
}
