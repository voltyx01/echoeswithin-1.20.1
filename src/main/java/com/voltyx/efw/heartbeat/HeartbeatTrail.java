package com.voltyx.efw.heartbeat;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;

/**
 * Ghost trail of the picture at a high pulse (mwccf HeartbeatTrailRenderer): each frame the previous picture of the world is drawn over
 * the current one with a slight shift and zoom, so moving things and the hands drag a blurred trail and the picture drifts; the trail
 * gets stronger on the beats. The frame is copied before the HUD is drawn, so the HUD stays sharp.
 */
public final class HeartbeatTrail {
    private static final float TRAIL_START_BPM = 150.0f;
    private static final float TRAIL_FULL_BPM = 180.0f;
    private static final float MAX_ALPHA = 0.6f;

    private static float strength;
    /** Nausea (a fit of the Call of the Hive), 0..1, set from outside: the trail comes regardless of the pulse and swims wider. */
    public static float nausea;
    private static float nauseaNow;
    private static int texId = -1;
    private static int texW;
    private static int texH;
    private static boolean hasFrame;

    private HeartbeatTrail() {
    }

    public static void reset() {
        strength = 0.0f;
        nauseaNow = 0.0f;
        hasFrame = false;
    }

    /** Called before the HUD is drawn. */
    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        float target = 0.0f;
        if (HeartbeatConfig.TRAIL.get() && HeartbeatConfig.ENABLED.get() && mc.screen == null && HeartbeatManager.currentBPM > TRAIL_START_BPM) {
            target = Math.min(1.0f, (HeartbeatManager.currentBPM - TRAIL_START_BPM) / (TRAIL_FULL_BPM - TRAIL_START_BPM));
        }
        nauseaNow += (nausea - nauseaNow) * 0.05f;
        if (HeartbeatConfig.TRAIL.get() && mc.screen == null) {
            target = Math.max(target, nauseaNow);
        }
        strength += (target - strength) * 0.05f;
        if (strength < 0.001f) {
            strength = 0.0f;
            hasFrame = false;
            return;
        }
        int fbW = mc.getMainRenderTarget().width;
        int fbH = mc.getMainRenderTarget().height;
        if (texId == -1 || fbW != texW || fbH != texH) {
            allocate(fbW, fbH);
            hasFrame = false;
        }
        // the previous frame first, then the blended result is stored: that is what builds the trail up
        if (hasFrame) {
            drawGhost(g, mc, strength);
        }
        GlStateManager._bindTexture(texId);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, texW, texH);
        hasFrame = true;
    }

    private static void allocate(int w, int h) {
        if (texId == -1) {
            texId = TextureUtil.generateTextureId();
        }
        GlStateManager._bindTexture(texId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, w, h, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        texW = w;
        texH = h;
    }

    private static void drawGhost(GuiGraphics g, Minecraft mc, float s) {
        float sw = mc.getWindow().getGuiScaledWidth();
        float sh = mc.getWindow().getGuiScaledHeight();
        float cx = sw / 2.0f;
        float cy = sh / 2.0f;
        float pulse = (HeartbeatManager.getHeartScale() - 1.0f) / 0.4f;
        float amp = s * (0.7f + 0.3f * pulse) * (1.0f + 2.5f * nauseaNow);
        float t = System.currentTimeMillis() / 1000.0f;
        float dx = (float) Math.sin(t * 1.3f) * 2.2f * amp;
        float dy = (float) Math.cos(t * 0.9f) * 1.6f * amp;
        float zoom = 1.0f + 0.006f * s * (0.6f + 0.4f * pulse);
        float x0 = cx + (0 - cx) * zoom + dx;
        float x1 = cx + (sw - cx) * zoom + dx;
        float y0 = cy + (0 - cy) * zoom + dy;
        float y1 = cy + (sh - cy) * zoom + dy;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texId);
        float a = MAX_ALPHA * s;
        Matrix4f m = g.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // the copy of the frame buffer has its bottom edge at v = 0
        buf.vertex(m, x0, y1, 0.0f).uv(0.0f, 0.0f).color(1.0f, 1.0f, 1.0f, a).endVertex();
        buf.vertex(m, x1, y1, 0.0f).uv(1.0f, 0.0f).color(1.0f, 1.0f, 1.0f, a).endVertex();
        buf.vertex(m, x1, y0, 0.0f).uv(1.0f, 1.0f).color(1.0f, 1.0f, 1.0f, a).endVertex();
        buf.vertex(m, x0, y0, 0.0f).uv(0.0f, 1.0f).color(1.0f, 1.0f, 1.0f, a).endVertex();
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
