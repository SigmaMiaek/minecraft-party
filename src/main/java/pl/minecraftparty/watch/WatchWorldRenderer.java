package pl.minecraftparty.watch;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Real browser texture on a depth-tested world-space quad, without registering server blocks/entities. */
public final class WatchWorldRenderer {
    private WatchWorldRenderer() { }
    public static void register(WatchManager watch) {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            if (!watch.sameDimension()) return;
            var state = watch.state(); var placement = state.placement();
            var camera = context.levelState().cameraRenderState.pos;
            if (camera.distanceToSqr(placement.x(), placement.y(), placement.z()) > 64 * 64) return;
            var texture = watch.texture(); if (texture == null) return;
            PoseStack pose = context.poseStack(); pose.pushPose();
            pose.translate(placement.x() - camera.x, placement.y() - camera.y, placement.z() - camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(placement.yaw()));
            float w = placement.width() / 2, h = placement.height() / 2;
            context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(texture), (matrix, vertices) -> {
                vertex(vertices, matrix, -w, h, 0, 0); vertex(vertices, matrix, -w, -h, 0, 1);
                vertex(vertices, matrix, w, -h, 1, 1); vertex(vertices, matrix, w, h, 1, 0);
            });
            pose.popPose();
        });
    }
    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float u, float v) {
        vertices.addVertex(pose, x, y, 0).setColor(0xFFFFFFFF).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
    }
}
