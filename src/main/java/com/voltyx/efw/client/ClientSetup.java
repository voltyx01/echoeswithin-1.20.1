package com.voltyx.efw.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.roll.RollState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client wiring: roll state for common code and the Player Animator layer. */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent e) {
        RollState.clientRolling = RollClient::isRolling;
        com.voltyx.efw.notes.client.NotesClient.registerItemProperties(e);
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new com.voltyx.efw.config.EfwConfigScreen(parent)));
        if (ModList.get().isLoaded("playeranimator")) {
            e.enqueueWork(RollAnimation::register);
            e.enqueueWork(BareCrawl::register);
        } else {
            EFW.LOGGER.warn("Player Animator is not installed: rolls will have no animation.");
        }
    }
}
