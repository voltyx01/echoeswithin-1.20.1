package com.voltyx.efw.gore;

import com.voltyx.efw.EFW;
import com.voltyx.efw.gore.json.BloodEntityConfig;
import com.voltyx.efw.gore.particle.render.BloodRenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Extra Gore (port of the mwccf version): blood spills out of hurt entities, flies, lands, sticks to walls and ceilings, drips,
 * pools and merges, runs into water and burns on lava. The server only tells the clients about the damage
 * ({@link com.voltyx.efw.gore.pack.BloodDamageSyncHandler}); everything else happens on the client.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GoreSetup {
    private GoreSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent e) {
        BloodEntityConfig.init();
        MinecraftForge.EVENT_BUS.register(BloodOnHitHandler.getInstance());
        MinecraftForge.EVENT_BUS.register(new BloodRenderType());
    }

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading e) {
        if (e.getConfig().getSpec() == ModConfigurationClient.SPEC) {
            ModConfigurationClient.apply();
        }
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading e) {
        if (e.getConfig().getSpec() == ModConfigurationClient.SPEC) {
            ModConfigurationClient.apply();
        }
    }
}
