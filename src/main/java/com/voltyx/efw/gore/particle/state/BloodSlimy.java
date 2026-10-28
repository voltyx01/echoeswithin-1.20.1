package com.voltyx.efw.gore.particle.state;

import com.voltyx.efw.gore.json.BloodStyle;
import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.BloodTuning;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.motion.BloodMotionSlimy;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
public final class BloodSlimy {
    public static final float SLIMY_GRAVITY = 0.68f;
    private static final float AIR_SCALE_MUL = 0.7f;
    private static final float SURFACE_SCALE_MUL_MIN = 1.4f;
    private static final int SURFACE_POP_TICKS = 5;
    private static final int RAPID_GROWTH_BOOST_TICKS = 26;
    private static final float GROUND_SPREAD_MUL = 1.1f;
    private static final float WALL_STRETCH_MUL = 1.18f;
    private static final float WALL_DELAY_MUL = 1.35f;
    private static final int WALL_DELAY_BONUS_TICKS = 10;
    private static final int WALL_REATTACH_COOLDOWN = 6;

    public static boolean isSlimyFluid(@Nullable BloodStyle w) {
        return (w != null ? w : BloodStyle.LIGHT) == BloodStyle.SLIMY;
    }

    public static boolean isSlimy(@Nullable ParticleBlood p) {
        return p != null && BloodSlimy.isSlimyFluid(p.fluidWeight);
    }

    public static float airScale(float baseScale) {
        float s = baseScale * 0.7f;
        return Math.max(s, 0.001f);
    }

    public static float minimumSurfaceScale(float baseScale) {
        float s = baseScale * 1.4f;
        return Math.max(s, 0.001f);
    }

    public static float styleGroundExtendMul(@Nullable BloodStyle w, float base) {
        if (!BloodSlimy.isSlimyFluid(w)) {
            return base;
        }
        return Math.max(0.0f, base * 1.1f);
    }

    public static float styleWallStretchMul(@Nullable BloodStyle w, float base) {
        if (!BloodSlimy.isSlimyFluid(w)) {
            return base;
        }
        return Math.max(0.0f, base * 1.18f);
    }

    public static boolean allowAttachmentAttempt(@Nullable ParticleBlood p, @Nullable Direction face) {
        return BloodMotionSlimy.allowAttach(p, face);
    }

    public static float effectiveWallDripChance01(@Nullable ParticleBlood p) {
        if (p == null) {
            return 0.0f;
        }
        return p.getDripChance01();
    }

    public static int effectiveWallDetachDelayTicks(@Nullable ParticleBlood p) {
        if (p == null) {
            return 0;
        }
        int base = Math.max(0, p.planWallDetachDelayTicks);
        if (!BloodSlimy.isSlimy(p)) {
            return base;
        }
        float visc = Util.clamp01(p.viscosity01);
        int out = Math.round((float)base * (1.35f + 0.1f * visc)) + Math.round(10.0f * (0.5f + 0.5f * visc));
        return Math.max(base, out);
    }

    public static int wallReattachCooldownTicks(@Nullable ParticleBlood p, int normalTicks) {
        if (!BloodSlimy.isSlimy(p)) {
            return normalTicks;
        }
        return Math.max(4, Math.min(normalTicks, 6));
    }

    public static void resetGroundBouncePlan(@Nullable ParticleBlood p) {
        BloodMotionSlimy.resetBounce(p);
    }

    public static void onDetachedFromSurface(@Nullable ParticleBlood p) {
        if (!BloodSlimy.isSlimy(p)) {
            return;
        }
        p.slimySurfaceAnimStartAge = -1;
        p.slimySurfaceAnimTicks = 0;
        BloodSlimy.clearGroundBounceVisual(p);
        float s = BloodSlimy.airScale(p.spawnScale);
        p.setScale(s);
        float cs = BloodTuning.collisionSizeForScale(s);
        p.setSizeSafe(cs, cs);
    }

    private static void clearGroundBounceVisual(@Nullable ParticleBlood p) {
        if (p == null) {
            return;
        }
        p.slimyBounceAnimStartAge = -1;
        p.slimyBounceAnimTicks = 0;
        p.slimyBounceAnimStrength = 0.0f;
    }

    public static boolean hasGroundBounceVisual(@Nullable ParticleBlood p) {
        if (!BloodSlimy.isSlimy(p)) {
            return false;
        }
        if (p.isStuck || p.fallingDripActive) {
            return false;
        }
        if (p.slimyBounceAnimStartAge < 0 || p.slimyBounceAnimTicks <= 0) {
            return false;
        }
        int elapsed = p.getAge() - p.slimyBounceAnimStartAge;
        return elapsed >= 0 && elapsed < p.slimyBounceAnimTicks;
    }

    private static float groundBounceVisualProgress01(@Nonnull ParticleBlood p, float partialTicks) {
        float ageF = (float)p.getAge() + partialTicks;
        float elapsed = ageF - (float)p.slimyBounceAnimStartAge;
        return Util.clamp01(elapsed / (float)Math.max(1, p.slimyBounceAnimTicks));
    }

    public static float groundBounceBillboardScaleX(@Nullable ParticleBlood p, float partialTicks) {
        if (!BloodSlimy.hasGroundBounceVisual(p)) {
            return 1.0f;
        }
        float t = BloodSlimy.groundBounceVisualProgress01(p, partialTicks);
        float s = Util.clamp01(p.slimyBounceAnimStrength);
        float squash = 1.24f + 0.26f * s;
        float stretch = 0.9f - 0.12f * s;
        if (stretch < 0.72f) {
            stretch = 0.72f;
        }
        if (t < 0.18f) {
            return BloodSlimy.lerp(1.0f, squash, Util.easeOutCubic01(t / 0.18f));
        }
        if (t < 0.52f) {
            return BloodSlimy.lerp(squash, stretch, Util.easeOutCubic01((t - 0.18f) / 0.34f));
        }
        return BloodSlimy.lerp(stretch, 1.0f, Util.smoothstep01((t - 0.52f) / 0.48f));
    }

    public static float groundBounceBillboardScaleY(@Nullable ParticleBlood p, float partialTicks) {
        if (!BloodSlimy.hasGroundBounceVisual(p)) {
            return 1.0f;
        }
        float t = BloodSlimy.groundBounceVisualProgress01(p, partialTicks);
        float s = Util.clamp01(p.slimyBounceAnimStrength);
        float squash = 0.8f - 0.18f * s;
        if (squash < 0.58f) {
            squash = 0.58f;
        }
        float stretch = 1.18f + 0.26f * s;
        if (t < 0.18f) {
            return BloodSlimy.lerp(1.0f, squash, Util.easeOutCubic01(t / 0.18f));
        }
        if (t < 0.52f) {
            return BloodSlimy.lerp(squash, stretch, Util.easeOutCubic01((t - 0.18f) / 0.34f));
        }
        return BloodSlimy.lerp(stretch, 1.0f, Util.smoothstep01((t - 0.52f) / 0.48f));
    }

    public static void beginSurfacePop(@Nullable ParticleBlood p) {
        if (!BloodSlimy.isSlimy(p)) {
            return;
        }
        if (!p.isStuck) {
            return;
        }
        if (p.stuckFace == Direction.DOWN) {
            return;
        }
        p.slimySurfaceAnimStartAge = p.getAge();
        p.slimySurfaceAnimTicks = Math.max(1, 5);
    }

    public static boolean isRapidSurfacePopAnimating(@Nullable ParticleBlood p) {
        if (!BloodSlimy.isSlimy(p)) {
            return false;
        }
        if (!p.isStuck) {
            return false;
        }
        if (p.stuckFace == Direction.DOWN) {
            return false;
        }
        if (p.slimySurfaceAnimStartAge < 0 || p.slimySurfaceAnimTicks <= 0) {
            return false;
        }
        int elapsed = p.getAge() - p.slimySurfaceAnimStartAge;
        return elapsed >= 0 && elapsed < p.slimySurfaceAnimTicks;
    }

    public static int pushRapidGrowthWindow(@Nullable ParticleBlood p) {
        float t;
        int boost;
        if (!BloodSlimy.isSlimy(p)) {
            return Integer.MIN_VALUE;
        }
        if (!p.isStuck) {
            return Integer.MIN_VALUE;
        }
        if (p.stuckFace == Direction.DOWN) {
            return Integer.MIN_VALUE;
        }
        if (p.stuckStartAge < 0) {
            return Integer.MIN_VALUE;
        }
        if (p.slimySurfaceAnimStartAge < 0 || p.slimySurfaceAnimTicks <= 0) {
            return Integer.MIN_VALUE;
        }
        int old = p.stuckStartAge;
        int elapsed = p.getAge() - p.slimySurfaceAnimStartAge;
        if (elapsed < 0) {
            elapsed = 0;
        }
        if ((boost = Math.round(26.0f * Util.easeOutCubic01(t = Util.clamp01((float)elapsed / (float)Math.max(1, p.slimySurfaceAnimTicks))))) > 0) {
            p.stuckStartAge = old - boost;
        }
        return old;
    }

    public static void restoreRapidGrowthWindow(@Nullable ParticleBlood p, int oldStuckStartAge) {
        if (p == null) {
            return;
        }
        if (oldStuckStartAge != Integer.MIN_VALUE) {
            p.stuckStartAge = oldStuckStartAge;
        }
    }

    public static void applyScaleForCurrentState(@Nullable ParticleBlood p) {
        float baseVisual;
        if (!BloodSlimy.isSlimy(p)) {
            return;
        }
        if (p.fallingDripActive) {
            return;
        }
        float air = BloodSlimy.airScale(p.spawnScale);
        if (!p.isStuck) {
            float visual = air * BloodSlimy.pulseScaleMul01(p);
            if (visual < 0.001f) {
                visual = 0.001f;
            }
            if (Math.abs(p.getScale() - visual) > 5.0E-4f) {
                p.setScale(visual);
            }
            return;
        }
        if (p.stuckFace == Direction.DOWN) {
            return;
        }
        float target = Math.max(p.getScale(), BloodSlimy.minimumSurfaceScale(p.spawnScale));
        if (p.slimySurfaceAnimStartAge < 0 || p.slimySurfaceAnimTicks <= 0) {
            baseVisual = target;
        } else {
            int dur = Math.max(1, p.slimySurfaceAnimTicks);
            int elapsed = p.getAge() - p.slimySurfaceAnimStartAge;
            if (elapsed < 0) {
                elapsed = 0;
            }
            if (elapsed >= dur) {
                p.slimySurfaceAnimStartAge = -1;
                p.slimySurfaceAnimTicks = 0;
                baseVisual = target;
            } else {
                float t = Util.clamp01((float)elapsed / (float)dur);
                float e = BloodSlimy.easeOutBack01(t);
                baseVisual = BloodSlimy.lerp(air, target, e);
            }
        }
        float visual = baseVisual * BloodSlimy.pulseScaleMul01(p);
        if (visual < 0.001f) {
            visual = 0.001f;
        }
        if (Math.abs(p.getScale() - visual) > 5.0E-4f) {
            p.setScale(visual);
        }
    }

    public static boolean shouldForcePolyRebuild(@Nullable ParticleBlood p) {
        if (!BloodSlimy.isSlimy(p)) {
            return false;
        }
        if (p.fallingDripActive) {
            return false;
        }
        if (!p.isStuck) {
            return false;
        }
        if (p.stuckFace == Direction.DOWN) {
            return false;
        }
        return BloodSlimy.hasSoftPulse(p);
    }

    private static boolean hasSoftPulse(@Nonnull ParticleBlood p) {
        if (!BloodSlimy.isSlimy(p)) {
            return false;
        }
        float visc = Util.smoothstep01(p.viscosity01);
        if (visc < 0.2f) {
            return false;
        }
        float chance = 0.12f + 0.46f * visc;
        return BloodSlimy.traitUnit01(p, 7640891576956012809L) < chance;
    }

    private static float pulseScaleMul01(@Nonnull ParticleBlood p) {
        float waveB;
        double phase;
        float waveA;
        float wave;
        float mul;
        double freq;
        if (!BloodSlimy.hasSoftPulse(p)) {
            return 1.0f;
        }
        float visc = Util.smoothstep01(p.viscosity01);
        float amp = 0.01f + 0.026f * visc;
        if (p.isStuck) {
            amp *= 0.7f;
        }
        if ((freq = 0.18 - 0.07 * (double)visc + 0.03 * (double)BloodSlimy.traitUnit01(p, 4354685564936845354L)) < 0.06) {
            freq = 0.06;
        }
        if ((mul = 1.0f + amp * (wave = (waveA = (float)Math.sin(phase = (double)p.getAge() * freq + (double)p.groundRot + (double)BloodSlimy.traitUnit01(p, -6534734903238641935L) * (Math.PI * 2))) * 0.76f + (waveB = (float)Math.sin(phase * 0.57 + 1.35)) * 0.24f)) < 0.88f) {
            mul = 0.88f;
        }
        if (mul > 1.16f) {
            mul = 1.16f;
        }
        return mul;
    }

    private static float traitUnit01(@Nonnull ParticleBlood p, long salt) {
        long s = BloodSlimy.seedFromParticle(p) ^ salt;
        s = BloodSlimy.mix64(s);
        return (float)(s >>> 40 & 0xFFFFFFL) / 1.6777216E7f;
    }

    private static long seedFromParticle(@Nonnull ParticleBlood p) {
        long sx = Double.doubleToLongBits(p.spawnX);
        long sy = Double.doubleToLongBits(p.spawnY);
        long sz = Double.doubleToLongBits(p.spawnZ);
        long a = Float.floatToIntBits(p.groundRot);
        long b = Float.floatToIntBits(p.dripSeed);
        long s = -7046029254386353131L;
        s ^= sx * 31L + sy * 17L + sz * 13L;
        s ^= a * -4658895280553007687L;
        return BloodSlimy.mix64(s ^= b * -7723592293110705685L);
    }

    private static long mix64(long z) {
        z ^= z >>> 33;
        z *= -49064778989728563L;
        z ^= z >>> 33;
        z *= -4265267296055464877L;
        z ^= z >>> 33;
        return z;
    }

    private static float easeOutBack01(float t01) {
        float t = Util.clamp01(t01) - 1.0f;
        float c1 = 1.70158f;
        float c3 = 2.70158f;
        return 1.0f + 2.70158f * t * t * t + 1.70158f * t * t;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}

