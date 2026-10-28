package com.voltyx.efw.tacz.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.TaczCompat;
import com.voltyx.efw.sins.client.DustParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Black backdrop with drifting ash dust for the TACZ modification screen, drawn in the level pass so that it covers the world
 * (the gun and the interface come after it, from the screen itself).
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class GunRefitBackground {
    private static final DustParticles DUST = new DustParticles();
    private static boolean wasOpen;

    private GunRefitBackground() {
    }

    /** The hands of the player are not drawn while the modification screen is open: the screen draws the gun itself. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderHand(RenderHandEvent event) {
        if (TaczCompat.isRefitScreen(Minecraft.getInstance().screen)) {
            event.setCanceled(true);
        }
    }

    /** Black backdrop with dust, drawn by the screen itself so that it also covers the hotbar and the rest of the interface. */
    public static void draw(GuiGraphics g, int width, int height) {
        if (!wasOpen) {
            DUST.init(width, height);
            wasOpen = true;
        }
        g.fill(0, 0, width, height, 0xFF000000);
        DUST.updateAndRender(g, width, height);
    }

    public static void reset() {
        wasOpen = false;
    }
}
