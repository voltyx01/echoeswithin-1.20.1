package com.voltyx.efw.gore.pack;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** What kind of damage it was (the config turns blood off for some kinds): by the damage types and tags of 1.20. */
public final class BloodDamageClassifier {
    private BloodDamageClassifier() {
    }

    @Nonnull
    public static BloodDamageKind classify(@Nullable DamageSource source) {
        if (source == null) {
            return BloodDamageKind.UNKNOWN;
        }
        boolean hasCarrier = source.getEntity() != null || source.getDirectEntity() != null || source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypeTags.IS_EXPLOSION);
        if (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.LAVA) || source.is(DamageTypes.HOT_FLOOR)
                || !hasCarrier && source.is(DamageTypeTags.IS_FIRE)) {
            return BloodDamageKind.FIRE;
        }
        if (source.is(DamageTypes.DROWN)) {
            return BloodDamageKind.DROWNING;
        }
        if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING)) {
            return BloodDamageKind.SUFFOCATION;
        }
        if (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)) {
            return BloodDamageKind.CACTUS;
        }
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER)
                || source.is(DamageTypes.DRAGON_BREATH) || source.is(DamageTypes.STARVE) || source.is(DamageTypes.FREEZE)) {
            return BloodDamageKind.EFFECTS;
        }
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.FALLING_ANVIL)
                || source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_STALACTITE) || source.is(DamageTypes.STALAGMITE)) {
            return BloodDamageKind.FALL;
        }
        if (source.is(DamageTypeTags.IS_LIGHTNING)) {
            return BloodDamageKind.LIGHTNING;
        }
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) {
            return BloodDamageKind.VOID;
        }
        return hasCarrier ? BloodDamageKind.IMPACT : BloodDamageKind.UNKNOWN;
    }
}
