package com.voltyx.efw.gore;

import com.voltyx.efw.gore.ModConfigurationClient;
import com.voltyx.efw.gore.json.BloodEntityConfig;
import com.voltyx.efw.gore.particle.common.Util;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
public final class BloodHitScaling {
    private static final double SMALL_MAX_DIM = 1.0;
    private static final double MEDIUM_MAX_DIM = 2.0;
    private static final float SMALL_SCALE_MUL = 0.72f;
    private static final float MEDIUM_SCALE_MUL = 1.0f;
    private static final float LARGE_SCALE_MUL = 1.4f;
    private static final float SMALL_COUNT_MUL = 0.6f;
    private static final float MEDIUM_COUNT_MUL = 1.0f;
    private static final float LARGE_COUNT_MUL = 1.2f;
    private static final float SMALL_RADIUS_MUL = 1.0f;
    private static final float MEDIUM_RADIUS_MUL = 1.18f;
    private static final float LARGE_RADIUS_MUL = 1.32f;
    private static final float SMALL_SPEED_MUL = 0.8f;
    private static final float MEDIUM_SPEED_MUL = 1.0f;
    private static final float LARGE_SPEED_MUL = 1.2f;
    private static final float DEFAULT_HEALTH_SCALE_START_HEALTH_FRAC = 0.8f;
    private static final float DEFAULT_HEALTH_SCALE_END_HEALTH_FRAC = 0.3f;
    private static final float DEFAULT_HEALTH_SCALE_AT_START = 0.55f;
    private static final float DEFAULT_HEALTH_SCALE_AT_LOW = 1.0f;
    private static final float HEALTH_COUNT_START_HEALTH_FRAC = 0.8f;
    private static final float HEALTH_COUNT_END_HEALTH_FRAC = 0.3f;
    private static final float HEALTH_COUNT_AT_START = 0.4f;
    private static final float HEALTH_COUNT_AT_LOW = 1.0f;
    private static final float MIN_VISIBLE_PARTICLE_SCALE = 0.03f;

    @Nullable
    public static Resolved resolve(@Nonnull LivingEntity target, @Nonnull BloodEntityConfig.Values cfg, float postHitHealth, float maxHealth) {
        SizeProfile size = BloodHitScaling.getSizeProfile(target);
        int baseCount = BloodEntityConfig.sanitizeParticleCount(cfg.count, BloodEntityConfig.getDefaultParticleCount());
        if (baseCount <= 0) {
            return null;
        }
        float healthFrac = BloodHitScaling.computeHealthFraction(postHitHealth, maxHealth);
        double countExact = (float)baseCount * size.countMultiplier * BloodHitScaling.getHealthCountMultiplier(healthFrac);
        if (countExact <= 1.0E-6) {
            return null;
        }
        float finalScaleMul = size.scaleMultiplier * BloodHitScaling.getHealthScaleMultiplier(healthFrac);
        Util.RangeF baseRange = BloodEntityConfig.sanitizeScaleRange(cfg.scaleMin, cfg.scaleMax);
        Util.RangeF scaledRange = BloodEntityConfig.sanitizeScaleRange(baseRange.min * finalScaleMul, baseRange.max * finalScaleMul);
        if (scaledRange.max < 0.03f) {
            return null;
        }
        if (scaledRange.min < 0.03f) {
            scaledRange = BloodEntityConfig.sanitizeScaleRange(0.03f, scaledRange.max);
        }
        return new Resolved(size, countExact, scaledRange.min, scaledRange.max);
    }

    @Nonnull
    public static SizeProfile getSizeProfile(@Nonnull LivingEntity target) {
        double depth;
        AABB bb = target.getBoundingBox();
        if (bb == null) {
            return SizeProfile.MEDIUM;
        }
        double width = Math.max(0.0, bb.maxX - bb.minX);
        double height = Math.max(0.0, bb.maxY - bb.minY);
        double maxDim = Math.max(height, Math.max(width, depth = Math.max(0.0, bb.maxZ - bb.minZ)));
        if (maxDim <= 1.0) {
            return SizeProfile.SMALL;
        }
        if (maxDim <= 2.0) {
            return SizeProfile.MEDIUM;
        }
        return SizeProfile.LARGE;
    }

    public static float getHealthScaleMultiplier(float healthFrac) {
        ModConfigurationClient.HealthScaleSettings cfg = ModConfigurationClient.healthScale;
        if (!cfg.enableSizeReduction) {
            return 1.0f;
        }
        float startHealthFrac = BloodHitScaling.sanitizeConfiguredFraction(cfg.startHealthFraction, 0.8f);
        float endHealthFrac = BloodHitScaling.sanitizeConfiguredFraction(cfg.endHealthFraction, 0.3f);
        float scaleAtStart = BloodHitScaling.sanitizeConfiguredMultiplier(cfg.sizeAtStart, 0.55f);
        float scaleAtLow = BloodHitScaling.sanitizeConfiguredMultiplier(cfg.sizeAtLow, 1.0f);
        float t = BloodHitScaling.rampBetweenHealthFractions(healthFrac, startHealthFrac, endHealthFrac);
        return BloodHitScaling.lerp(scaleAtStart, scaleAtLow, t);
    }

    public static float getHealthCountMultiplier(float healthFrac) {
        if (healthFrac >= 0.8f) {
            return 0.4f;
        }
        if (healthFrac <= 0.3f) {
            return 1.0f;
        }
        float t = BloodHitScaling.rampBetweenHealthFractions(healthFrac, 0.8f, 0.3f);
        return BloodHitScaling.lerp(0.4f, 1.0f, t);
    }

    private static float rampBetweenHealthFractions(float healthFrac, float startHealthFrac, float endHealthFrac) {
        if (startHealthFrac < endHealthFrac) {
            float swap = startHealthFrac;
            startHealthFrac = endHealthFrac;
            endHealthFrac = swap;
        }
        if (healthFrac >= startHealthFrac) {
            return 0.0f;
        }
        if (healthFrac <= endHealthFrac) {
            return 1.0f;
        }
        return (startHealthFrac - healthFrac) / Math.max(1.0E-6f, startHealthFrac - endHealthFrac);
    }

    private static float computeHealthFraction(float health, float maxHealth) {
        maxHealth = Math.max(1.0f, maxHealth);
        health = Mth.clamp((float)health, (float)0.0f, (float)maxHealth);
        return health / maxHealth;
    }

    private static float lerp(float a, float b, float t) {
        t = Mth.clamp((float)t, (float)0.0f, (float)1.0f);
        return a + (b - a) * t;
    }

    private static float sanitizeConfiguredFraction(double value, float fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Mth.clamp((float)((float)value), (float)0.0f, (float)1.0f);
    }

    private static float sanitizeConfiguredMultiplier(double value, float fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(0.0f, (float)value);
    }

    public static final class Resolved {
        public final SizeProfile sizeProfile;
        public final double countExact;
        public final float scaleMin;
        public final float scaleMax;

        private Resolved(SizeProfile sizeProfile, double countExact, float scaleMin, float scaleMax) {
            this.sizeProfile = sizeProfile;
            this.countExact = countExact;
            this.scaleMin = scaleMin;
            this.scaleMax = scaleMax;
        }
    }

    public static enum SizeProfile {
        SMALL(0.72f, 0.6f, 1.0f, 0.8f),
        MEDIUM(1.0f, 1.0f, 1.18f, 1.0f),
        LARGE(1.4f, 1.2f, 1.32f, 1.2f);

        public final float scaleMultiplier;
        public final float countMultiplier;
        public final float splashRadiusMultiplier;
        public final float splashSpeedMultiplier;

        private SizeProfile(float scaleMultiplier, float countMultiplier, float splashRadiusMultiplier, float splashSpeedMultiplier) {
            this.scaleMultiplier = scaleMultiplier;
            this.countMultiplier = countMultiplier;
            this.splashRadiusMultiplier = splashRadiusMultiplier;
            this.splashSpeedMultiplier = splashSpeedMultiplier;
        }
    }
}

