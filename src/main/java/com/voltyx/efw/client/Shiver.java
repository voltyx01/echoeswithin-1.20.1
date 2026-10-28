package com.voltyx.efw.client;

import com.voltyx.efw.heartbeat.HeartbeatTremor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/**
 * Shivering of the third person model: when the player is frozen (instead of the vanilla shake, which flips the body by 72 degrees
 * each tick) and when the pulse is high (the same moment the hands shake in first person). Smooth noise from a few sines, so it is
 * a tremor and not a twitch.
 */
public final class Shiver {
    private Shiver() {
    }

    /** 0..1: how hard the entity shivers. */
    public static float amount(LivingEntity entity) {
        float a = entity.isFullyFrozen() ? 1.0f : 0.0f;
        if (entity == Minecraft.getInstance().player) {
            a = Math.max(a, HeartbeatTremor.bodyShake());
        }
        return a;
    }

    /** Smooth noise around 0 (-1..1), about six shakes a second; {@code seed} makes the parts shake apart from each other. */
    public static float wave(LivingEntity entity, float partialTick, float seed) {
        float t = entity.tickCount + partialTick + entity.getId() * 7.31f;
        return noise(t, seed);
    }

    /** The same noise for code that has no entity at hand (the arm pose of Not Enough Animations), mapped to 0..1. */
    public static double unit(float seed) {
        float t = (System.nanoTime() / 1_000_000_000.0f) * 20.0f;
        return 0.5 + 0.5 * noise(t, seed);
    }

    private static float noise(float t, float seed) {
        return (float) (Math.sin(t * 1.9f + seed) * 0.55 + Math.sin(t * 3.1f + seed * 2.3f) * 0.3 + Math.sin(t * 0.7f + seed * 0.6f) * 0.15);
    }
}
