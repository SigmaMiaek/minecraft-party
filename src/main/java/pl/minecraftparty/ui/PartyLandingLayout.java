package pl.minecraftparty.ui;

/** Layout in Minecraft's scaled GUI coordinates. Narrow windows show one tab at a time. */
public record PartyLandingLayout(Rect content, Rect create, Rect join, Rect publicList, boolean columns) {
    public record Rect(int x, int y, int width, int height) { }
    public static PartyLandingLayout of(int width, int height) {
        int w = Math.min(900, width - 24), x = (width - w) / 2;
        boolean columns = width >= 560 && height >= 300;
        int y = columns ? 48 : 71, h = height - 58 - y;
        Rect content = new Rect(x, y, w, h);
        if (!columns) return new PartyLandingLayout(content, content, content, content, false);
        int leftWidth = Math.clamp(w * 39 / 100, 228, 320), gap = 10;
        int createHeight = 100, joinY = y + createHeight + 8;
        return new PartyLandingLayout(content, new Rect(x, y, leftWidth, createHeight),
                new Rect(x, joinY, leftWidth, height - 58 - joinY),
                new Rect(x + leftWidth + gap, y, w - leftWidth - gap, h), true);
    }
}
