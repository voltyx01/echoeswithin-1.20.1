package com.voltyx.efw.sins.client;

import com.voltyx.efw.EFW;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Fade to black, swap the screen, fade back in (port of InspectTransitionHandler): 190 ms each way.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class ScreenTransition {
    private static final float HALF_DURATION_MS = 190.0f;

    private static boolean active;
    private static long startTime;
    private static Screen pendingScreen;
    private static boolean screenSwapped;
    private static boolean justSwapped;

    private ScreenTransition() {
    }

    /** Starts the transition; {@code target} may be null to close the screen. */
    public static void to(Screen target) {
        pendingScreen = target;
        screenSwapped = false;
        justSwapped = false;
        active = true;
        startTime = System.currentTimeMillis();
    }

    private static float alpha() {
        long now = System.currentTimeMillis();
        if (justSwapped) {
            justSwapped = false;
            startTime = now;
        }
        float elapsed = now - startTime;
        if (!screenSwapped) {
            if (elapsed < HALF_DURATION_MS) {
                return elapsed / HALF_DURATION_MS;
            }
            // darkest point: swap the screen
            screenSwapped = true;
            justSwapped = true;
            Screen toOpen = pendingScreen;
            pendingScreen = null;
            Minecraft.getInstance().setScreen(toOpen);
            return 1.0f;
        }
        if (elapsed >= HALF_DURATION_MS) {
            active = false;
            return 0.0f;
        }
        return 1.0f - elapsed / HALF_DURATION_MS;
    }

    private static void draw(GuiGraphics g) {
        float a = Math.max(0.0f, Math.min(1.0f, alpha()));
        if (a <= 0.0f) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        g.pose().pushPose();
        g.pose().translate(0, 0, 2000);
        g.fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), ((int) (a * 255) << 24));
        g.pose().popPose();
    }

    private static boolean drawnThisFrame;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRender(ScreenEvent.Render.Post e) {
        if (active) {
            draw(e.getGuiGraphics());
            drawnThisFrame = true;
        }
    }

    /** Over the HUD while no screen is open (an item inspected straight from the world). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHudRender(RenderGuiEvent.Post e) {
        if (active && Minecraft.getInstance().screen == null) {
            draw(e.getGuiGraphics());
            drawnThisFrame = true;
        }
    }

    /** Keeps the timer running on frames where nothing could draw the overlay (hidden HUD, closed to the world). */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        if (active && !drawnThisFrame) {
            alpha();
        }
        drawnThisFrame = false;
    }
}
