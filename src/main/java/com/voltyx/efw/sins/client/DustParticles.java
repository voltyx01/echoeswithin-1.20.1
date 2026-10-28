package com.voltyx.efw.sins.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Ash dust drifting up and to the right behind the SEVEN and inspect screens (port of InspectDustManager). */
public class DustParticles {
    private static final ResourceLocation PARTICLES_TEXTURE = new ResourceLocation("minecraft", "textures/particle/generic_0.png");
    private static final Random RANDOM = new Random();
    private static final int DUST_COUNT = 65;

    private static class Dust {
        float x, y, vx, vy, size, alpha, baseAlpha, phase, phaseSpeed;

        Dust(int width, int height) {
            respawn(width, height, true);
        }

        void respawn(int width, int height, boolean randomInitial) {
            if (randomInitial) {
                x = RANDOM.nextFloat() * (width + 100) - 50;
                y = RANDOM.nextFloat() * (height + 50);
            } else if (RANDOM.nextBoolean()) {
                // from the bottom
                x = RANDOM.nextFloat() * (width + 50) - 50;
                y = height + 10.0f;
            } else {
                // from the left border
                x = -15.0f;
                y = RANDOM.nextFloat() * (height + 50);
            }
            vx = 8.0f + RANDOM.nextFloat() * 16.0f;
            vy = -(10.0f + RANDOM.nextFloat() * 18.0f);
            size = 2.5f + RANDOM.nextFloat() * 4.0f;
            baseAlpha = 0.25f + RANDOM.nextFloat() * 0.45f;
            alpha = baseAlpha;
            phase = RANDOM.nextFloat() * 6.28f;
            phaseSpeed = 1.0f + RANDOM.nextFloat() * 2.0f;
        }

        void update(float deltaSec, int width, int height) {
            phase += phaseSpeed * deltaSec;
            float sway = (float) Math.sin(phase) * 5.0f;
            x += (vx + sway) * deltaSec;
            y += vy * deltaSec;
            alpha = baseAlpha * (0.7f + 0.3f * (float) Math.cos(phase)); // breathing
            if (y < -15 || x > width + 25) {
                respawn(width, height, false);
            }
        }
    }

    private final List<Dust> particles = new ArrayList<>();
    private long lastTime = 0;

    public void init(int width, int height) {
        particles.clear();
        for (int i = 0; i < DUST_COUNT; i++) {
            particles.add(new Dust(width, height));
        }
        lastTime = System.currentTimeMillis();
    }

    public void updateAndRender(GuiGraphics g, int width, int height) {
        updateAndRender(g.pose().last().pose(), width, height);
    }

    /** Same with an explicit matrix, for drawing outside a screen (e.g. behind the first person hand). */
    public void updateAndRender(Matrix4f mat, int width, int height) {
        long now = System.currentTimeMillis();
        float deltaSec = lastTime == 0 ? 0.016f : Math.min((now - lastTime) / 1000.0f, 0.1f);
        lastTime = now;
        if (particles.isEmpty()) {
            init(width, height);
        }
        for (Dust p : particles) {
            p.update(deltaSec, width, height);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, PARTICLES_TEXTURE);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // the whole sprite of the vanilla 1.20.1 ash particle (ash.json -> generic_0)
        float u1 = 0f, v1 = 0f, u2 = 1f, v2 = 1f;
        for (Dust p : particles) {
            float s = p.size;
            int a = (int) (Math.max(0.0f, Math.min(1.0f, p.alpha)) * 255);
            buffer.vertex(mat, p.x, p.y + s, 0).uv(u1, v2).color(220, 225, 230, a).endVertex();
            buffer.vertex(mat, p.x + s, p.y + s, 0).uv(u2, v2).color(220, 225, 230, a).endVertex();
            buffer.vertex(mat, p.x + s, p.y, 0).uv(u2, v1).color(220, 225, 230, a).endVertex();
            buffer.vertex(mat, p.x, p.y, 0).uv(u1, v1).color(220, 225, 230, a).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
