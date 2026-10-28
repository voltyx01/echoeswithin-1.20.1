package com.voltyx.efw.compat.client;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.config.client.KeyConfig;
import net.minecraft.client.Minecraft;

/** The gun in the local player's hand worked as its keys do: aim, fire, and whether it is out yet. Only touched when TACZ is installed. */
public final class TaczShoot {
    private TaczShoot() {
    }

    /** The gun is out: its draw (and the put away of what was before) is over. */
    public static boolean isDrawn() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        LocalPlayerDataHolder data = IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder();
        return IGunOperator.fromLivingEntity(mc.player).getSynDrawCoolDown() <= 0L && !data.clientStateLock
                && System.currentTimeMillis() >= data.clientDrawTimestamp;
    }

    /**
     * Holds the gun up to the eye, as the aim key does, or lets it down. With TACZ's hold-to-aim the key itself is held (TACZ drops
     * the aim every tick the key is up); with the toggle the aim is switched once.
     */
    public static void holdAim(boolean aim) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(mc.player);
        if (KeyConfig.HOLD_TO_AIM.get()) {
            AimKey.AIM_KEY.setDown(aim);
        }
        if (operator.isAim() != aim) {
            operator.aim(aim);
        }
    }

    /** How far up to the eye the gun is, 0..1. */
    public static float aimProgress() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0.0F : IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1.0F);
    }

    /** TACZ's answer (SUCCESS, IS_DRAWING, COOL_DOWN, NO_AMMO ...), or null without a player. */
    public static String shoot() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : IClientPlayerGunOperator.fromLocalPlayer(mc.player).shoot().name();
    }
}
