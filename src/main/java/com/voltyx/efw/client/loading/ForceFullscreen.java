package com.voltyx.efw.client.loading;

import com.mojang.blaze3d.platform.Window;
import com.voltyx.efw.EFW;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The game window is fullscreen from the moment the loading screen after the early window (the Drippy Loading Screen one) opens,
 * once per launch: the player can leave fullscreen afterwards and it stays that way.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class ForceFullscreen {
    private static boolean done;

    private ForceFullscreen() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (done || e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.getOverlay() instanceof LoadingOverlay)) {
            return;
        }
        done = true;
        Window window = mc.getWindow();
        if (!window.isFullscreen()) {
            window.toggleFullScreen();
        }
    }
}
