package pl.minecraftparty.ui;

/** Six independent slots: a gap must never be silently shifted into a different code. */
public final class PartyCodeDigits {
    private final String[] digits = {"", "", "", "", "", ""};
    public String digit(int index) { return digits[index]; }
    public String value() { return String.join("", digits); }
    public boolean complete() {
        for (String digit : digits) if (digit.isEmpty()) return false;
        return true;
    }
    /** Returns the next focused slot, or -1 if the entire input was rejected. */
    public int insert(int index, String text) {
        if (text == null || text.length() > 32 || !text.matches("[0-9\\s-]+")) return -1;
        String clean = text.replaceAll("[\\s-]", "");
        if (clean.isEmpty() || clean.length() > 6) return -1;
        int start = clean.length() == 6 ? 0 : index;
        if (start + clean.length() > 6) return -1;
        for (int i = 0; i < clean.length(); i++) digits[start + i] = clean.substring(i, i + 1);
        return Math.min(5, start + clean.length());
    }
    public int backspace(int index) {
        if (digits[index].isEmpty() && index > 0) index--;
        digits[index] = ""; return index;
    }
    public void delete(int index) { digits[index] = ""; }
    public void clear() { java.util.Arrays.fill(digits, ""); }
}
