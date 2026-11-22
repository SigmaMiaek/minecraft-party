package pl.minecraftparty.ui;

/** Visibility is local screen state; copying never depends on revealing the code. */
public final class PartyCodePresentation {
    private String code = "";
    private boolean visible;
    public void sync(String next) { if (!code.equals(next)) { code = next; visible = false; } }
    public boolean visible() { return visible; }
    public void toggle() { if (code.matches("[0-9]{6}")) visible = !visible; }
    public String display() {
        if (!code.matches("[0-9]{6}")) return "--- - ---";
        return visible ? code.substring(0, 3) + " - " + code.substring(3) : "••• - •••";
    }
}
