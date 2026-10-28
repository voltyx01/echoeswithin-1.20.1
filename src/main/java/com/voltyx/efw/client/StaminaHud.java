package com.voltyx.efw.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.init.EfwContent;
import com.voltyx.efw.stamina.EfwConfig;
import com.voltyx.efw.stamina.PlayerData;
import com.voltyx.efw.stamina.StaminaEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Stamina bar and roll cooldown bar (port of OverlayStamina): default style above the food row, alternative style on the XP bar. */
public final class StaminaHud {
    private static final ResourceLocation BASE_LAYER = tex("sloi_2");
    private static final ResourceLocation FULL_LAYER = tex("sloi_1");
    private static final ResourceLocation HALF_LAYER = tex("sloi_3");
    private static final ResourceLocation ENERGY_LAYER = tex("sloi_5");
    private static final ResourceLocation ALT_BAR = tex("altlockndash");
    private static final ResourceLocation ALT_CELL = tex("altlockndash2");
    private static final ResourceLocation ALT_BAR_BOOST = tex("altlockndash3");
    private static final ResourceLocation DASH_ROLLBAR = tex("dashbar");
    private static final ResourceLocation DASH_CELL = tex("dashbar_2");
    private static final ResourceLocation ALT_DASH_BAR = tex("alt_dash_bar");
    private static final ResourceLocation ALT_DASH_CELL = tex("alt_dash_cell");

    private static final int DASH_TEX_W = 25;
    private static final int DASH_TEX_H = 5;
    private static final int MAX_COOLDOWN = 15;
    private static final float FADE_SPEED = 0.05f;
    private static final int ALT_TEX_W = 18;
    private static final int ALT_TEX_H = 3;
    private static final int ALT_BAR_W = 182;
    private static final int ALT_BAR_H = 5;
    /** Fill area inside the frames: the stamina bar fills x 1..180 (3 rows), the roll bar x 1..16 (2 rows). */
    private static final int ALT_FILL_W = 180;
    private static final int ALT_DASH_FILL_W = 16;

    private static float dashAlpha = 1.0f;
    /** Cooldown the current roll started with; the server may correct the client's guess upward (SEVEN). */
    private static int altDashTotal;
    private static int prevRawCooldown;
    private static float slideProgress;
    private static long lastSlideMs;

    private StaminaHud() {
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(EFW.MOD_ID, "textures/gui/stamina/" + name + ".png");
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent e) {
            e.registerAbove(VanillaGuiOverlay.FOOD_LEVEL.id(), "stamina", StaminaHud::render);
        }
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class ForgeBus {
        /** The alternative style replaces the XP bar. */
        @SubscribeEvent
        public static void onOverlayPre(RenderGuiOverlayEvent.Pre e) {
            if (EfwConfig.USE_ALTERNATIVE_HUD.get() && e.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type()) {
                e.setCanceled(true);
            }
        }
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || mc.gameMode == null) {
            return;
        }
        GameType mode = mc.gameMode.getPlayerMode();
        if (mode != GameType.SURVIVAL && mode != GameType.ADVENTURE) {
            return;
        }
        PlayerData data = PlayerData.get(player);
        if (data == null) {
            return;
        }
        boolean boost = player.hasEffect(EfwContent.ENERGY_BOOST.get());
        if (EfwConfig.USE_ALTERNATIVE_HUD.get()) {
            renderAltDash(mc, g, data.cooldown, partialTick, width, height);
            renderAlt(g, data.stamina, StaminaEvents.maxStamina(player), boost, width, height);
        } else {
            renderDefault(gui, g, data.stamina, boost, width, height);
            renderDash(g, data, width, height);
        }
    }

    private static void renderDefault(ForgeGui gui, GuiGraphics g, double stamina, boolean boost, int width, int height) {
        int bars = 10;
        int step = 8;
        int baseY = height - gui.rightHeight - EfwConfig.OVERLAY_BASE_Y.get();
        int baseX = width / 2 + EfwConfig.OVERLAY_BASE_X.get() - 82 + 164;
        gui.rightHeight += 10;

        for (int i = 0; i < bars; i++) {
            g.blit(BASE_LAYER, baseX - i * step, baseY, 0, 0, 9, 9, 9, 9);
        }
        if (boost) {
            for (int i = 0; i < bars; i++) {
                g.blit(ENERGY_LAYER, baseX - i * step, baseY, 0, 0, 9, 9, 9, 9);
            }
        }
        for (int i = 0; i < bars; i++) {
            if (stamina >= (i + 1) * 2) {
                g.blit(FULL_LAYER, baseX - i * step, baseY, 0, 0, 9, 9, 9, 9);
            }
        }
        for (int i = 0; i < bars; i++) {
            if (stamina >= i * 2 + 1) {
                g.blit(HALF_LAYER, baseX - i * step, baseY, 0, 0, 9, 9, 9, 9);
            }
        }
    }

    private static int altBarX(int width) {
        return width / 2 - ALT_BAR_W / 2 + EfwConfig.OVERLAY_BASE_X.get() + EfwConfig.ALT_OVERLAY_BASE_X.get();
    }

    private static int altBarY(int height) {
        return height - 10 - EfwConfig.OVERLAY_BASE_Y.get() - EfwConfig.ALT_OVERLAY_BASE_Y.get() - ALT_BAR_H - 40 + 26;
    }

    /**
     * Fills {@code fill} pixels of a bar from its cell texture: column 1 of the cell is the body, stretched,
     * {@code edgeU} is the darker right edge. Same picture the old overlapping per-cell blits produced, two quads total.
     */
    private static void blitFill(GuiGraphics g, ResourceLocation cell, int x, int y, int fill, int rows, int edgeU, int texW, int texH) {
        if (fill <= 0) {
            return;
        }
        if (fill > 1) {
            g.blit(cell, x + 1, y + 1, fill - 1, rows, 1, 1, 1, rows, texW, texH);
        }
        g.blit(cell, x + fill, y + 1, 1, rows, edgeU, 1, 1, rows, texW, texH);
    }

    private static void renderAlt(GuiGraphics g, double stamina, double max, boolean boost, int width, int height) {
        int posX = altBarX(width);
        int posY = altBarY(height);
        g.blit(ALT_BAR, posX, posY, 0, 0, ALT_BAR_W, ALT_BAR_H, ALT_BAR_W, ALT_BAR_H);
        if (stamina > 0 && max > 0) {
            int fill = Mth.clamp((int) Math.round(ALT_FILL_W * Math.min(stamina / max, 1.0)), 2, ALT_FILL_W);
            blitFill(g, ALT_CELL, posX, posY, fill, 3, 6, ALT_BAR_W, ALT_BAR_H);
        }
        if (boost) {
            g.blit(ALT_BAR_BOOST, posX, posY, 0, 0, ALT_BAR_W, ALT_BAR_H, ALT_BAR_W, ALT_BAR_H);
        }
    }

    private static void renderDash(GuiGraphics g, PlayerData data, int width, int height) {
        int cooldown = Math.min(data.cooldown, MAX_COOLDOWN);
        if (cooldown == 0) {
            if (dashAlpha > 0) {
                dashAlpha -= FADE_SPEED;
            }
        } else {
            dashAlpha = 1.0f;
        }
        if (dashAlpha <= 0f) {
            return;
        }
        int posY = height - 10 - EfwConfig.DASH_BAR_BASE_Y.get() - DASH_TEX_H - 40;
        int posX = width / 2 + EfwConfig.DASH_BAR_BASE_X.get() - 13;
        g.setColor(1F, 1F, 1F, dashAlpha);
        g.blit(DASH_ROLLBAR, posX, posY, 0, 0, DASH_TEX_W, DASH_TEX_H, DASH_TEX_W, DASH_TEX_H);
        int filled = MAX_COOLDOWN - cooldown;
        if (filled == 0) {
            filled = 1;
        }
        for (int i = 0; i < filled; i++) {
            g.blit(DASH_CELL, posX + i, posY, 0, 0, DASH_TEX_W, DASH_TEX_H, DASH_TEX_W, DASH_TEX_H);
        }
        g.setColor(1F, 1F, 1F, 1F);
    }

    /** Slides in on a roll and fills by the real remaining cooldown, so a longer cooldown (SEVEN) just fills slower. */
    private static void renderAltDash(Minecraft mc, GuiGraphics g, int cooldown, float partialTick, int width, int height) {
        if (cooldown > prevRawCooldown) {
            altDashTotal = cooldown;
        } else if (cooldown <= 0) {
            altDashTotal = 0;
        }
        prevRawCooldown = cooldown;

        long now = System.currentTimeMillis();
        long deltaMs = lastSlideMs == 0 ? 0 : Math.min(now - lastSlideMs, 50);
        lastSlideMs = now;
        float dt = mc.isPaused() ? 0f : deltaMs / 1000f;
        slideProgress = cooldown > 0 ? Math.min(1.0f, slideProgress + 6.0f * dt) : Math.max(0.0f, slideProgress - 5.0f * dt);
        if (slideProgress <= 0f) {
            return;
        }

        float progress = altDashTotal > 0 ? 1.0f - Mth.clamp((cooldown - partialTick) / altDashTotal, 0.0f, 1.0f) : 1.0f;
        int fill = Mth.clamp(Math.round(progress * ALT_DASH_FILL_W), 2, ALT_DASH_FILL_W);
        int x = altBarX(width) + ALT_BAR_W / 2 - ALT_TEX_W / 2;
        int y = altBarY(height) - ALT_TEX_H + EfwConfig.ALT_DASH_BAR_OFFSET_Y.get();

        g.pose().pushPose();
        g.pose().translate(0.0f, (1.0f - slideProgress) * ALT_TEX_H, 0.0f);
        g.blit(ALT_DASH_BAR, x, y, 0, 0, ALT_TEX_W, ALT_TEX_H, ALT_TEX_W, ALT_TEX_H);
        blitFill(g, ALT_DASH_CELL, x, y, fill, 2, 2, ALT_TEX_W, ALT_TEX_H);
        g.pose().popPose();
    }
}
