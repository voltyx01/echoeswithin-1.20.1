package com.voltyx.efw.gore.particle.state.liquid;

import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
public final class BloodLiquidUtil {
    public static double clamp(double v, double lo, double hi) {
        if (v < lo) {
            return lo;
        }
        return Math.min(v, hi);
    }

    public static float clamp(float v, float lo, float hi) {
        if (v < lo) {
            return lo;
        }
        return Math.min(v, hi);
    }

    public static float clamp01(float v) {
        if (v <= 0.0f) {
            return 0.0f;
        }
        return Math.min(v, 1.0f);
    }

    public static double clamp01(double v) {
        if (v <= 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float smoothstep01(float t) {
        t = BloodLiquidUtil.clamp01(t);
        return t * t * (3.0f - 2.0f * t);
    }

    public static float randBetween(RandomSource rand, float min, float max) {
        if (rand == null) {
            return min;
        }
        if (max <= min) {
            return min;
        }
        return min + rand.nextFloat() * (max - min);
    }

    public static float solveBasePassAlphaForTarget(float targetAlpha, boolean overlayEnabled, float overlayMul) {
        targetAlpha = BloodLiquidUtil.clamp01(targetAlpha);
        if (!overlayEnabled) {
            return targetAlpha;
        }
        float o = BloodLiquidUtil.clamp01(overlayMul);
        if (o <= 0.0f) {
            return targetAlpha;
        }
        double b = -(1.0 + (double)o);
        double a = o;
        double c = targetAlpha;
        double disc = b * b - 4.0 * a * c;
        if (disc <= 0.0) {
            return targetAlpha;
        }
        double sqrt = Math.sqrt(disc);
        double x1 = (-b - sqrt) / (2.0 * a);
        double x2 = (-b + sqrt) / (2.0 * a);
        double x = x1 >= 0.0 && x1 <= 1.0 ? x1 : x2;
        x = BloodLiquidUtil.clamp(x, 0.0, 1.0);
        return (float)x;
    }

    public static float noiseValue(float x, float y, int seed) {
        int ix = Mth.floor((float)x);
        int iy = Mth.floor((float)y);
        float fx = x - (float)ix;
        float fy = y - (float)iy;
        float u = BloodLiquidUtil.smoothstep01(fx);
        float v = BloodLiquidUtil.smoothstep01(fy);
        float n00 = BloodLiquidUtil.hashSigned(ix, iy, seed);
        float n10 = BloodLiquidUtil.hashSigned(ix + 1, iy, seed);
        float n01 = BloodLiquidUtil.hashSigned(ix, iy + 1, seed);
        float n11 = BloodLiquidUtil.hashSigned(ix + 1, iy + 1, seed);
        float nx0 = BloodLiquidUtil.lerp(n00, n10, u);
        float nx1 = BloodLiquidUtil.lerp(n01, n11, u);
        return BloodLiquidUtil.lerp(nx0, nx1, v);
    }

    public static float noiseFbm(float x, float y, int seed) {
        float sum = 0.0f;
        float amp = 0.62f;
        float freq = 1.0f;
        float norm = 0.0f;
        for (int i = 0; i < 3; ++i) {
            sum += BloodLiquidUtil.noiseValue(x * freq, y * freq, seed + i * 1013) * amp;
            norm += amp;
            amp *= 0.5f;
            freq *= 2.0f;
        }
        if (norm > 1.0E-6f) {
            sum /= norm;
        }
        if (sum > 1.0f) {
            sum = 1.0f;
        }
        if (sum < -1.0f) {
            sum = -1.0f;
        }
        return sum;
    }

    private static int hash2(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ h >> 13) * 1274126177;
        return h ^ h >> 16;
    }

    private static float hashSigned(int x, int y, int seed) {
        int h = BloodLiquidUtil.hash2(x, y, seed);
        float u = (float)(h & Integer.MAX_VALUE) / 2.14748365E9f;
        return u * 2.0f - 1.0f;
    }
}

