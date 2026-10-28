package com.voltyx.efw.client;

import com.voltyx.efw.EFW;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** There is no vanilla experience (see NoExperience), so its bar and level number are never drawn. */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class NoExperienceHud {
    private NoExperienceHud() {
    }

    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre e) {
        if (e.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type()) {
            e.setCanceled(true);
        }
    }
}
