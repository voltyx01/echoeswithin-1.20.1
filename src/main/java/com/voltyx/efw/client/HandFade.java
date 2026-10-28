package com.voltyx.efw.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * Fade of the first person hands with what they hold (the vanilla item, a TACZ gun, anything drawn in the hand pass). The hand pass
 * is drawn into its own picture and laid over the world with the fade alpha, so the whole thing fades as one, whatever its render
 * types are.
 */
public final class HandFade {
    private static float from = 1.0f;
    private static float to = 1.0f;
    private static long start;
    private static long duration;
    private static RenderTarget target;

    private HandFade() {
    }

    /** Fades from the current value to {@code value} over {@code ms} milliseconds. */
    public static void fadeTo(float value, long ms) {
        from = alpha();
        to = value;
        start = System.nanoTime();
        duration = Math.max(1L, ms) * 1_000_000L;
    }

    public static void set(float value) {
        from = value;
        to = value;
        duration = 0L;
    }

    public static float alpha() {
        if (duration <= 0L) {
            return to;
        }
        float t = Math.min(1.0f, (System.nanoTime() - start) / (float) duration);
        if (t >= 1.0f) {
            duration = 0L;
            return to;
        }
        float s = t * t * (3.0f - 2.0f * t);
        return from + (to - from) * s;
    }

    private static boolean capturing;
    private static float capturedAlpha;

    /** Before the hand pass: a faded pass is drawn into its own picture (an invisible one is drawn there and never shown). */
    public static void begin() {
        capturing = false;
        float a = alpha();
        if (a >= 0.999f) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (target == null || target.width != main.width || target.height != main.height) {
            if (target != null) {
                target.destroyBuffers();
            }
            target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        }
        target.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(false);
        capturing = true;
        capturedAlpha = a;
    }

    /** After the hand pass: back to the screen, and the picture is laid over it with the fade alpha. */
    public static void end() {
        if (!capturing) {
            return;
        }
        capturing = false;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        main.bindWrite(false);
        if (capturedAlpha > 0.001f) {
            composite(capturedAlpha, main.width, main.height);
        }
    }

    private static void composite(float a, int w, int h) {
        Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting oldSorting = RenderSystem.getVertexSorting();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0f, w, h, 0.0f, -1.0f, 1.0f), VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // the picture of a frame buffer starts at the bottom left
        b.vertex(0.0, h, 0.0).uv(0.0f, 0.0f).color(1.0f, 1.0f, 1.0f, a).endVertex();
        b.vertex(w, h, 0.0).uv(1.0f, 0.0f).color(1.0f, 1.0f, 1.0f, a).endVertex();
        b.vertex(w, 0.0, 0.0).uv(1.0f, 1.0f).color(1.0f, 1.0f, 1.0f, a).endVertex();
        b.vertex(0.0, 0.0, 0.0).uv(0.0f, 1.0f).color(1.0f, 1.0f, 1.0f, a).endVertex();
        BufferUploader.drawWithShader(b.end());
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
    }
}
