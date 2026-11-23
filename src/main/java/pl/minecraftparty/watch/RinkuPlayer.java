package pl.minecraftparty.watch;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import java.lang.reflect.*;

/** Optional Rinku 3.x API adapter. Missing Chromium never prevents loading the Party mod. */
public final class RinkuPlayer implements AutoCloseable {
    private final Object browser;
    private final Method texture, click, release, move, wheel, focus, close;
    public static boolean installed() { return FabricLoader.getInstance().isModLoaded("rinku"); }
    public RinkuPlayer(String localPage) throws ReflectiveOperationException {
        Class<?> api = Class.forName("de.keksuccino.rinku.Rinku");
        if (!Boolean.TRUE.equals(api.getMethod("isInitialized").invoke(null))) throw new IllegalStateException("browser not ready");
        Class<?> browserType = Class.forName("de.keksuccino.rinku.RinkuBrowser");
        texture = browserType.getMethod("getTextureIdentifier");
        click = browserType.getMethod("sendMousePress", int.class, int.class, int.class);
        release = browserType.getMethod("sendMouseRelease", int.class, int.class, int.class);
        move = browserType.getMethod("sendMouseMove", int.class, int.class);
        wheel = browserType.getMethod("sendMouseWheel", int.class, int.class, double.class, int.class);
        focus = browserType.getMethod("setFocus", boolean.class);
        close = browserType.getMethod("close");
        browser = api.getMethod("createBrowser", String.class, boolean.class, int.class, int.class)
                .invoke(null, localPage, false, 1280, 720);
        if (browser == null) throw new IllegalStateException("browser unavailable");
    }
    public Identifier texture() {
        try { return (Identifier) texture.invoke(browser); }
        catch (ReflectiveOperationException e) { return null; }
    }
    public void click(double u, double v, int button) {
        try { focus.invoke(browser, true); click.invoke(browser, (int) (u * 1280), (int) (v * 720), button); }
        catch (ReflectiveOperationException ignored) { }
    }
    public void release(double u, double v, int button) {
        try { release.invoke(browser, (int) (Math.clamp(u, 0, 1) * 1280), (int) (Math.clamp(v, 0, 1) * 720), button); }
        catch (ReflectiveOperationException ignored) { }
    }
    public void move(double u, double v) {
        try { move.invoke(browser, (int) (u * 1280), (int) (v * 720)); } catch (ReflectiveOperationException ignored) { }
    }
    public void wheel(double u, double v, double amount, int modifiers) {
        try { wheel.invoke(browser, (int) (u * 1280), (int) (v * 720), amount, modifiers); } catch (ReflectiveOperationException ignored) { }
    }
    public void unfocus() { try { focus.invoke(browser, false); } catch (ReflectiveOperationException ignored) { } }
    @Override public void close() {
        try { close.invoke(browser); } catch (ReflectiveOperationException ignored) { }
    }
}
