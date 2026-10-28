package com.voltyx.efw.sins.stat;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunFinishReloadEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

/** TACZ part of SEVEN: bullet damage, headshots, gun kills and reloads. Registered only when TACZ is installed. */
public final class SinTaczHooks {
    private SinTaczHooks() {
    }

    /** After the efw head boxes decided the head (TaczHeadshots runs at LOWEST too, but registered earlier). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGunHurt(EntityHurtByGunEvent.Pre e) {
        if (e.getLogicalSide() != LogicalSide.SERVER || !(e.getAttacker() instanceof ServerPlayer p)) {
            return;
        }
        LivingEntity target = e.getHurtEntity() instanceof LivingEntity l ? l : null;
        e.setBaseAmount(e.getBaseAmount() * (float) SinStats.multAgainst(p, Stat.GUN_DAMAGE, target));
        if (e.isHeadShot()) {
            e.setHeadshotMultiplier(e.getHeadshotMultiplier() * (float) SinStats.multAgainst(p, Stat.HEADSHOT_DAMAGE, target));
        }
    }

    @SubscribeEvent
    public static void onGunHurtPost(EntityHurtByGunEvent.Post e) {
        if (e.getLogicalSide() == LogicalSide.SERVER && e.isHeadShot() && e.getAttacker() instanceof ServerPlayer p) {
            SinStats.fire(p, Trigger.HEADSHOT);
        }
    }

    @SubscribeEvent
    public static void onGunKill(EntityKillByGunEvent e) {
        if (e.getLogicalSide() == LogicalSide.SERVER && e.getAttacker() instanceof ServerPlayer p) {
            SinStats.fire(p, Trigger.KILL_GUN);
        }
    }

    @SubscribeEvent
    public static void onReloaded(GunFinishReloadEvent e) {
        // the event knows only the gun: the server finds the player holding it
        if (e.getLogicalSide() != LogicalSide.SERVER) {
            return;
        }
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getMainHandItem() == e.getGunItemStack()) {
                SinStats.fire(p, Trigger.RELOAD);
                return;
            }
        }
    }
}
