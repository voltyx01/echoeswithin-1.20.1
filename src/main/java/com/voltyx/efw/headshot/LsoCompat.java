package com.voltyx.efw.headshot;

import com.voltyx.efw.EFW;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Collections;
import java.util.List;

/**
 * Bridge to Legendary Survival Overhaul's localized body damage (see the Lso mixins): while LSO handles a damage, the part
 * found by the efw hit detection replaces LSO's own guess, like the 1.12 body damage system used HeadshotDamageHandler.
 */
public final class LsoCompat {
    private static final ThreadLocal<DamageSource> CURRENT = new ThreadLocal<>();
    private static Class<?> bodyPartEnum;
    private static boolean lookedUp;

    private LsoCompat() {
    }

    public static void begin(DamageSource source) {
        CURRENT.set(source);
    }

    public static void end() {
        CURRENT.remove();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<Object> hitPart() {
        BodyPart part = HeadshotEvents.getHitPart(CURRENT.get());
        Class<?> cls = enumClass();
        if (part == null || cls == null) {
            return null;
        }
        return Collections.singletonList(Enum.valueOf((Class) cls, part.name()));
    }

    /** LSO's head multiplier, or 1 when the efw head boxes already multiplied this headshot. */
    public static double headCriticalShotMultiplier() {
        double lso = lsoValue();
        if (HeadshotConfig.LSO_SUPPRESS_OWN_HEADSHOT.get() && HeadshotEvents.isHeadshot(CURRENT.get())) {
            return 1.0;
        }
        return lso;
    }

    private static double lsoValue() {
        try {
            return Class.forName("sfiomn.legendarysurvivaloverhaul.config.Config$Baked").getField("headCriticalShotMultiplier").getDouble(null);
        } catch (ReflectiveOperationException e) {
            return 1.0;
        }
    }

    private static synchronized Class<?> enumClass() {
        if (!lookedUp) {
            lookedUp = true;
            try {
                bodyPartEnum = Class.forName("sfiomn.legendarysurvivaloverhaul.api.bodydamage.BodyPartEnum");
            } catch (ClassNotFoundException e) {
                EFW.LOGGER.warn("[Headshots] Legendary Survival Overhaul body parts not found");
            }
        }
        return bodyPartEnum;
    }
}
