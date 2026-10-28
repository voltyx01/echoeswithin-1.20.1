package com.voltyx.efw.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Legendary Survival Overhaul hearts for our heart bar, by reflection (LSO stays optional): the broken hearts (lost while a limb is
 * badly hurt; LSO already took them off the max health) and the shield health. Both are synced to the client for LSO's own HUD.
 */
public final class LsoHearts {
    private static final boolean LOADED = ModList.get().isLoaded("legendarysurvivaloverhaul");
    private static boolean resolved;
    private static Method brokenHearts;
    private static Method healthCap;
    private static Method shieldHealth;

    private LsoHearts() {
    }

    /** Whole broken hearts, 0 without LSO. */
    public static int broken(Player player) {
        if (!LOADED || player == null || !resolve() || brokenHearts == null) {
            return 0;
        }
        try {
            return Math.max(0, (int) brokenHearts.invoke(null, player));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    /** Shield health points (2 = one heart), 0 without LSO. */
    public static float shield(Player player) {
        if (!LOADED || player == null || !resolve() || healthCap == null || shieldHealth == null) {
            return 0.0f;
        }
        try {
            Object cap = healthCap.invoke(null, player);
            return cap == null ? 0.0f : Math.max(0.0f, (float) shieldHealth.invoke(cap));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0.0f;
        }
    }

    private static synchronized boolean resolve() {
        if (!resolved) {
            resolved = true;
            try {
                brokenHearts = Class.forName("sfiomn.legendarysurvivaloverhaul.api.health.HealthUtil")
                        .getMethod("getEffectiveBrokenHearts", Player.class);
            } catch (ReflectiveOperationException | RuntimeException e) {
                brokenHearts = null;
            }
            try {
                healthCap = Class.forName("sfiomn.legendarysurvivaloverhaul.util.CapabilityUtil").getMethod("getHealthCapability", Player.class);
                shieldHealth = healthCap.getReturnType().getMethod("getShieldHealth");
            } catch (ReflectiveOperationException | RuntimeException e) {
                healthCap = null;
            }
        }
        return true;
    }
}
