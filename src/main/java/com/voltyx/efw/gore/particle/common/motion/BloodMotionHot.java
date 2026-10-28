package com.voltyx.efw.gore.particle.common.motion;

import com.voltyx.efw.gore.particle.common.GoreUtil;

import com.voltyx.efw.gore.ModConfigurationClient;
import com.voltyx.efw.gore.json.BloodStyle;
import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlpha;
import com.voltyx.efw.gore.particle.common.motion.BloodMotion;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceAttach;
import com.voltyx.efw.gore.particle.state.BloodHotBlocks;
import com.voltyx.efw.gore.particle.state.BloodMagic;
import com.voltyx.efw.gore.particle.state.BloodSlimy;
import com.voltyx.efw.gore.particle.state.liquid.BloodLiquidUtil;
import net.minecraft.util.RandomSource;
import com.voltyx.efw.gore.particle.common.GoreMaterial;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
public class BloodMotionHot {
    private static final double FREE_UPWARD_SKIP = 0.02;
    private static final double FEET_EPS = 1.0E-4;
    private static final double HOT_BLOCK_BOUNCE_MAX_HEIGHT = 0.7;
    private static final JumpProfile GROUND_LIGHT = new JumpProfile(10, 6, 14, 22, 6, 0.18f, 0.82f, 0.22f, 0.06, 0.086, 0.01, 0.045);
    private static final JumpProfile GROUND_SLIMY = new JumpProfile(8, 5, 12, 18, 10, 0.22f, 0.84f, 0.2f, 0.056, 0.08, 0.016, 0.05);
    private static final JumpProfile GROUND_HEAVY = new JumpProfile(16, 10, 20, 30, 4, 0.1f, 0.72f, 0.28f, 0.042, 0.06, 0.005, 0.032);
    private static final LiftProfile LIFT_MAGIC = new LiftProfile(24, 0.072, 0.098, 0.0035, 0.03, 12, 18);
    private static final BurstProfile BURST_LIGHT = new BurstProfile(12, 18, 3, 5, 0.0035, 0.0095, 0.003, 0.009, 0.0035, 0.055, 0.22);
    private static final BurstProfile BURST_SLIMY = new BurstProfile(14, 20, 4, 6, 0.002, 0.0065, 0.002, 0.0075, 0.006, 0.06, 0.2);
    private static final BurstProfile BURST_HEAVY = new BurstProfile(10, 14, 2, 4, 0.0015, 0.004, 0.0018, 0.005, 0.0015, 0.035, 0.16);
    private static final BurstProfile BURST_MAGIC = new BurstProfile(12, 18, 3, 5, 8.0E-4, 0.003, 0.0045, 0.012, 0.0012, 0.03, 0.26);

    public static boolean tickGround(ParticleBlood p) {
        if (p == null || p.isExpiredSafe()) {
            return false;
        }
        if (!ModConfigurationClient.hotBlocks.hotBlocks) {
            return false;
        }
        if (!BloodHotBlocks.isHotGroundTopHost(p)) {
            return false;
        }
        BloodHotBlocks.HotStyle style = BloodHotBlocks.resolveStyle(p);
        if (style == BloodHotBlocks.HotStyle.MAGIC) {
            return false;
        }
        if (p.isAmalgamConsuming()) {
            return false;
        }
        if (p.amalgamMass > 1.05f || p.amalgamVisualMass > 1.05f) {
            return false;
        }
        JumpProfile prof = BloodMotionHot.jumpProfile(style);
        BloodMotionHot.primeLife(p, style);
        if (p.getAge() >= p.getMaxAge()) {
            return false;
        }
        if (p.hotBounceCooldownTicks > 0) {
            return false;
        }
        if (p.stuckStartAge < 0) {
            return false;
        }
        int restTicks = p.getAge() - p.stuckStartAge;
        if (restTicks < prof.minRestTicks) {
            return false;
        }
        float consume01 = BloodHotBlocks.groundConsume01(p, 0.0f);
        if (consume01 > prof.jumpConsumeMax) {
            return false;
        }
        float predictedAlpha = BloodAlpha.predictAlpha(p, p.getAge() + 4, p.getMaxAge());
        if (predictedAlpha < prof.jumpMinPredictedAlpha) {
            return false;
        }
        int seed = BloodMotionHot.hotSeed(p) & Integer.MAX_VALUE;
        int offset = seed % prof.jumpInterval;
        if ((p.getAge() + offset) % prof.jumpInterval != 0) {
            return false;
        }
        float gate = BloodMotionHot.noiseGate(p, 0.0f);
        float chance = prof.jumpChance * (0.7f + 0.3f * gate) * (1.0f - 0.35f * consume01);
        if (p.getRand().nextFloat() >= chance) {
            return false;
        }
        p.hotBounceCooldownTicks = BloodMotion.randomInclusive(p.getRand(), prof.cooldownMin, prof.cooldownMax);
        BloodSurfaceAttach.hotJump(p, prof.upMin, prof.upMax, prof.sideJitter, prof.maxHorizontal, prof.wobbleTicks);
        return true;
    }

    static void tickLift(ParticleBlood p) {
        if (p == null || p.isExpiredSafe()) {
            return;
        }
        if (!ModConfigurationClient.hotBlocks.hotBlocks) {
            return;
        }
        if (!BloodMagic.isMagic(p)) {
            return;
        }
        if (p.isStuck || p.fallingDripActive) {
            return;
        }
        if (p.hotBounceCooldownTicks > 0) {
            return;
        }
        if (p.motionY > 0.02) {
            return;
        }
        if (p.isInsideWaterNow() || p.isInsideLavaNow()) {
            return;
        }
        double probeHeight = Util.clamp(0.7, 0.05, 8.0);
        HotBelow hot = BloodMotionHot.findBelow(p, probeHeight);
        if (hot == null) {
            return;
        }
        if (BloodMotionHot.LIFT_MAGIC.chanceOneIn > 1 && p.getRand().nextInt(BloodMotionHot.LIFT_MAGIC.chanceOneIn) != 0) {
            return;
        }
        p.hotBounceCooldownTicks = BloodMotion.randomInclusive(p.getRand(), BloodMotionHot.LIFT_MAGIC.cooldownMin, BloodMotionHot.LIFT_MAGIC.cooldownMax);
        BloodMotionHot.freeLift(p, hot.gap, probeHeight);
    }

    public static void beginGroundBurst(ParticleBlood p) {
        BloodMotionHot.beginBurst(p, BloodMotionHot.burstProfile(p));
    }

    public static void beginLiftBurst(ParticleBlood p) {
        BloodMotionHot.beginBurst(p, BURST_MAGIC);
    }

    static void tickBurst(ParticleBlood p) {
        if (p.hotBurstTicks <= 0 || p.hotBurstTotal <= 0) {
            return;
        }
        if (p.isExpiredSafe()) {
            return;
        }
        if (p.isStuck || p.fallingDripActive) {
            return;
        }
        if (p.isInsideWaterNow() || p.isInsideLavaNow()) {
            p.hotBurstTicks = 0;
            p.hotBurstTotal = 0;
            return;
        }
        BurstProfile prof = BloodMotionHot.burstProfile(p);
        float k = Util.clamp01((float)p.hotBurstTicks / (float)Math.max(1, p.hotBurstTotal));
        double power = 0.7 + 0.3 * (double)Util.clamp01(p.hotVisualPower);
        double push = BloodMotion.lerp(prof.pushMin, prof.pushMax, k) * power;
        double up = BloodMotion.lerp(prof.upMin, prof.upMax, k) * power;
        p.motionX += (double)p.hotBurstDirX * push + (p.getRand().nextDouble() - 0.5) * prof.jitter;
        p.motionZ += (double)p.hotBurstDirZ * push + (p.getRand().nextDouble() - 0.5) * prof.jitter;
        if (BloodSlimy.isSlimy(p)) {
            double wob = Math.sin(((double)p.getAge() + (double)p.dripSeed * 10.0) * 0.85) * prof.jitter;
            p.motionX += -((double)p.hotBurstDirZ) * wob;
            p.motionZ += (double)p.hotBurstDirX * wob;
        } else if (BloodMagic.isMagic(p)) {
            p.motionX *= 0.985;
            p.motionZ *= 0.985;
        } else if (ParticleBlood.normalizeWeight(p.fluidWeight) == BloodStyle.HEAVY) {
            p.motionX *= 0.95;
            p.motionZ *= 0.95;
        }
        p.motionY += up;
        if (p.motionY > prof.maxUpward) {
            p.motionY = prof.maxUpward;
        }
        BloodMotion.clampHorizontal(p, prof.maxHorizontal);
        p.noAirFlutter = false;
        p.setOnGroundFlag(false);
    }

    private static void primeLife(ParticleBlood p, BloodHotBlocks.HotStyle style) {
        if (p.hotSurfaceStartAge >= 0) {
            return;
        }
        p.hotSurfaceStartAge = p.getAge();
        float lifeFactor = style == BloodHotBlocks.HotStyle.HEAVY ? 0.5f : 0.3f;
        int extraTicks = (int)Math.ceil((double)Math.max(1, p.getMaxAge()) * (double)lifeFactor);
        if (extraTicks < 1) {
            extraTicks = 1;
        }
        int needAge = p.hotSurfaceStartAge + extraTicks;
        if (p.getMaxAge() < needAge) {
            p.setMaxAge(needAge);
        }
    }

    private static void freeLift(ParticleBlood p, double gap, double probeHeight) {
        RandomSource rand = p.getRand();
        double heat01 = 1.0 - Util.clamp(gap / Math.max(0.05, probeHeight), 0.0, 1.0);
        double up = BloodMotionHot.LIFT_MAGIC.upMin + rand.nextDouble() * (BloodMotionHot.LIFT_MAGIC.upMax - BloodMotionHot.LIFT_MAGIC.upMin);
        up *= 0.9 + 0.25 * heat01;
        if (p.motionY < 0.0) {
            p.motionY *= 0.35;
        }
        p.motionY = p.motionY < up ? up : (p.motionY += up * 0.35);
        p.motionX += (rand.nextDouble() - 0.5) * BloodMotionHot.LIFT_MAGIC.sideJitter;
        p.motionZ += (rand.nextDouble() - 0.5) * BloodMotionHot.LIFT_MAGIC.sideJitter;
        p.motionX *= 0.96;
        p.motionZ *= 0.96;
        BloodMotion.clampHorizontal(p, BloodMotionHot.LIFT_MAGIC.maxHorizontal);
        p.noAirFlutter = false;
        p.idleTicks = 0;
        p.setOnGroundFlag(false);
        BloodMotionHot.primeLife(p, BloodHotBlocks.HotStyle.MAGIC);
        BloodMotionHot.beginLiftBurst(p);
    }

    private static HotBelow findBelow(ParticleBlood p, double maxGap) {
        Level w = p.getParticleWorld();
        if (w == null) {
            return null;
        }
        AABB bb = p.getBoundingBox();
        double feetY = bb.minY + 1.0E-4;
        int x = Mth.floor((double)p.posX);
        int z = Mth.floor((double)p.posZ);
        int yStart = Mth.floor((double)(feetY - 1.0E-6));
        int yEnd = Mth.floor((double)(feetY - maxGap - 1.0));
        for (int y = yStart; y >= yEnd; --y) {
            BlockState st;
            BlockPos bp = BlockPos.containing(x, y, z);
            if (!w.isLoaded(bp)) {
                return null;
            }
            try {
                st = w.getBlockState(bp);
            }
            catch (Throwable t) {
                return null;
            }
            if (w.isEmptyBlock(bp) || GoreMaterial.of(st) == GoreMaterial.AIR) continue;
            double topY = BloodMotionHot.topY(w, bp, st);
            double gap = feetY - topY;
            if (!BloodHotBlocks.isHotBlock(st)) {
                return null;
            }
            if (gap < -0.05) {
                return null;
            }
            if (gap > maxGap + 1.0E-6) {
                return null;
            }
            return new HotBelow(gap);
        }
        return null;
    }

    private static double topY(Level w, BlockPos pos, BlockState st) {
        try {
            AABB bb = GoreUtil.boundingBox(st, (BlockGetter)w, pos);
            return (double)((net.minecraft.core.Vec3i) pos).getY() + bb.maxY;
        }
        catch (Throwable throwable) {
            return (double)((net.minecraft.core.Vec3i) pos).getY() + 1.0;
        }
    }

    private static float noiseGate(ParticleBlood p, float partialTicks) {
        float age = (float)p.getAge() + partialTicks;
        int seed = BloodMotionHot.hotSeed(p);
        float px = (float)p.posX;
        float pz = (float)p.posZ;
        return Util.clamp01(0.5f + 0.5f * BloodLiquidUtil.noiseFbm(px * 0.9f + age * 0.05f, pz * 0.9f - age * 0.04f, seed + 9123));
    }

    private static int hotSeed(ParticleBlood p) {
        int s = 1;
        s = 31 * s + Float.floatToIntBits(p.dripSeed);
        s = 31 * s + Float.floatToIntBits(p.groundRot);
        s = 31 * s + Float.floatToIntBits(p.spawnScale);
        if (p.stuckPos != null) {
            s = 31 * s + p.stuckPos.hashCode();
        }
        return s;
    }

    private static JumpProfile jumpProfile(BloodHotBlocks.HotStyle style) {
        switch (style) {
            case SLIMY: {
                return GROUND_SLIMY;
            }
            case HEAVY: {
                return GROUND_HEAVY;
            }
        }
        return GROUND_LIGHT;
    }

    private static void beginBurst(ParticleBlood p, BurstProfile prof) {
        RandomSource rand = p.getRand();
        p.hotVisualTicks = p.hotVisualTotal = BloodMotion.randBetween(rand, prof.visualMin, prof.visualMax);
        p.hotBurstTicks = p.hotBurstTotal = BloodMotion.randBetween(rand, prof.burstMin, prof.burstMax);
        p.hotVisualPower = 0.92f + rand.nextFloat() * 0.22f;
        double dirX = p.motionX;
        double dirZ = p.motionZ;
        double lenSq = dirX * dirX + dirZ * dirZ;
        if (lenSq <= 1.0E-10) {
            double a = rand.nextDouble() * Math.PI * 2.0;
            dirX = Math.cos(a);
            dirZ = Math.sin(a);
        } else {
            double inv = 1.0 / Math.sqrt(lenSq);
            dirX *= inv;
            dirZ *= inv;
        }
        p.hotBurstDirX = (float)dirX;
        p.hotBurstDirZ = (float)dirZ;
    }

    private static BurstProfile burstProfile(ParticleBlood p) {
        if (BloodMagic.isMagic(p)) {
            return BURST_MAGIC;
        }
        if (BloodSlimy.isSlimy(p)) {
            return BURST_SLIMY;
        }
        BloodStyle style = ParticleBlood.normalizeWeight(p.fluidWeight);
        if (style == BloodStyle.HEAVY) {
            return BURST_HEAVY;
        }
        return BURST_LIGHT;
    }

    private static final class BurstProfile {
        final int visualMin;
        final int visualMax;
        final int burstMin;
        final int burstMax;
        final double pushMin;
        final double pushMax;
        final double upMin;
        final double upMax;
        final double jitter;
        final double maxHorizontal;
        final double maxUpward;

        BurstProfile(int visualMin, int visualMax, int burstMin, int burstMax, double pushMin, double pushMax, double upMin, double upMax, double jitter, double maxHorizontal, double maxUpward) {
            this.visualMin = visualMin;
            this.visualMax = visualMax;
            this.burstMin = burstMin;
            this.burstMax = burstMax;
            this.pushMin = pushMin;
            this.pushMax = pushMax;
            this.upMin = upMin;
            this.upMax = upMax;
            this.jitter = jitter;
            this.maxHorizontal = maxHorizontal;
            this.maxUpward = maxUpward;
        }
    }

    private static final class HotBelow {
        final double gap;

        HotBelow(double gap) {
            this.gap = gap;
        }
    }

    private static final class LiftProfile {
        final int chanceOneIn;
        final double upMin;
        final double upMax;
        final double sideJitter;
        final double maxHorizontal;
        final int cooldownMin;
        final int cooldownMax;

        LiftProfile(int chanceOneIn, double upMin, double upMax, double sideJitter, double maxHorizontal, int cooldownMin, int cooldownMax) {
            this.chanceOneIn = Math.max(1, chanceOneIn);
            this.upMin = upMin;
            this.upMax = Math.max(upMin, upMax);
            this.sideJitter = Math.max(0.0, sideJitter);
            this.maxHorizontal = Math.max(0.0, maxHorizontal);
            this.cooldownMin = Math.max(0, cooldownMin);
            this.cooldownMax = Math.max(this.cooldownMin, cooldownMax);
        }
    }

    private static final class JumpProfile {
        final int minRestTicks;
        final int jumpInterval;
        final int cooldownMin;
        final int cooldownMax;
        final int wobbleTicks;
        final float jumpChance;
        final float jumpConsumeMax;
        final float jumpMinPredictedAlpha;
        final double upMin;
        final double upMax;
        final double sideJitter;
        final double maxHorizontal;

        JumpProfile(int minRestTicks, int jumpInterval, int cooldownMin, int cooldownMax, int wobbleTicks, float jumpChance, float jumpConsumeMax, float jumpMinPredictedAlpha, double upMin, double upMax, double sideJitter, double maxHorizontal) {
            this.minRestTicks = Math.max(0, minRestTicks);
            this.jumpInterval = Math.max(1, jumpInterval);
            this.cooldownMin = Math.max(0, cooldownMin);
            this.cooldownMax = Math.max(this.cooldownMin, cooldownMax);
            this.wobbleTicks = Math.max(0, wobbleTicks);
            this.jumpChance = Util.clamp01(jumpChance);
            this.jumpConsumeMax = Util.clamp01(jumpConsumeMax);
            this.jumpMinPredictedAlpha = Util.clamp01(jumpMinPredictedAlpha);
            this.upMin = upMin;
            this.upMax = Math.max(upMin, upMax);
            this.sideJitter = Math.max(0.0, sideJitter);
            this.maxHorizontal = Math.max(0.0, maxHorizontal);
        }
    }
}

