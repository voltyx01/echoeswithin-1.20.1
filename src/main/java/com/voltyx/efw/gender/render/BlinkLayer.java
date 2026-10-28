package com.voltyx.efw.gender.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.voltyx.efw.gender.main.GenderPlayer;
import com.voltyx.efw.gender.main.WildfireGender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Blinking eyes (port of mwccf LayerBlinkingEyes): now and then, for 150 ms, the eyes on the face of the skin are covered by
 * eyelids, which are other pixels of the same face (the player picks where the eyes are and which pixels are the lids in the Form
 * tab of the Seven screen). While that editor is open the eyes are outlined red and the lid pixels green instead.
 *
 * <p>The front of the head on a 64x64 skin is u 8..16, v 8..16; on the model it spans x -4..4 (mirrored: u = 12 - x) and
 * y -8..0 (v = 16 + y).</p>
 */
public class BlinkLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Set by the Seven screen around drawing the own player while the eye editor is open. */
    public static boolean editing;

    private static final float FACE_X_MIN = -4.0f;
    private static final float FACE_X_MAX = 4.0f;
    private static final float FACE_Y_MIN = -8.0f;
    private static final float FACE_Y_MAX = 0.0f;
    private static final float PX = 1.0f / 16.0f;
    private static final float UV = 1.0f / 64.0f;

    public BlinkLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
            float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible() || !getParentModel().head.visible) {
            return;
        }
        if (com.voltyx.efw.tech.client.BodycamFeed.isRendering() && player == com.voltyx.efw.tech.client.BodycamFeed.getCurrentCarrier()) {
            return;
        }
        GenderPlayer gp = WildfireGender.getPlayerById(player.getUUID());
        if (gp == null || !gp.isBlinkEnabled()) {
            return;
        }
        boolean edit = editing && player == Minecraft.getInstance().player;
        // period = 4 s / frequency; the phase differs per player so not everyone blinks at once
        long period = Math.max(500L, Math.round(4000.0 / gp.getBlinkFrequency()));
        long time = System.currentTimeMillis() + Math.abs(player.getUUID().hashCode() % 2000);
        boolean blinking = time % period < 150;
        if (!blinking && !edit) {
            return;
        }

        int eyeSize = gp.getEyeSize();
        float eyeW = eyeSize == 0 ? 1.0f : 2.0f;
        float eyeH = eyeSize == 2 ? 2.0f : 1.0f;
        float dist = gp.getEyeDistance();
        float eyeY = -(float) gp.getEyeHeight();
        float leftEyeX = Math.max(FACE_X_MIN, Math.min(FACE_X_MAX - eyeW, -dist - eyeW));
        float rightEyeX = Math.max(FACE_X_MIN, Math.min(FACE_X_MAX - eyeW, dist));
        eyeY = Math.max(FACE_Y_MIN, Math.min(FACE_Y_MAX - eyeH, eyeY));
        int lidSize = Math.min(eyeSize, gp.getEyelidSize());
        float lidW = lidSize == 0 ? 1.0f : 2.0f;
        float lidH = lidSize == 2 ? 2.0f : 1.0f;
        int offX = gp.getEyelidOffsetX();
        int offY = gp.getEyelidOffsetY();

        pose.pushPose();
        getParentModel().head.translateAndRotate(pose);
        PoseStack.Pose last = pose.last();
        if (edit) {
            VertexConsumer lines = buffers.getBuffer(RenderType.debugQuads());
            float z = -4.06f * PX;
            outline(lines, last, leftEyeX, eyeY, eyeW, eyeH, z, 1.0f, 0.2f, 0.2f);
            outline(lines, last, rightEyeX, eyeY, eyeW, eyeH, z, 1.0f, 0.2f, 0.2f);
            // where the lid pixels come from: one patch (mirrored to the other eye), or two in the dual mode
            outline(lines, last, leftEyeX + offX, eyeY - offY, lidW, lidH, z, 0.2f, 1.0f, 0.2f);
            if (gp.isDualEyelid()) {
                outline(lines, last, rightEyeX + offX, eyeY - offY, lidW, lidH, z, 0.2f, 1.0f, 0.2f);
            }
        } else {
            VertexConsumer skin = buffers.getBuffer(RenderType.entityCutoutNoCull(player.getSkinTextureLocation()));
            float z = -4.02f * PX;
            // left eye: its own patch; right eye: a patch of its own (dual) or the left one, mirrored by the model's UV
            lid(skin, last, light, leftEyeX, eyeY, eyeW, eyeH, z, leftEyeX + offX, lidW, lidH, offY);
            float rightSrc = gp.isDualEyelid() ? -rightEyeX - eyeW + offX : leftEyeX + offX;
            lid(skin, last, light, rightEyeX, eyeY, eyeW, eyeH, z, rightSrc, lidW, lidH, offY);
        }
        pose.popPose();
    }

    /** An eyelid over the eye at (eyeX, eyeY) of the face, textured with the skin pixels starting at srcX (face units). */
    private static void lid(VertexConsumer vc, PoseStack.Pose last, int light, float eyeX, float eyeY, float eyeW, float eyeH, float z,
            float srcX, float lidW, float lidH, int offY) {
        float uRight = (12.0f - srcX) * UV;
        float uLeft = (12.0f - srcX - lidW) * UV;
        float srcV = 16.0f + eyeY - offY;
        float vTop = srcV * UV;
        float vBottom = (srcV + lidH) * UV;
        float x1 = eyeX * PX, x2 = (eyeX + eyeW) * PX, y1 = eyeY * PX, y2 = (eyeY + eyeH) * PX;
        vertex(vc, last, light, x2, y1, z, uRight, vTop);
        vertex(vc, last, light, x1, y1, z, uLeft, vTop);
        vertex(vc, last, light, x1, y2, z, uLeft, vBottom);
        vertex(vc, last, light, x2, y2, z, uRight, vBottom);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose last, int light, float x, float y, float z, float u, float v) {
        vc.vertex(last.pose(), x, y, z).color(1.0f, 1.0f, 1.0f, 1.0f).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(last.normal(), 0.0f, 0.0f, -1.0f).endVertex();
    }

    /** A rectangle outline of thin bars around (x, y, w, h) in face units. */
    private static void outline(VertexConsumer vc, PoseStack.Pose last, float x, float y, float w, float h, float z,
            float r, float g, float b) {
        float t = 0.12f;
        bar(vc, last, x - t, y - t, x + w + t, y, z, r, g, b);
        bar(vc, last, x - t, y + h, x + w + t, y + h + t, z, r, g, b);
        bar(vc, last, x - t, y, x, y + h, z, r, g, b);
        bar(vc, last, x + w, y, x + w + t, y + h, z, r, g, b);
    }

    private static void bar(VertexConsumer vc, PoseStack.Pose last, float x1, float y1, float x2, float y2, float z, float r, float g, float b) {
        vc.vertex(last.pose(), x1 * PX, y1 * PX, z).color(r, g, b, 1.0f).endVertex();
        vc.vertex(last.pose(), x1 * PX, y2 * PX, z).color(r, g, b, 1.0f).endVertex();
        vc.vertex(last.pose(), x2 * PX, y2 * PX, z).color(r, g, b, 1.0f).endVertex();
        vc.vertex(last.pose(), x2 * PX, y1 * PX, z).color(r, g, b, 1.0f).endVertex();
    }
}
