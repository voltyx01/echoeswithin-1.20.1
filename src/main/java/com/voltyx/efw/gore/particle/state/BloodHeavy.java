package com.voltyx.efw.gore.particle.state;

import com.voltyx.efw.gore.json.BloodStyle;
import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.state.liquid.lava.BloodLava;
import com.voltyx.efw.gore.particle.state.liquid.water.BloodWater;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
public final class BloodHeavy {
    public static final float HEAVY_LAVA_BASE_SCALE_MUL = 1.0f;
    public static final float HEAVY_LAVA_SURFACE_SCALE_MUL = 1.0f;
    public static final float HEAVY_LAVA_DECAL_CLOT_SCALE_ADD = 0.0f;
    public static final float HEAVY_WATER_BASE_SCALE_MUL = 1.0f;
    public static final float HEAVY_WATER_SPREAD_MUL = 1.0f;

    public static float waterBaseScaleMul(boolean heavy) {
        return heavy ? 1.0f : 1.5f;
    }

    public static float waterSpreadMul(boolean heavy, float spreadMulFromConfig) {
        if (heavy) {
            return Math.max(0.0f, 1.0f);
        }
        return Math.max(1.0f, spreadMulFromConfig * 2.0f);
    }

    public static BloodStyle normalizeWeight(@Nullable BloodStyle w) {
        return w != null ? w : BloodStyle.LIGHT;
    }

    public static boolean isHeavyFluid(@Nullable BloodStyle w) {
        return BloodHeavy.normalizeWeight(w) == BloodStyle.HEAVY;
    }

    public static boolean isHeavy(@Nullable ParticleBlood p) {
        return p != null && BloodHeavy.isHeavyFluid(p.fluidWeight);
    }

    public static float lavaPulseStatic01(int noiseSeed) {
        int s = noiseSeed;
        float u = (float)((s ^ s >>> 16) & 0x3FF) / 1023.0f;
        float v = 0.42f + 0.16f * Util.smoothstep01(u);
        return Util.clamp01(v);
    }

    public static float lavaLumpStatic01(int noiseSeed) {
        int s = noiseSeed * 1664525 + 1013904223;
        float u = (float)(s >>> 20 & 0x3FF) / 1023.0f;
        float t = Util.smoothstep01(u);
        t *= t;
        return Util.clamp01(t);
    }

    public static float lavaPulseStatic01(@Nullable BloodLava p) {
        return p == null ? 0.0f : BloodHeavy.lavaPulseStatic01(p.getNoiseSeed());
    }

    public static float lavaLumpStatic01(@Nullable BloodLava p) {
        return p == null ? 0.0f : BloodHeavy.lavaLumpStatic01(p.getNoiseSeed());
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean withLavaHeavyPreHitSegment(@Nonnull BloodLava p, float partialTicks, @Nonnull RenderCall call) {
        if (!p.isOnSurface()) {
            return false;
        }
        if (!p.isHeavyLandedThisTick()) {
            return false;
        }
        float tHit = p.getHeavyLandT();
        if (partialTicks >= tHit) {
            return false;
        }
        double opx = p.getPosX();
        double opy = p.getPosY();
        double opz = p.getPosZ();
        double oprx = p.getPrevPosX();
        double opry = p.getPrevPosY();
        double oprz = p.getPrevPosZ();
        double sx = p.getHeavyLandStartX();
        double sy = p.getHeavyLandStartY();
        double sz = p.getHeavyLandStartZ();
        double ex = sx + p.getHeavyLandStepX();
        double ey = sy + p.getHeavyLandStepY();
        double ez = sz + p.getHeavyLandStepZ();
        p.setRawPrevPos(sx, sy, sz);
        p.setRawPos(ex, ey, ez);
        try {
            call.render();
        }
        finally {
            p.setRawPrevPos(oprx, opry, oprz);
            p.setRawPos(opx, opy, opz);
        }
        return true;
    }

    public static boolean shouldSkipLavaSurfaceDecal(@Nullable BloodLava p, float partialTicks) {
        if (p == null) {
            return true;
        }
        if (!p.isHeavyInLava()) {
            return false;
        }
        return p.isHeavyLandedThisTick() && partialTicks < p.getHeavyLandT();
    }

    public static double lavaSurfaceDecalX(@Nonnull BloodLava p, float partialTicks) {
        if (p.isHeavyInLava() && p.isHeavyLandedThisTick()) {
            return p.getPosX();
        }
        return p.getPrevPosX() + (p.getPosX() - p.getPrevPosX()) * (double)partialTicks;
    }

    public static double lavaSurfaceDecalZ(@Nonnull BloodLava p, float partialTicks) {
        if (p.isHeavyInLava() && p.isHeavyLandedThisTick()) {
            return p.getPosZ();
        }
        return p.getPrevPosZ() + (p.getPosZ() - p.getPrevPosZ()) * (double)partialTicks;
    }

    public static float lavaBaseScaleMul(boolean heavy) {
        return heavy ? 1.0f : 1.25f;
    }

    public static float lavaSurfaceScaleMul(boolean heavy) {
        return heavy ? 1.0f : 1.4f;
    }

    public static float lavaHeavyDecalScaleMul(float clot01) {
        return 1.0f + 0.0f * Util.clamp01(clot01);
    }

    public static boolean allowWaterAmalgamation(@Nullable BloodWater p) {
        return p != null && !p.isHeavyInWater();
    }

    @FunctionalInterface
    public static interface RenderCall {
        public void render();
    }

    public static final class Runtime {
        public int heavyGroundBounceStartAge = -1;
        public int heavyGroundBounceEndAge = -1;
        public int heavyGroundBounceCount = 0;
        public int heavyGroundBounceMax = 0;
        public HeavyGroundBounceMode heavyGroundBounceMode = HeavyGroundBounceMode.NONE;
        public int heavyTotterEndAge = -1;
        public float heavyTotterSpin = 0.0f;
        public float heavyTotterAmp = 0.0f;
    }

    public static enum HeavyGroundBounceMode {
        NONE,
        SMALL,
        AROUND;

    }
}

