package com.voltyx.efw.client.hearts;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.LsoHearts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The heart bar in the spirit of Colorful Hearts: one row of ten hearts, the health over twenty points in coloured layers on top of
 * each other, absorption and the LSO shield in rows of their own above it, the LSO broken hearts at the end of the row. It replaces
 * the vanilla bar and LSO's health_overhaul overlay. The number of hearts follows the max health at once (vanilla keeps the old
 * count for a second after a change, so a heart breaking looked like one added).
 * <p>
 * The hearts are the game's own: the containers and the blinking hearts come from {@code icons.png} as they are, the coloured ones
 * are the vanilla red heart turned grey once (when the resources load) and tinted with any colour. A resource pack changes them too.
 */
public final class HeartsHud {
    public static final ResourceLocation ID = new ResourceLocation(EFW.MOD_ID, "hearts");
    private static final ResourceLocation LSO_HEALTH = new ResourceLocation("legendarysurvivaloverhaul", "health_overhaul");
    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");

    private static final int STEP = 8;
    private static final int ROW = 10;
    private static final int PER_ROW = 10;

    // ---- vanilla icons.png (1.20.1, 256x256 grid): x of the heart sprites, y of the normal and hardcore rows ----------------
    private static final int U_CONTAINER = 16;
    private static final int U_CONTAINER_BLINK = 25;
    private static final int U_FULL = 52;
    private static final int U_HALF = 61;
    private static final int U_BLINK_FULL = 70;
    private static final int U_BLINK_HALF = 79;
    private static final int V_NORMAL = 0;
    private static final int V_HARDCORE = 45;

    /** Pixels of the crack of a broken heart, drawn over the grey heart. */
    private static final int[][] CRACK = {{4, 2}, {3, 3}, {4, 4}, {5, 5}, {4, 6}};

    // ---- colours of the layers ----------------------------------------------------------------------------------------------
    private static final int[] LAYERS = {0xE8262B, 0xF07A2A, 0xF0CC2E, 0x63C83E, 0x33C0C4, 0x4078E8, 0x9550E6, 0xE652B4};
    private static final int[] POISON = {0x94AE2C, 0x67801C};
    private static final int[] WITHER = {0x4A4A4A, 0x2A2A2A};
    private static final int[] FROZEN = {0xA8DDF7, 0x6AB3DE};
    private static final int[] ABSORPTION = {0xF2C232, 0xFAE184, 0xD4931F};
    private static final int[] SHIELD = {0x9CB4CC, 0x6A89A6, 0xC4D6E6};
    private static final int BROKEN = 0x6E6868;
    private static final int CRACK_COLOR = 0xFF120707;

    // ---- blinking after a change ------------------------------------------------------------------------------------------
    private static float lastHealth = -1.0f;
    private static float lastMax = -1.0f;
    private static int blinkTicks;
    private static int ghostPoints;
    private static final RandomSource SHAKE = RandomSource.create();

    private HeartsHud() {
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent e) {
            e.registerAbove(VanillaGuiOverlay.PLAYER_HEALTH.id(), ID.getPath(), HeartsHud::render);
        }

        /** New resources (a pack switched on or off): the grey heart is made again from the new icons. */
        @SubscribeEvent
        public static void onReloadListeners(RegisterClientReloadListenersEvent e) {
            e.registerReloadListener((ResourceManagerReloadListener) rm -> Tint.invalidate());
        }
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {
        }

        /** Ours replaces the vanilla bar and LSO's bar (their broken and shield hearts are drawn here). */
        @SubscribeEvent
        public static void onOverlayPre(RenderGuiOverlayEvent.Pre e) {
            if (!HeartsConfig.ENABLED.get()) {
                return;
            }
            ResourceLocation id = e.getOverlay().id();
            if (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) || id.equals(LSO_HEALTH)) {
                e.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            Player p = Minecraft.getInstance().player;
            if (p == null) {
                lastHealth = -1.0f;
                lastMax = -1.0f;
                return;
            }
            if (blinkTicks > 0) {
                blinkTicks--;
            }
            float hp = p.getHealth();
            float max = p.getMaxHealth();
            // LSO breaking a heart (a limb badly hurt, applied up to a second after the blow) lowers the max health and cuts the health
            // down to it: that is no new hit, so it does not blink as one
            boolean cutToMax = lastMax > 0.0f && max < lastMax - 0.01f && hp < lastHealth && hp >= max - 0.01f
                    && lastHealth - hp <= lastMax - max + 0.01f;
            lastMax = max;
            if (cutToMax) {
                lastHealth = hp;
                return;
            }
            if (lastHealth >= 0.0f && Math.abs(hp - lastHealth) > 0.01f) {
                int blink = HeartsConfig.BLINK_TICKS.get();
                if (hp < lastHealth) {
                    // the lost part stays white while it blinks (keeps the highest point of several quick hits)
                    ghostPoints = blinkTicks > 0 ? Math.max(ghostPoints, Mth.ceil(lastHealth)) : Mth.ceil(lastHealth);
                    blinkTicks = blink;
                } else {
                    ghostPoints = 0;
                    blinkTicks = blink / 2;
                }
            }
            lastHealth = hp;
        }
    }

    // ---- drawing ---------------------------------------------------------------------------------------------------------

    /** What every heart of this frame shares. */
    private record Look(int v, int containerU, boolean blinking, boolean shake, int wave) {
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!HeartsConfig.ENABLED.get() || mc.options.hideGui || !gui.shouldDrawSurvivalElements()
                || !(mc.getCameraEntity() instanceof Player player)) {
            return;
        }
        float health = player.getHealth();
        float max = Math.max(1.0f, player.getMaxHealth());
        float absorption = player.getAbsorptionAmount();
        float shield = LsoHearts.shield(player);
        int broken = Math.min(20, LsoHearts.broken(player));

        int left = width / 2 - 91;
        int bottom = height - gui.leftHeight;
        int tick = gui.getGuiTicks();
        int slots = Math.min(PER_ROW, Mth.ceil(max / 2.0f));
        int healthPoints = Mth.ceil(health);
        boolean blinking = blinkTicks > 0 && (blinkTicks / 3) % 2 == 1;
        Look look = new Look(player.level().getLevelData().isHardcore() ? V_HARDCORE : V_NORMAL,
                blinking ? U_CONTAINER_BLINK : U_CONTAINER, blinking,
                HeartsConfig.LOW_HEALTH_SHAKE.get() && health + absorption <= 4.0f,
                HeartsConfig.REGEN_WAVE.get() && player.hasEffect(MobEffects.REGENERATION) ? tick % Mth.ceil(max + 5.0f) : -1);
        SHAKE.setSeed(tick * 312871L);
        int[] palette = player.hasEffect(MobEffects.POISON) ? POISON : player.hasEffect(MobEffects.WITHER) ? WITHER
                : player.isFullyFrozen() ? FROZEN : LAYERS;

        RenderSystem.enableBlend();
        // the health row, then the broken hearts after it (wrapping upwards when the row is full)
        int ghost = blinkTicks > 0 && ghostPoints > healthPoints ? ghostPoints : 0;
        layeredRow(g, left, bottom, slots, healthPoints, ghost, palette, true, look, look.wave);
        int used = slots;
        int row = 0;
        for (int i = 0; i < broken; i++) {
            if (used >= PER_ROW) {
                used = 0;
                row++;
            }
            int x = left + used * STEP;
            int y = bottom - row * ROW + jitter(look.shake);
            container(g, x, y, look);
            Tint.draw(g, x, y, BROKEN, false);
            for (int[] c : CRACK) {
                g.fill(x + c[0], y + c[1], x + c[0] + 1, y + c[1] + 1, CRACK_COLOR);
            }
            used++;
        }
        row++;
        // absorption and the LSO shield: rows of their own, layered the same way
        if (absorption > 0.0f) {
            int pts = Mth.ceil(absorption);
            layeredRow(g, left, bottom - row * ROW, Math.min(PER_ROW, Mth.ceil(pts / 2.0f)), pts, 0, ABSORPTION, false, look, -1);
            row++;
        }
        if (shield > 0.0f) {
            int pts = Mth.ceil(shield);
            layeredRow(g, left, bottom - row * ROW, Math.min(PER_ROW, Mth.ceil(pts / 2.0f)), pts, 0, SHIELD, false, look, -1);
            row++;
        }
        gui.leftHeight += row * ROW;
    }

    /**
     * A row of {@code slots} hearts holding {@code points} half hearts in layers of {@code slots * 2}: the layer below fills every
     * heart, the top layer covers as much as is left. {@code ghost}: the points just lost, shown as vanilla's white blinking heart.
     */
    private static void layeredRow(GuiGraphics g, int left, int y, int slots, int points, int ghost, int[] palette, boolean containers,
                                   Look look, int wave) {
        int cap = Math.max(2, slots * 2);
        int layer = points <= 0 ? 0 : (points - 1) / cap;
        int top = points - layer * cap;
        int ghostTop = ghost > points ? Math.min(cap, ghost - layer * cap) : 0;
        int below = layer > 0 ? darker(palette[(layer - 1) % palette.length]) : -1;
        int topColor = palette[layer % palette.length];
        for (int i = 0; i < slots; i++) {
            int x = left + i * STEP;
            int hy = y + jitter(look.shake) - (i == wave ? 2 : 0);
            int fill = Mth.clamp(top - i * 2, 0, 2);
            int lostTo = Mth.clamp(ghostTop - i * 2, 0, 2);
            if (!containers && below < 0 && fill == 0 && lostTo == 0) {
                continue;
            }
            container(g, x, hy, look);
            if (below >= 0) {
                Tint.draw(g, x, hy, below, false);
            }
            if (lostTo > fill) {
                // the lost part blinks white (a still filled left half is drawn over it below)
                g.blit(ICONS, x, hy, lostTo == 2 ? U_BLINK_FULL : U_BLINK_HALF, look.v, 9, 9);
            }
            if (fill > 0) {
                Tint.draw(g, x, hy, topColor, fill == 1);
            }
        }
    }

    private static void container(GuiGraphics g, int x, int y, Look look) {
        g.blit(ICONS, x, y, look.containerU, look.v, 9, 9);
    }

    /** The layer below the top one is a little darker, so the top layer reads. */
    private static int darker(int rgb) {
        int r = (int) (((rgb >> 16) & 0xFF) * 0.72f);
        int gr = (int) (((rgb >> 8) & 0xFF) * 0.72f);
        int b = (int) ((rgb & 0xFF) * 0.72f);
        return (r << 16) | (gr << 8) | b;
    }

    private static int jitter(boolean shake) {
        return shake ? SHAKE.nextInt(2) : 0;
    }

    /**
     * The vanilla red heart (full and half), made grey: the usual red of the heart becomes light grey, its highlight white and its
     * shade darker, so that any tint keeps the drawing of the heart. Built from the icons of the current resources.
     */
    private static final class Tint {
        private static final ResourceLocation LOCATION = new ResourceLocation(EFW.MOD_ID, "dynamic/heart_tint");
        private static DynamicTexture texture;
        private static int scale = 1;
        private static boolean failed;

        static void invalidate() {
            if (texture != null) {
                Minecraft.getInstance().getTextureManager().release(LOCATION);
                texture = null;
            }
            failed = false;
        }

        /** The grey heart (or its left half) tinted {@code rgb}; the alpha of the HUD fade (AutoHud) is kept. */
        static void draw(GuiGraphics g, int x, int y, int rgb, boolean half) {
            if (!ensure()) {
                return;
            }
            float[] was = RenderSystem.getShaderColor().clone();
            RenderSystem.setShaderColor(was[0] * ((rgb >> 16) & 0xFF) / 255.0f, was[1] * ((rgb >> 8) & 0xFF) / 255.0f,
                    was[2] * (rgb & 0xFF) / 255.0f, was[3]);
            int s = scale;
            g.blit(LOCATION, x, y, 9, 9, half ? 9 * s : 0, 0, 9 * s, 9 * s, 36 * s, 9 * s);
            // the white highlight of the heart, which a tint cannot make, over it untinted
            RenderSystem.setShaderColor(was[0], was[1], was[2], was[3]);
            g.blit(LOCATION, x, y, 9, 9, (half ? 27 : 18) * s, 0, 9 * s, 9 * s, 36 * s, 9 * s);
        }

        private static boolean ensure() {
            if (texture != null) {
                return true;
            }
            if (failed) {
                return false;
            }
            Minecraft mc = Minecraft.getInstance();
            try (InputStream in = mc.getResourceManager().open(ICONS); NativeImage icons = NativeImage.read(in)) {
                int s = Math.max(1, icons.getWidth() / 256);
                NativeImage out = new NativeImage(36 * s, 9 * s, true);
                float base = commonLuminance(icons, U_FULL * s, 0, 9 * s);
                grey(icons, out, U_FULL * s, 0, 18 * s, 9 * s, base);
                grey(icons, out, U_HALF * s, 9 * s, 27 * s, 9 * s, base);
                scale = s;
                texture = new DynamicTexture(out);
                mc.getTextureManager().register(LOCATION, texture);
                return true;
            } catch (Exception e) {
                EFW.LOGGER.warn("Hearts: cannot build the tinted heart from {}", ICONS, e);
                failed = true;
                return false;
            }
        }

        /** The luminance most pixels of the heart have (its plain red), the one that should come out light grey. */
        private static float commonLuminance(NativeImage icons, int u, int v, int size) {
            Map<Integer, Integer> count = new HashMap<>();
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int c = icons.getPixelRGBA(u + x, v + y);
                    if ((c >>> 24) > 128) {
                        count.merge(Math.round(luminance(c) * 255.0f), 1, Integer::sum);
                    }
                }
            }
            int best = 0;
            int bestCount = -1;
            for (Map.Entry<Integer, Integer> e : count.entrySet()) {
                if (e.getValue() > bestCount) {
                    best = e.getKey();
                    bestCount = e.getValue();
                }
            }
            return Math.max(0.05f, best / 255.0f);
        }

        /**
         * The grey heart at {@code toX} (the plain red at 96% grey, darker shades darker) and its highlight at {@code glowX}: what is
         * brighter than the plain red becomes white with that much opacity.
         */
        private static void grey(NativeImage icons, NativeImage out, int u, int toX, int glowX, int size, float base) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int c = icons.getPixelRGBA(u + x, y);
                    int a = c >>> 24;
                    float rel = luminance(c) / base;
                    int l = Mth.clamp(Math.round(rel * 0.96f * 255.0f), 0, 255);
                    int glow = a == 0 ? 0 : Mth.clamp(Math.round((rel - 1.15f) * 0.85f * 255.0f), 0, 255);
                    // NativeImage pixels are ABGR
                    out.setPixelRGBA(toX + x, y, (a << 24) | (l << 16) | (l << 8) | l);
                    out.setPixelRGBA(glowX + x, y, (glow << 24) | 0xFFFFFF);
                }
            }
        }

        private static float luminance(int abgr) {
            int r = abgr & 0xFF;
            int gr = (abgr >> 8) & 0xFF;
            int b = (abgr >> 16) & 0xFF;
            return (0.299f * r + 0.587f * gr + 0.114f * b) / 255.0f;
        }
    }
}
