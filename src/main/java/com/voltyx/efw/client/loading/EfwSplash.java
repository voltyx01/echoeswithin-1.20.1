package com.voltyx.efw.client.loading;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.InputStream;

/**
 * The early loading screen of mwccf (SplashRenderer): the splash picture stretched over the window, with the square "snake"
 * running in the bottom right corner. It covers
 * <ul>
 * <li>the loading of the mods, drawn straight with GL from the periodic tick of the mod loader (no shaders exist yet), and</li>
 * <li>the loading of the resources and every later resource reload, drawn over the loading overlays.</li>
 * </ul>
 * The very first window of Forge, before Minecraft takes it over, is drawn by Forge's own code, which mods cannot reach.
 */
public final class EfwSplash {
    private static final String PATH = "/assets/efw/textures/gui/splash.png";
    private static final ResourceLocation OVERLAY_TEXTURE = new ResourceLocation("efw", "splash_dynamic");

    private static boolean earlyFailed;
    private static long lastEarlyFrame;
    private static int earlyTexture;
    private static int earlyFramebuffer;
    private static int imageW;
    private static int imageH;
    private static boolean overlayReady;

    private EfwSplash() {
    }

    /**
     * The EFW early window (the separate efw-splash jar, earlyWindowProvider = "efwsplash") draws the whole early loading itself; the
     * mod then only covers the later resource reloads, so the picture does not change size between the phases.
     */
    public static boolean providerActive() {
        return "true".equals(System.getProperty("efw.splash.provider"));
    }

    private static NativeImage readImage() throws Exception {
        try (InputStream in = EfwSplash.class.getResourceAsStream(PATH)) {
            if (in == null) {
                throw new IllegalStateException("splash picture missing: " + PATH);
            }
            NativeImage img = NativeImage.read(in);
            imageW = img.getWidth();
            imageH = img.getHeight();
            return img;
        }
    }

    // ------------------------------------------------------------------------------------------------ loading of the mods

    /** Replaces the periodic tick of the Forge window while the mods load (main thread, the window belongs to Minecraft). */
    public static void earlyTick() {
        long window;
        try {
            window = Minecraft.getInstance().getWindow().getWindow();
        } catch (Throwable t) {
            return;
        }
        GLFW.glfwPollEvents();
        long now = System.currentTimeMillis();
        if (earlyFailed || now - lastEarlyFrame < 16L) {
            return;
        }
        lastEarlyFrame = now;
        try {
            renderEarly(window, now);
        } catch (Throwable t) {
            earlyFailed = true;
        }
    }

    private static void renderEarly(long window, long now) throws Exception {
        if (earlyTexture == 0) {
            try (NativeImage img = readImage()) {
                earlyTexture = TextureUtil.generateTextureId();
                TextureUtil.prepareImage(earlyTexture, imageW, imageH);
                img.upload(0, 0, 0, false);
            }
            GlStateManager._bindTexture(0);
            earlyFramebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, earlyFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, earlyTexture, 0);
        }
        int[] fw = new int[1];
        int[] fh = new int[1];
        GLFW.glfwGetFramebufferSize(window, fw, fh);
        int w = fw[0];
        int h = fh[0];
        if (w <= 0 || h <= 0) {
            return;
        }
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
        GL11.glViewport(0, 0, w, h);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        // the picture over the whole window; the first row of the texture is the top of the picture, the window counts from below
        int[] r = fit(w, h);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, earlyFramebuffer);
        GL30.glBlitFramebuffer(0, 0, imageW, imageH, r[0], h - r[1], r[0] + r[2], h - r[1] - r[3], GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

        // the snake: scissored clears, the colour already mixed with the black under it
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        snake(w, h, now, (x, y, sw, sh, alpha) -> {
            float v = alpha / 255.0f;
            GL11.glScissor(x, h - y - sh, sw, sh);
            GL11.glClearColor(v, v, v, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        });
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GLFW.glfwSwapBuffers(window);
    }

    private static void disposeEarly() {
        if (earlyFramebuffer != 0) {
            GL30.glDeleteFramebuffers(earlyFramebuffer);
            earlyFramebuffer = 0;
        }
        if (earlyTexture != 0) {
            TextureUtil.releaseTextureId(earlyTexture);
            earlyTexture = 0;
        }
    }

    // ------------------------------------------------------------------------------------------------ overlays

    /** Drawn over a loading overlay; {@code alpha} is the fade of that overlay. Drippy Loading Screen owns that overlay when present. */
    public static void renderOverlay(GuiGraphics g, float alpha) {
        if (alpha <= 0.002f || ModList.get() != null && ModList.get().isLoaded("drippyloadingscreen")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!overlayReady) {
            try {
                disposeEarly();
                mc.getTextureManager().register(OVERLAY_TEXTURE, new DynamicTexture(readImage()));
                overlayReady = true;
            } catch (Throwable t) {
                return;
            }
        }
        int a = Math.round(alpha * 255.0f) << 24;
        int gw = g.guiWidth();
        int gh = g.guiHeight();
        g.fill(RenderType.guiOverlay(), 0, 0, gw, gh, a);

        // everything else in window pixels, like the early frames, so the picture and the snake do not jump at the change
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        float inv = (float) (1.0 / mc.getWindow().getGuiScale());
        g.pose().pushPose();
        g.pose().scale(inv, inv, 1.0f);
        int[] r = fit(w, h);
        RenderSystem.enableBlend();
        g.setColor(1.0f, 1.0f, 1.0f, alpha);
        g.blit(OVERLAY_TEXTURE, r[0], r[1], r[2], r[3], 0.0f, 0.0f, imageW, imageH, imageW, imageH);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        snake(w, h, System.currentTimeMillis(), (x, y, sw, sh, dotAlpha) ->
                g.fill(RenderType.guiOverlay(), x, y, x + sw, y + sh, (Math.round(dotAlpha * alpha) << 24) | 0xFFFFFF));
        g.pose().popPose();
    }

    // ------------------------------------------------------------------------------------------------ shared layout

    /** {x, y, width, height} of the picture in the window, from the top left: stretched over the whole window, no black bars. */
    private static int[] fit(int w, int h) {
        return new int[]{0, 0, w, h};
    }

    private interface Rect {
        void draw(int x, int y, int w, int h, int alpha);
    }

    /** mwccf's snake: a faint square track and 24 dots running round it, the tail fading out (window pixels, from the top left). */
    private static void snake(int screenW, int screenH, long time, Rect out) {
        int size = Math.min(40, Math.max(20, Math.min(screenW, screenH) / 20));
        int margin = 64;
        int cx = screenW - margin;
        int cy = screenH - margin;
        out.draw(cx - size, cy - size, size * 2, 2, 0x55);
        out.draw(cx + size - 2, cy - size, 2, size * 2, 0x55);
        out.draw(cx - size, cy + size - 2, size * 2, 2, 0x55);
        out.draw(cx - size, cy - size, 2, size * 2, 0x55);
        int perimeter = size * 8;
        int pos = (int) ((time / 10) % perimeter);
        int dotCount = 24;
        int dot = Math.max(2, size / 7);
        for (int i = dotCount - 1; i >= 0; i--) {
            int p = (pos - i * 2 + perimeter * 100) % perimeter;
            int dx;
            int dy;
            if (p < size * 2) {
                dx = -size + p;
                dy = -size;
            } else if (p < size * 4) {
                dx = size;
                dy = -size + (p - size * 2);
            } else if (p < size * 6) {
                dx = size - (p - size * 4);
                dy = size;
            } else {
                dx = -size;
                dy = size - (p - size * 6);
            }
            int alpha = (int) (255.0f * (1.0f - i / (float) dotCount));
            out.draw(cx + dx - dot, cy + dy - dot, dot * 2, dot * 2, alpha);
        }
    }
}
