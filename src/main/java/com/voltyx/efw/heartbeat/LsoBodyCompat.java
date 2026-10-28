package com.voltyx.efw.heartbeat;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Legendary Survival Overhaul body damage (broken limbs) for the pulse, by reflection so that the mod stays optional. A part counts as
 * broken when less than one point of its health is left (the 1.12 body damage system of mwccf used the same threshold).
 */
public final class LsoBodyCompat {
    private static final boolean LOADED = ModList.get().isLoaded("legendarysurvivaloverhaul");
    private static boolean resolved;
    private static boolean usable;
    private static Capability<?> capability;
    private static Object[] parts;
    private static Method getDamage;
    private static Method getMaxHealth;

    private LsoBodyCompat() {
    }

    /** Number of broken body parts of the player (0 when the mod is absent). */
    public static int brokenParts(Player player) {
        if (!LOADED || !resolve()) {
            return 0;
        }
        // no body damage in creative and spectator
        if (player.isCreative() || player.isSpectator()) {
            return 0;
        }
        try {
            Object body = player.getCapability(capability).resolve().orElse(null);
            if (body == null) {
                return 0;
            }
            int broken = 0;
            for (Object part : parts) {
                float damage = (Float) getDamage.invoke(body, part);
                float max = (Float) getMaxHealth.invoke(body, part);
                // the server has not sent the body yet (right after joining a world all maximums are 0): not broken, just unknown
                if (max <= 1.0f) {
                    continue;
                }
                if (max - damage <= 1.0f) {
                    broken++;
                }
            }
            return broken;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    private static synchronized boolean resolve() {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> provider = Class.forName("sfiomn.legendarysurvivaloverhaul.common.capabilities.bodydamage.BodyDamageProvider");
                Field cap = provider.getField("BODY_DAMAGE_CAPABILITY");
                capability = (Capability<?>) cap.get(null);
                Class<?> partEnum = Class.forName("sfiomn.legendarysurvivaloverhaul.api.bodydamage.BodyPartEnum");
                parts = partEnum.getEnumConstants();
                Class<?> api = Class.forName("sfiomn.legendarysurvivaloverhaul.api.bodydamage.IBodyDamageCapability");
                getDamage = api.getMethod("getBodyPartDamage", partEnum);
                getMaxHealth = api.getMethod("getBodyPartMaxHealth", partEnum);
                usable = capability != null;
            } catch (ReflectiveOperationException | RuntimeException e) {
                usable = false;
            }
        }
        return usable;
    }
}
