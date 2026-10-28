package com.voltyx.efw.eyes.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.voltyx.efw.EFW;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;

/**
 * The eyes of the player. Dying they close (at once after a TACZ bullet in the head, an explosion or a heavy blow, the sound cut off
 * to its echo, see DeathSounds) and the death screen comes slowly out of the black. Opening them, after the respawn and on entering
 * a world or a server: the screen starts black, the lids part (black halves going up and down), squint shut a little once, then
 * open for good, while the blurred world comes into focus. From the death until they are fully open the sounds come muffled and
 * echoing (with Sound Physics Remastered, see SprEyesMuffleMixin).
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class EyesOpen {
    /** Shut, then the stages of the lids: open to a slit, squint, open fully (ms from the start). */
    private static final long SHUT = 200L, SLIT = 450L, SQUINT = 600L, OPEN = 1100L;
    /** The blur lasts past the lids: sharp at this time. */
    private static final long FOCUS = 1700L;
    /** Blur radius at its strongest, in screen pixels. */
    private static final float MAX_BLUR = 14.0f;
    private static final int TAPS = 12;
    /** Back from death in the worn track jacket: the lids just open, quickly, no slit or squint (ms). */
    private static final long FAST_OPEN = 250L;

    private static boolean pending;
    private static long start = -1L;
    /** 0..1, read by the sound thread. */
    private static volatile float muffle;

    private static int texId = -1;
    private static int texW, texH;

    /** Dying: the lids close over this time, stay shut, then the death screen comes out of the black. */
    private static final long CLOSE = 380L, DARK = 250L, DARK_INSTANT = 450L, SCREEN_FADE = 800L;
    /** After an instant death the cut sounds ring out as an echo this long, then everything stops. */
    private static final long ECHO = 1800L;
    private static long deathStart = -1L;
    private static boolean deathInstant;
    private static long stopSoundsAt = -1L;
    /** The player died in the worn track jacket: they come back at once, awake (see {@link #awake()}). */
    private static boolean awakeOnRespawn;
    /** The current opening is the quick one ({@link #awake()}). */
    private static boolean fast;

    /** A blink ({@link #blink()}): the lids close, stay shut, open again (ms). */
    private static final long BLINK_CLOSE = 170L, BLINK_SHUT = 230L, BLINK_OPEN = 280L;
    private static long blinkStart = -1L;

    private EyesOpen() {
    }

    /**
     * The player died (told by the server before the death screen opens); instant: a TACZ headshot, an explosion, a heavy blow; awake:
     * they died in the worn track jacket (the server looks before a corpse mod takes the Curios away).
     */
    public static void died(boolean instant, boolean awake) {
        EFW.LOGGER.info("[Eyes] died, instant: {}, in the jacket: {}", instant, awake);
        deathStart = Util.getMillis();
        deathInstant = instant;
        awakeOnRespawn = awake;
        pending = false;
        start = -1L;
        muffle = 1.0f;
        if (instant) {
            DeathSounds.cut();
            stopSoundsAt = Util.getMillis() + ECHO;
        }
    }

    private static boolean dying() {
        return deathStart >= 0L;
    }

    /** No packet came (the death screen opened anyway): the plain closing of the eyes. */
    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening e) {
        if (e.getNewScreen() instanceof DeathScreen && !dying()) {
            Minecraft mc = Minecraft.getInstance();
            died(false, mc.player != null && com.voltyx.efw.gear.WornOlympicItem.wears(mc.player));
        }
    }

    /**
     * Under the death screen: while the lids close over the world (shut at once after an instant death) the screen is not drawn at all;
     * once they are shut it comes on black, out of the black that thins out over it (onScreenPost).
     */
    @SubscribeEvent
    public static void onScreenPre(ScreenEvent.Render.Pre e) {
        if (!(e.getScreen() instanceof DeathScreen) || !dying()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = e.getGuiGraphics();
        drawLids(g, mc, deathOpen(), 900.0f);
        if (Util.getMillis() - deathStart < shutAt()) {
            e.setCanceled(true);
            return;
        }
        // over the HUD, but the death screen, its text and buttons, go over them
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    /** Over the death screen: black that thins out, so the screen comes slowly. */
    @SubscribeEvent
    public static void onScreenPost(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof DeathScreen) || !dying()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long t = Util.getMillis() - deathStart - shutAt();
        if (t < 0L) {
            return; // the screen was not drawn (Post comes even when Pre is cancelled): the lids are still closing over the world
        }
        float cover = 1.0f - smooth(t / (float) SCREEN_FADE);
        if (cover <= 0.0f) {
            return;
        }
        GuiGraphics g = e.getGuiGraphics();
        g.pose().pushPose();
        g.pose().translate(0.0f, 0.0f, 900.0f);
        g.fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), Math.round(cover * 255.0f) << 24);
        g.pose().popPose();
    }

    /** From the death on, when the screen starts to come: the lids shut and a moment of black. */
    private static long shutAt() {
        return deathInstant ? DARK_INSTANT : CLOSE + DARK;
    }

    /** How open the eyes still are while dying. */
    private static float deathOpen() {
        return deathInstant ? 0.0f : 1.0f - easeIn((Util.getMillis() - deathStart) / (float) CLOSE);
    }

    private static float easeIn(float x) {
        x = Math.max(0.0f, Math.min(1.0f, x));
        return x * x;
    }

    /** Starts as soon as nothing covers the world (the death or loading screen gone). */
    public static void trigger() {
        if (stopSoundsAt >= 0L) {
            // respawned before the echo of an instant death died out: it ends now, not over the new life
            stopSoundsAt = -1L;
            DeathSounds.stopCut();
        }
        pending = true;
        start = -1L;
        deathStart = -1L;
        fast = false;
        muffle = 1.0f;
    }

    /** One blink, starting now; what should change unseen can change once {@link #blinkShut()} says the lids are closed. */
    public static void blink() {
        blinkStart = Util.getMillis();
    }

    /** The lids of a blink are fully closed now (or have been and are opening again). */
    public static boolean blinkShut() {
        return blinkStart >= 0L && Util.getMillis() - blinkStart >= BLINK_CLOSE;
    }

    /** How open the eyes are in the blink, 0 shut .. 1 open; 1 when there is none. */
    private static float blinkOpen() {
        if (blinkStart < 0L) {
            return 1.0f;
        }
        long t = Util.getMillis() - blinkStart;
        if (t < BLINK_CLOSE) {
            return 1.0f - easeIn(t / (float) BLINK_CLOSE);
        }
        t -= BLINK_CLOSE;
        if (t < BLINK_SHUT) {
            return 0.0f;
        }
        t -= BLINK_SHUT;
        if (t < BLINK_OPEN) {
            return smooth(t / (float) BLINK_OPEN);
        }
        blinkStart = -1L;
        return 1.0f;
    }

    /** From the death, or the start of opening, until the eyes are fully open. */
    public static boolean eyesClosed() {
        return pending || dying() || (start >= 0L && Util.getMillis() - start < (fast ? FAST_OPEN : OPEN));
    }

    /** How muffled the sounds starting now are, 0 not at all .. 1 fully. */
    public static float muffle() {
        return muffle;
    }

    /**
     * Back from death in the worn track jacket: the eyes just open, quickly, without the slit and the squint, the sounds are not
     * muffled, and the sound of coming back plays.
     */
    public static void awake() {
        Minecraft mc = Minecraft.getInstance();
        if (stopSoundsAt >= 0L) {
            stopSoundsAt = -1L;
            DeathSounds.stopCut();
        }
        pending = true;
        start = -1L;
        deathStart = -1L;
        fast = true;
        muffle = 0.0f;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(com.voltyx.efw.init.EfwContent.RESPAWN.get(), 1.0f, 1.0f));
    }

    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone e) {
        if (e.getOldPlayer().isDeadOrDying()) {
            if (awakeOnRespawn) {
                awake();
            } else {
                trigger();
            }
            awakeOnRespawn = false;
        }
    }

    /** Every time the player enters a world or a server. */
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn e) {
        trigger();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        awakeOnRespawn = false;
        fast = false;
        pending = false;
        start = -1L;
        deathStart = -1L;
        stopSoundsAt = -1L;
        blinkStart = -1L;
        muffle = 0.0f;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (stopSoundsAt >= 0L && Util.getMillis() >= stopSoundsAt) {
            stopSoundsAt = -1L;
            DeathSounds.stopCut();
        }
        // the respawn ends it (trigger); a death screen that never came, or was left some other way, too
        if (dying() && !(mc.screen instanceof DeathScreen) && Util.getMillis() - deathStart > 3000L) {
            deathStart = -1L;
        }
        if (blinkStart >= 0L && Util.getMillis() - blinkStart > BLINK_CLOSE + BLINK_SHUT + BLINK_OPEN) {
            blinkStart = -1L; // also when no HUD was drawn to end it (F1)
        }
        long t = elapsed(mc);
        muffle = !fast && (pending || dying() || (t >= 0L && t < OPEN)) ? 1.0f : 0.0f;
    }

    private static long elapsed(Minecraft mc) {
        if (pending && mc.screen == null && mc.level != null) {
            pending = false;
            start = Util.getMillis();
        }
        if (start < 0L) {
            return -1L;
        }
        long t = Util.getMillis() - start;
        if (t > (fast ? FAST_OPEN : FOCUS)) {
            start = -1L;
            return -1L;
        }
        return t;
    }

    /** How far open the lids are, 0 shut .. 1 open. */
    private static float openness(long t) {
        if (fast) {
            return smooth(t / (float) FAST_OPEN);
        }
        if (t < SHUT) {
            return 0.0f;
        }
        if (t < SLIT) {
            return 0.45f * easeOut((t - SHUT) / (float) (SLIT - SHUT));
        }
        if (t < SQUINT) {
            return 0.45f - 0.3f * smooth((t - SLIT) / (float) (SQUINT - SLIT));
        }
        if (t < OPEN) {
            return 0.15f + 0.85f * smooth((t - SQUINT) / (float) (OPEN - SQUINT));
        }
        return 1.0f;
    }

    private static float blurRadius(long t) {
        if (fast) {
            return MAX_BLUR * 0.4f * (1.0f - smooth(t / (float) FAST_OPEN));
        }
        if (t < SQUINT) {
            return MAX_BLUR;
        }
        return MAX_BLUR * (1.0f - smooth((t - SQUINT) / (float) (FOCUS - SQUINT)));
    }

    private static float easeOut(float x) {
        return 1.0f - (1.0f - x) * (1.0f - x);
    }

    private static float smooth(float x) {
        x = Math.max(0.0f, Math.min(1.0f, x));
        return x * x * (3.0f - 2.0f * x);
    }

    /** The blur goes on the world before the HUD is drawn. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHudPre(RenderGuiEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (dying()) {
            // the world goes dim as the eyes close (here, not under the death screen: a copy of the frame taken there came out black)
            float open = deathOpen();
            if (open > 0.0f && open < 1.0f) {
                blur(e.getGuiGraphics(), mc, MAX_BLUR * (1.0f - open));
            }
            return;
        }
        long t = elapsed(mc);
        if (t < SHUT) {
            return; // nothing, or the lids are shut anyway
        }
        float radius = blurRadius(t);
        if (radius >= 0.5f) {
            blur(e.getGuiGraphics(), mc, radius);
        }
    }

    /** The lids go over everything, the HUD too. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHudPost(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (dying()) {
            if (!(mc.screen instanceof DeathScreen)) {
                drawLids(e.getGuiGraphics(), mc, deathOpen(), 900.0f); // before the death screen is up; then it draws them itself
            }
            return;
        }
        if (pending && mc.screen != null) {
            return;
        }
        long t = pending ? 0L : start < 0L ? -1L : Util.getMillis() - start;
        float open = t < 0L ? 1.0f : openness(t);
        drawLids(e.getGuiGraphics(), mc, Math.min(open, blinkOpen()), 900.0f);
    }

    /** The two black halves, apart by how open the eyes are (0 shut .. 1 open), with soft edges; z: their depth among the GUI. */
    private static void drawLids(GuiGraphics g, Minecraft mc, float open, float z) {
        if (open >= 1.0f) {
            return;
        }
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        float half = h / 2.0f;
        // a soft edge on each lid, pushed out of the screen once they are open
        float band = h * 0.14f;
        float edge = (half + band) * (1.0f - open) - band;
        g.pose().pushPose();
        g.pose().translate(0.0f, 0.0f, z);
        int top = Math.round(edge);
        int soft = Math.round(edge + band);
        if (top > 0) {
            g.fill(0, 0, w, top, 0xFF000000);
            g.fill(0, h - top, w, h, 0xFF000000);
        }
        g.fillGradient(0, top, w, soft, 0xFF000000, 0x00000000);
        g.fillGradient(0, h - soft, w, h - top, 0x00000000, 0xFF000000);
        g.pose().popPose();
    }

    /**
     * The picture of the world, copied, drawn again shifted around in two rings, each copy weighted so the result is their average: a
     * blur that stays within the GUI pass (no post shader, which flickered here).
     */
    private static void blur(GuiGraphics g, Minecraft mc, float radius) {
        int fbW = mc.getMainRenderTarget().width;
        int fbH = mc.getMainRenderTarget().height;
        if (texId == -1 || fbW != texW || fbH != texH) {
            allocate(fbW, fbH);
        }
        GlStateManager._bindTexture(texId);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, texW, texH);

        float sw = mc.getWindow().getGuiScaledWidth();
        float sh = mc.getWindow().getGuiScaledHeight();
        float r = radius / (float) mc.getWindow().getGuiScale();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texId);
        Matrix4f m = g.pose().last().pose();
        for (int k = 1; k <= TAPS; k++) {
            // two rings of six, the inner one turned half a step
            boolean inner = k > TAPS / 2;
            double angle = (k % (TAPS / 2)) * (Math.PI * 2.0 / (TAPS / 2)) + (inner ? Math.PI / (TAPS / 2) : 0.0);
            float d = inner ? r * 0.5f : r;
            float dx = (float) Math.cos(angle) * d;
            float dy = (float) Math.sin(angle) * d;
            float a = 1.0f / (k + 1);
            BufferBuilder buf = Tesselator.getInstance().getBuilder();
            buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            // the copy of the frame buffer has its bottom edge at v = 0
            buf.vertex(m, dx, sh + dy, 0.0f).uv(0.0f, 0.0f).color(1.0f, 1.0f, 1.0f, a).endVertex();
            buf.vertex(m, sw + dx, sh + dy, 0.0f).uv(1.0f, 0.0f).color(1.0f, 1.0f, 1.0f, a).endVertex();
            buf.vertex(m, sw + dx, dy, 0.0f).uv(1.0f, 1.0f).color(1.0f, 1.0f, 1.0f, a).endVertex();
            buf.vertex(m, dx, dy, 0.0f).uv(0.0f, 1.0f).color(1.0f, 1.0f, 1.0f, a).endVertex();
            BufferUploader.drawWithShader(buf.end());
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
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
}
