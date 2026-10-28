package com.voltyx.efw.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Legendary Survival Overhaul thirst, temperature and wetness of a player, by reflection so that the mod stays optional (the
 * capabilities are synced to the client for LSO's own HUD, so this works on both sides). Unknown values come back as -1 / null.
 */
public final class LsoSurvival {
    /** LSO's WetnessCapability.WETNESS_LIMIT. */
    public static final int WETNESS_LIMIT = 400;

    private static final boolean LOADED = ModList.get().isLoaded("legendarysurvivaloverhaul");
    private static boolean resolved;
    private static Method thirstCap;
    private static Method tempCap;
    private static Method wetCap;
    private static Method hydration;
    private static Method temperature;
    private static Method wetness;

    private LsoSurvival() {
    }

    public static boolean loaded() {
        return LOADED;
    }

    /** Hydration 0..20, -1 when unknown. */
    public static int hydration(Player player) {
        Object v = read(player, thirstCap, hydration);
        return v instanceof Integer i ? i : -1;
    }

    /** FROSTBITE, COLD, NORMAL, HOT or HEAT_STROKE; null when unknown. */
    public static String temperature(Player player) {
        Object v = read(player, tempCap, temperature);
        return v instanceof Enum<?> e ? e.name() : null;
    }

    /** Wetness 0..{@link #WETNESS_LIMIT}, -1 when unknown. */
    public static int wetness(Player player) {
        Object v = read(player, wetCap, wetness);
        return v instanceof Integer i ? i : -1;
    }

    private static Object read(Player player, Method capability, Method getter) {
        if (!LOADED || player == null || !resolve() || capability == null || getter == null) {
            return null;
        }
        try {
            Object cap = capability.invoke(null, player);
            return cap == null ? null : getter.invoke(cap);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static synchronized boolean resolve() {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> util = Class.forName("sfiomn.legendarysurvivaloverhaul.util.CapabilityUtil");
                thirstCap = util.getMethod("getThirstCapability", Player.class);
                tempCap = util.getMethod("getTempCapability", Player.class);
                wetCap = util.getMethod("getWetnessCapability", Player.class);
                hydration = thirstCap.getReturnType().getMethod("getHydrationLevel");
                temperature = tempCap.getReturnType().getMethod("getTemperatureEnum");
                wetness = wetCap.getReturnType().getMethod("getWetness");
            } catch (ReflectiveOperationException | RuntimeException e) {
                thirstCap = null;
            }
        }
        return thirstCap != null;
    }
}
