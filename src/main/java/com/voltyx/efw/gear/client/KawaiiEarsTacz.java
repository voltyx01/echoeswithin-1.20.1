package com.voltyx.efw.gear.client;

import com.tacz.guns.api.event.common.GunFireEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** A shot of a TACZ gun fired by the local player: the ears flinch. Registered only when TACZ is loaded. */
public final class KawaiiEarsTacz {
    private KawaiiEarsTacz() {
    }

    @SubscribeEvent
    public static void onFire(GunFireEvent e) {
        if (e.getLogicalSide().isClient() && e.getShooter() instanceof Player player) {
            KawaiiEarsAnimator.shot(player);
        }
    }
}
