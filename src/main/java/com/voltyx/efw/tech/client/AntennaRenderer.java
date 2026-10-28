package com.voltyx.efw.tech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.voltyx.efw.EFW;
import com.voltyx.efw.tech.AntennaBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.joml.Matrix4f;

/**
 * The code safe (port of TileEntityAntennaRenderer): door and button animations, key labels with worn bloody marks on the
 * code digits 3, 6, 8 and 0, the two lock indicators and the PIN display.
 */
public class AntennaRenderer implements BlockEntityRenderer<AntennaBlockEntity> {
    private static final BedrockModel MODEL = new BedrockModel();
    private static final ResourceLocation GEO = new ResourceLocation(EFW.MOD_ID, "bedrock/antenna.geo.json");
    private static final ResourceLocation ANIM = new ResourceLocation(EFW.MOD_ID, "animations/antenna.animation.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(EFW.MOD_ID, "textures/block/antenna.png");

    private static final String[] BUTTON_NAMES = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "clear", "zero", "okay"};
    private static final String[] BUTTON_LABELS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "K"};
    /** button centres in model pixels */
    private static final float[][] BUTTON_COORDS = {
            {6.3F, 9.2F, -8.605F}, {5.0F, 9.2F, -8.605F}, {3.7F, 9.2F, -8.605F},
            {6.3F, 8.0F, -8.605F}, {5.0F, 8.0F, -8.605F}, {3.7F, 8.0F, -8.605F},
            {6.3F, 6.8F, -8.605F}, {5.0F, 6.8F, -8.605F}, {3.7F, 6.8F, -8.605F},
            {6.3F, 5.6F, -8.605F}, {5.0F, 5.6F, -8.605F}, {3.7F, 5.6F, -8.605F}
    };

    private final Font font;

    public AntennaRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(AntennaBlockEntity te, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        MODEL.load(GEO, ANIM);
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(TerminalRenderer.facingAngle(te.getBlockState().getValue(HorizontalDirectionalBlock.FACING))));

        MODEL.resetAnimations();
        float doorTime = te.getInterpolatedDoorAnimTime(partialTicks);
        if (doorTime > 0.0f) {
            MODEL.blendAnimation("dooropen", doorTime);
        }
        for (String btn : BUTTON_NAMES) {
            float t = te.getButtonAnimTime(btn);
            if (t > 0.0f) {
                MODEL.blendAnimation("press_" + btn, t);
            }
        }
        MODEL.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay);
        renderButtonLabels(pose, buffers);
        renderDisplayAndIndicators(te, pose, buffers);
        pose.popPose();
    }

    private static boolean isCodeButton(String label) {
        return "3".equals(label) || "6".equals(label) || "8".equals(label) || "0".equals(label);
    }

    private static float pressedZ(String btn, float z) {
        BedrockModel.Bone bone = MODEL.getBone(btn);
        return bone != null ? z + bone.animPos[2] / 16.0F : z;
    }

    private void renderButtonLabels(PoseStack pose, MultiBufferSource buffers) {
        // dried blood over the code digits
        VertexConsumer quads = buffers.getBuffer(TechRenderTypes.QUADS);
        Matrix4f m = pose.last().pose();
        for (int i = 0; i < BUTTON_NAMES.length; i++) {
            if (!isCodeButton(BUTTON_LABELS[i])) {
                continue;
            }
            float[] c = BUTTON_COORDS[i];
            addBloodStain(quads, m, BUTTON_LABELS[i], c[0] / 16.0F, c[1] / 16.0F, pressedZ(BUTTON_NAMES[i], c[2] / 16.0F) - 0.0006F);
        }
        for (int i = 0; i < BUTTON_NAMES.length; i++) {
            float[] c = BUTTON_COORDS[i];
            String label = BUTTON_LABELS[i];
            pose.pushPose();
            pose.translate(c[0] / 16.0F, c[1] / 16.0F, pressedZ(BUTTON_NAMES[i], c[2] / 16.0F) - 0.0010F);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            float s = 0.0034F;
            pose.scale(s, -s, s);
            Matrix4f t = pose.last().pose();
            int w = font.width(label);
            if (isCodeButton(label)) {
                draw(t, buffers, label, -w / 2 + 1, -3, 0xFF360606);
                draw(t, buffers, label, -w / 2, -4, 0xFFA42020);
            } else {
                draw(t, buffers, label, -w / 2, -4, 0xFFC8C8C8);
            }
            pose.popPose();
        }
    }

    private void draw(Matrix4f m, MultiBufferSource buffers, String text, int x, int y, int color) {
        font.drawInBatch(text, x, y, color, false, m, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }

    private static void addBloodStain(VertexConsumer buf, Matrix4f m, String label, float bx, float by, float z) {
        float p = 1.0F / 16.0F;
        switch (label) {
            case "3" -> { // smeared thumbprint
                stain(buf, m, bx - 0.35F * p, bx + 0.44F * p, by - 0.40F * p, by + 0.44F * p, z, 0.16F, 0.015F, 0.015F, 0.40F);
                stain(buf, m, bx - 0.15F * p, bx + 0.36F * p, by - 0.25F * p, by + 0.35F * p, z, 0.24F, 0.020F, 0.020F, 0.48F);
                stain(buf, m, bx - 0.05F * p, bx + 0.24F * p, by - 0.10F * p, by + 0.22F * p, z, 0.32F, 0.028F, 0.028F, 0.52F);
            }
            case "6" -> { // diagonal swipe and a trickle
                stain(buf, m, bx - 0.42F * p, bx + 0.42F * p, by - 0.42F * p, by + 0.35F * p, z, 0.15F, 0.015F, 0.015F, 0.38F);
                stain(buf, m, bx - 0.30F * p, bx + 0.28F * p, by - 0.30F * p, by + 0.18F * p, z, 0.25F, 0.022F, 0.022F, 0.46F);
                stain(buf, m, bx - 0.12F * p, bx + 0.08F * p, by - 0.58F * p, by - 0.40F * p, z, 0.18F, 0.018F, 0.018F, 0.42F);
            }
            case "8" -> { // heavy fingerprint with a drip
                stain(buf, m, bx - 0.44F * p, bx + 0.44F * p, by - 0.44F * p, by + 0.44F * p, z, 0.17F, 0.016F, 0.016F, 0.42F);
                stain(buf, m, bx - 0.30F * p, bx + 0.30F * p, by - 0.26F * p, by + 0.30F * p, z, 0.26F, 0.022F, 0.022F, 0.48F);
                stain(buf, m, bx - 0.16F * p, bx + 0.16F * p, by - 0.12F * p, by + 0.14F * p, z, 0.32F, 0.028F, 0.028F, 0.54F);
                stain(buf, m, bx - 0.06F * p, bx + 0.06F * p, by - 0.65F * p, by - 0.44F * p, z, 0.20F, 0.018F, 0.018F, 0.45F);
            }
            case "0" -> { // worn smudge with a splatter
                stain(buf, m, bx - 0.40F * p, bx + 0.38F * p, by - 0.38F * p, by + 0.42F * p, z, 0.15F, 0.015F, 0.015F, 0.38F);
                stain(buf, m, bx - 0.22F * p, bx + 0.22F * p, by - 0.18F * p, by + 0.26F * p, z, 0.25F, 0.022F, 0.022F, 0.46F);
                stain(buf, m, bx + 0.24F * p, bx + 0.42F * p, by - 0.36F * p, by - 0.22F * p, z, 0.19F, 0.018F, 0.018F, 0.40F);
            }
            default -> {
            }
        }
    }

    private static void stain(VertexConsumer buf, Matrix4f m, float x0, float x1, float y0, float y1, float z, float r, float g, float b, float a) {
        TerminalRenderer.quad(buf, m, x0, y0, x1, y1, z, r, g, b, a);
    }

    private void renderDisplayAndIndicators(AntennaBlockEntity te, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer quads = buffers.getBuffer(TechRenderTypes.QUADS);
        Matrix4f m = pose.last().pose();
        AntennaSession session = AntennaSession.get();
        boolean current = te.getBlockPos().equals(AntennaCamera.getAntennaPos());

        float red = 0.55F, green = 0.05F, blue = 0.05F;
        if (te.isUnlocked() || (current && "ACCESS".equals(session.getStatusMessage()))) {
            red = 0.05F;
            green = 0.55F;
            blue = 0.12F;
        } else if (current && "ERROR".equals(session.getStatusMessage())) {
            float flash = (float) Math.abs(Math.sin(System.currentTimeMillis() * 0.02));
            red = 0.6F;
            green = 0.1F * flash;
            blue = 0.04F;
        }
        // the two lock indicators, lit on every side
        box(quads, m, 5.98F / 16, 7.02F / 16, 12.88F / 16, 13.92F / 16, -8.32F / 16, -7.28F / 16, red, green, blue, 0.80F);
        box(quads, m, 2.98F / 16, 4.02F / 16, 12.88F / 16, 13.92F / 16, -8.32F / 16, -7.28F / 16, red, green, blue, 0.80F);

        // display: origin [3.6, 10.3, -8.5], size [2.8, 1.2, 0.9]
        float fade = current ? session.getScreenFade() : 0.0F;
        float screenZ = (-8.5F - 0.015F) / 16.0F;
        float minX = 3.6F / 16, maxX = 6.4F / 16, minY = 10.3F / 16, maxY = 11.5F / 16;
        TerminalRenderer.quad(quads, m, minX, minY, maxX, maxY, screenZ,
                0.01F + 0.03F * fade, 0.01F + 0.21F * fade, 0.02F + 0.63F * fade, 1.0F);

        if (current && fade > 0.05F) {
            pose.pushPose();
            pose.translate((minX + maxX) / 2.0F, (minY + maxY) / 2.0F, screenZ - 0.003F);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            float s = 0.0036F;
            pose.scale(s, -s, s);
            Matrix4f t = pose.last().pose();
            String status = session.getStatusMessage();
            if ("ACCESS".equals(status)) {
                draw(t, buffers, "ACCESS", -font.width("ACCESS") / 2, -4, 0xFF55FF55);
            } else if ("ERROR".equals(status)) {
                draw(t, buffers, "ERROR", -font.width("ERROR") / 2, -4, 0xFFFF5555);
            } else {
                String input = session.getInputBuffer();
                int spacing = 7;
                int startX = -(3 * spacing) / 2;
                for (int slot = 0; slot < 4; slot++) {
                    int sx = startX + slot * spacing;
                    if (slot < input.length()) {
                        String digit = String.valueOf(input.charAt(slot));
                        draw(t, buffers, digit, sx - font.width(digit) / 2, -4, 0xFFFFFFFF);
                    } else if (session.isCursorVisible()) {
                        draw(t, buffers, "_", sx - font.width("_") / 2, -4, 0xFFFFFFFF);
                    }
                }
            }
            pose.popPose();
        }
    }

    private static void box(VertexConsumer buf, Matrix4f m, float x0, float x1, float y0, float y1, float z0, float z1, float r, float g, float b, float a) {
        TerminalRenderer.quad(buf, m, x0, y0, x1, y1, z0, r, g, b, a);
        TerminalRenderer.quad(buf, m, x0, y0, x1, y1, z1, r, g, b, a);
        // top, bottom, left, right
        v(buf, m, x0, y1, z0, r, g, b, a);
        v(buf, m, x1, y1, z0, r, g, b, a);
        v(buf, m, x1, y1, z1, r, g, b, a);
        v(buf, m, x0, y1, z1, r, g, b, a);
        v(buf, m, x0, y0, z1, r, g, b, a);
        v(buf, m, x1, y0, z1, r, g, b, a);
        v(buf, m, x1, y0, z0, r, g, b, a);
        v(buf, m, x0, y0, z0, r, g, b, a);
        v(buf, m, x0, y0, z1, r, g, b, a);
        v(buf, m, x0, y0, z0, r, g, b, a);
        v(buf, m, x0, y1, z0, r, g, b, a);
        v(buf, m, x0, y1, z1, r, g, b, a);
        v(buf, m, x1, y0, z0, r, g, b, a);
        v(buf, m, x1, y0, z1, r, g, b, a);
        v(buf, m, x1, y1, z1, r, g, b, a);
        v(buf, m, x1, y1, z0, r, g, b, a);
    }

    private static void v(VertexConsumer buf, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        buf.vertex(m, x, y, z).color(r, g, b, a).endVertex();
    }
}
