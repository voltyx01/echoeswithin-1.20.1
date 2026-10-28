package com.voltyx.efw.gore.particle.state;

import com.voltyx.efw.gore.json.BloodStyle;
import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.cache.BloodCachesMagic;
import com.voltyx.efw.gore.particle.common.motion.BloodMotion;
import net.minecraft.util.RandomSource;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
public final class BloodMagic {
    public static boolean MAGIC_MIX_LIGHT_ENABLED = true;
    public static final float MAGIC_MIX_LIGHT_FRACTION = 0.3f;
    public static final float MAGIC_GRAVITY = 0.0f;
    public static final double MAGIC_DAMP_EARLY = 0.985;
    public static final double MAGIC_DAMP_LATE = 0.9;
    public static final double MAGIC_STOP_SPEED = 0.0012;
    public static final double MAGIC_TURN_TO_UP_RATE = 0.058;
    public static final double MAGIC_TURN_TO_UP_SLOW_BONUS = 0.01;
    public static final double MAGIC_SWIRL_YAW_PER_TICK = 0.055;
    public static final double MAGIC_NEBULA_SIDE_BLEND_MAX = 0.032;
    public static final double MAGIC_ORBIT_WOBBLE_RAD_MAX = 0.0125;
    public static final float MAGIC_TWO_PI_F = (float)Math.PI * 2;
    public static final double MAGIC_SLOW_PHASE_START_SPEED_FRAC = 0.5;

    public static BloodStyle normalizeWeight(@Nullable BloodStyle w) {
        return w != null ? w : BloodStyle.LIGHT;
    }

    public static boolean isMagicFluid(@Nullable BloodStyle w) {
        return BloodMagic.normalizeWeight(w) == BloodStyle.MAGIC;
    }

    public static boolean isMagic(@Nullable ParticleBlood p) {
        return p != null && BloodMagic.isMagicFluid(p.fluidWeight);
    }

    public static boolean treatLiquidBlocksAsAir(@Nullable ParticleBlood p) {
        return BloodMagic.isMagic(p);
    }

    public static boolean allowAttachmentAttempt(@Nullable ParticleBlood p, @Nullable Direction faceHint) {
        if (p == null || faceHint == null) {
            return false;
        }
        return !BloodMagic.isMagic(p);
    }

    public static BloodStyle mixMagicWithLightOnSpawn(@Nullable BloodStyle requested, @Nullable RandomSource rand) {
        BloodStyle w = BloodMagic.normalizeWeight(requested);
        if (w != BloodStyle.MAGIC) {
            return w;
        }
        if (!MAGIC_MIX_LIGHT_ENABLED) {
            return w;
        }
        float f = 0.3f;
        if (f <= 0.0f) {
            return BloodStyle.MAGIC;
        }
        if (f >= 1.0f) {
            return BloodStyle.LIGHT;
        }
        if (rand == null) {
            return BloodStyle.MAGIC;
        }
        return rand.nextFloat() < f ? BloodStyle.LIGHT : BloodStyle.MAGIC;
    }

    public static float magicSlowProgress01(double currentSpeed, double initSpeed) {
        initSpeed = Math.max(initSpeed, 1.0E-9);
        double startSpeed = initSpeed * 0.5;
        if (currentSpeed > (startSpeed = Math.max(startSpeed, 1.0E-9))) {
            return 0.0f;
        }
        double prog = (startSpeed - currentSpeed) / startSpeed;
        return Util.clamp01((float)prog);
    }

    public static long magicSeedFromParticle(@Nullable ParticleBlood p) {
        if (p == null) {
            return 1311862289879068560L;
        }
        long sx = Double.doubleToLongBits(p.spawnX);
        long sy = Double.doubleToLongBits(p.spawnY);
        long sz = Double.doubleToLongBits(p.spawnZ);
        long a = Float.floatToIntBits(p.groundRot);
        long b = Float.floatToIntBits(p.dripSeed);
        long s = -7046029254386353131L;
        s ^= sx * 31L + sy * 17L + sz * 13L;
        s ^= a * -4658895280553007687L;
        s ^= b * -7723592293110705685L;
        s ^= s >>> 33;
        s *= -49064778989728563L;
        s ^= s >>> 33;
        s *= -4265267296055464877L;
        s ^= s >>> 33;
        return s;
    }

    public static MagicRt magicRt(@Nullable ParticleBlood p, double currentSpeed) {
        if (p == null) {
            MagicRt tmp = new MagicRt();
            tmp.initSpeed = Math.max(currentSpeed, 1.0E-9);
            tmp.blinkPeriodMul = 1.0f;
            tmp.blinkAmpMul = 1.0f;
            tmp.blinkPhaseOffset = 0.0f;
            tmp.jitterFreq = 0.1f;
            tmp.jitterPhase = 0.0f;
            tmp.sideMul = 1.0f;
            tmp.sideSpin = 0.0f;
            tmp.swirlMul = 1.0f;
            tmp.axisX = 0.0f;
            tmp.axisY = 1.0f;
            tmp.axisZ = 0.0f;
            tmp.orbitPhase = 0.0f;
            return tmp;
        }
        MagicRt rt = BloodCachesMagic.get(p);
        if (rt == null) {
            rt = new MagicRt();
            rt.initSpeed = Math.max(currentSpeed, 1.0E-9);
            RandomSource r = RandomSource.create(BloodMagic.magicSeedFromParticle(p));
            rt.blinkPeriodMul = 0.92f + r.nextFloat() * 0.16f;
            rt.blinkAmpMul = 0.9f + r.nextFloat() * 0.2f;
            rt.blinkPhaseOffset = r.nextFloat() * ((float)Math.PI * 2);
            rt.jitterFreq = 0.06f + r.nextFloat() * 0.1f;
            rt.jitterPhase = r.nextFloat() * ((float)Math.PI * 2);
            rt.sideMul = 0.9f + r.nextFloat() * 0.2f;
            rt.sideSpin = (r.nextFloat() * 2.0f - 1.0f) * 0.006f;
            rt.swirlMul = 0.9f + r.nextFloat() * 0.2f;
            Vec3 launch = BloodMotion.normalizeOr(new Vec3(p.motionX, p.motionY, p.motionZ), new Vec3(0.0, 1.0, 0.0));
            rt.axisX = (float)launch.x;
            rt.axisY = (float)launch.y;
            rt.axisZ = (float)launch.z;
            rt.orbitPhase = r.nextFloat() * ((float)Math.PI * 2);
            BloodCachesMagic.put(p, rt);
        } else if (rt.initSpeed < 1.0E-9) {
            rt.initSpeed = Math.max(currentSpeed, 1.0E-9);
        }
        return rt;
    }

    public static final class MagicRt {
        private double initSpeed;
        private float blinkPeriodMul;
        private float blinkAmpMul;
        private float blinkPhaseOffset;
        private float jitterFreq;
        private float jitterPhase;
        private float sideMul;
        private float sideSpin;
        private float swirlMul;
        private float axisX;
        private float axisY;
        private float axisZ;
        private float orbitPhase;

        private MagicRt() {
        }

        public double getInitSpeed() {
            return this.initSpeed;
        }

        public float getBlinkPeriodMul() {
            return this.blinkPeriodMul;
        }

        public float getBlinkAmpMul() {
            return this.blinkAmpMul;
        }

        public float getBlinkPhaseOffset() {
            return this.blinkPhaseOffset;
        }

        public float getJitterFreq() {
            return this.jitterFreq;
        }

        public float getJitterPhase() {
            return this.jitterPhase;
        }

        public float getSideMul() {
            return this.sideMul;
        }

        public float getSideSpin() {
            return this.sideSpin;
        }

        public float getSwirlMul() {
            return this.swirlMul;
        }

        public float getAxisX() {
            return this.axisX;
        }

        public float getAxisY() {
            return this.axisY;
        }

        public float getAxisZ() {
            return this.axisZ;
        }

        public float getOrbitPhase() {
            return this.orbitPhase;
        }
    }
}

