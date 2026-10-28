package com.voltyx.efw;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * A dead mob does not vanish the moment its fall to the side is over: it stays a second longer (the server removes it later) and its
 * model fades out meanwhile. Players are left alone (their bodies become ragdolls).
 */
public final class DeathFade {
    /** Ticks of the vanilla fall to the side. */
    public static final int FALL_TICKS = 20;
    /** Ticks of the fade after it. */
    public static final int FADE_TICKS = 20;

    private DeathFade() {
    }

    public static boolean applies(LivingEntity entity) {
        return !(entity instanceof Player);
    }

    /** 1 while alive and falling, then down to 0. */
    public static float alpha(LivingEntity entity, float partialTick) {
        if (!applies(entity) || entity.deathTime <= FALL_TICKS) {
            return 1.0f;
        }
        float t = (entity.deathTime - FALL_TICKS + partialTick) / FADE_TICKS;
        return Math.max(0.0f, Math.min(1.0f, 1.0f - t));
    }
}
