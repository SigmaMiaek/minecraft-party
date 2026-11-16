package pl.minecraftparty.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import pl.minecraftparty.core.LobbyState;
import java.util.*;

/** Vanilla textures and face rendering; profile/skin resolution is handled asynchronously by Minecraft. */
public final class PartyVisuals {
    private static final Identifier DIRT = Identifier.withDefaultNamespace("textures/block/dirt.png");
    private static final Map<UUID, ResolvableProfile> PROFILES = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, ResolvableProfile> entry) { return size() > 32; }
    };
    private PartyVisuals() { }
    public static Component tr(String key, Object... args) { return Component.translatable("party." + key, args); }
    public static void background(GuiGraphicsExtractor graphics, int width, int height) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, DIRT, 0, 0, 0, 0, width, height, 32, 32, 0xFF404040);
    }
    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xA0000000);
        graphics.fill(x, y, x + width, y + 1, 0xFF555555);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF151515);
    }
    public static void text(GuiGraphicsExtractor g, Font font, Component label, int x, int y, int color, int width) {
        g.text(font, font.plainSubstrByWidth(label.getString(), Math.max(1, width)), x, y, color, true);
    }
    public static void centered(GuiGraphicsExtractor g, Font font, Component label, int width, int y, int color) {
        g.text(font, label, (width - font.width(label)) / 2, y, color, true);
    }
    public static void member(GuiGraphicsExtractor g, Font font, LobbyState.Member member, int x, int y, int width, boolean status) {
        PlayerFaceExtractor.extractRenderState(g, PROFILES.computeIfAbsent(member.id(), ResolvableProfile::createUnresolved), x, y + 2, status ? 16 : 12);
        Component name = Component.literal(member.name()).append(member.host() ? tr("host") : Component.empty());
        int inset = status ? 22 : 18;
        text(g, font, name, x + inset, status ? y : y + 3, member.host() ? 0xFFFFFF55 : 0xFFFFFFFF, width - inset);
        if (status) text(g, font, tr("status." + member.status().name().toLowerCase(Locale.ROOT)), x + 22, y + 11,
                member.status() == LobbyState.Status.IN_WORLD ? 0xFF55FF55 : 0xFFAAAAAA, width - 22);
    }
}
