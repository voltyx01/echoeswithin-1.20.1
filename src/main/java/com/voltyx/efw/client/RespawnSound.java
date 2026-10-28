package com.voltyx.efw.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.init.EfwContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The sound of coming back: played to the player when they respawn after a death (not on a change of dimension). */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class RespawnSound {
    /** Switched off for now. */
    private static final boolean PLAY = false;

    private RespawnSound() {
    }

    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone e) {
        // the player is cloned on every respawn: only the one that was dead is coming back from death
        if (PLAY && e.getOldPlayer().isDeadOrDying()) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(EfwContent.RESPAWN.get(), 1.0f, 1.0f));
        }
    }
}
