package com.voltyx.efw.compat;

import com.voltyx.efw.EFW;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Ticks the Shoulder Surfing transitions, only when the mod is installed. */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class SsrTransitionEvents {
    private SsrTransitionEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END && ShoulderCompat.isLoaded()) {
            SsrTransition.tick();
        }
    }
}
