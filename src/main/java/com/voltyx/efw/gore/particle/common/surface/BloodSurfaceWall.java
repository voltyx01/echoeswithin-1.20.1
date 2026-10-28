package com.voltyx.efw.gore.particle.common.surface;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.BloodTuning;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.amalgamation.BloodAmalgamationWall;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceAttach;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceGround;
import com.voltyx.efw.gore.particle.state.BloodHeavy;
import net.minecraft.world.phys.Vec3;
public class BloodSurfaceWall {
    public static final float DRIP_DETACH_MIN_ALPHA = 0.5f;
    private static final int WALL_STRETCH_TIME_TICKS = -1;
    static final int WATER_WASH_FADE_TICKS = 30;
    static final float DRIP_EXTRA = 3.0f;

    static float drip(float particleScale, float ease, float dripSeed) {
        float s = 0.1f * particleScale;
        return s * 3.0f * ease * dripSeed;
    }

    public static void update(ParticleBlood p) {
        int dt;
        boolean allowAmalgam;
        if (p == null) {
            return;
        }
        if (p.stuckStartAge < 0) {
            p.stuckStartAge = p.getAge();
        }
        if (BloodHeavy.isHeavy(p)) {
            float baseScale = BloodSurfaceGround.scale(p.spawnScale, 0.0f, false);
            p.setScale(baseScale);
            p.dripAmount = 0.0f;
            p.sideDetachAge = -1;
            return;
        }
        float baseScale = BloodSurfaceGround.scale(p.spawnScale, 0.0f, false);
        p.setScale(baseScale);
        float t = Util.progress01(p.getAge(), p.stuckStartAge, -1, p.getMaxAge());
        float ease = BloodSurfaceGround.easeOut(t);
        float baseDrip = BloodSurfaceWall.drip(baseScale, ease, p.dripSeed);
        baseDrip *= p.wallExtendMul;
        if (p.isAmalgamConsuming()) {
            int start = p.amalgamConsumeStartAge;
            int dur = Math.max(1, p.amalgamConsumeDurationTicks);
            int elapsed = p.getAge() - start;
            if (elapsed < 0) {
                elapsed = 0;
            }
            float ct = Util.clamp01((float)elapsed / (float)dur);
            float e = Util.easeOutCubic01(ct);
            float retract = 1.0f - e;
            float startDrip = Math.max(0.0f, p.amalgamConsumeStartDrip);
            if (startDrip <= 1.0E-5f) {
                startDrip = baseDrip;
            }
            p.dripAmount = startDrip * (0.15f + 0.85f * retract);
            p.sideDetachAge = -1;
            return;
        }
        boolean bl = allowAmalgam = BloodAmalgamationWall.enabled() && BloodAmalgamationWall.allow(p.getAmalgamationPolicy(), p.fluidWeight);
        if (!allowAmalgam) {
            if (p.amalgamMass != 1.0f || p.amalgamVisualMass != 1.0f || p.amalgamAnimTicks != 0 || p.amalgamScaleMul != 1.0f || p.amalgamLastMergeAge != -1) {
                p.resetAmalgamState(false);
            }
            p.dripAmount = baseDrip;
            return;
        }
        if (p.amalgamAnimTicks > 0) {
            p.amalgamVisualMass += (p.amalgamMass - p.amalgamVisualMass) * 0.22f;
            --p.amalgamAnimTicks;
            if (p.amalgamAnimTicks <= 0) {
                p.amalgamVisualMass = p.amalgamMass;
            }
        } else {
            p.amalgamVisualMass = p.amalgamMass;
        }
        float targetMul = BloodAmalgamationWall.dripMul(p.amalgamVisualMass);
        p.amalgamScaleMul += (targetMul - p.amalgamScaleMul) * 0.22f;
        float dripMul = p.amalgamScaleMul;
        if (p.amalgamLastMergeAge >= 0 && (dt = p.getAge() - p.amalgamLastMergeAge) >= 0 && dt < 6) {
            float tt = (float)dt / 6.0f;
            float pulse = 1.0f + (1.0f - Util.smoothstep01(tt)) * 0.08f;
            dripMul *= pulse;
        }
        if (dripMul < 1.0f) {
            dripMul = 1.0f;
        }
        if (dripMul > 2.25f) {
            dripMul = 2.25f;
        }
        p.dripAmount = baseDrip * dripMul;
    }

    public static void updateDetach(ParticleBlood p) {
        if (p == null) {
            return;
        }
        if (!BloodTuning.isWallFace(p.stuckFace)) {
            p.sideDetachAge = -1;
            return;
        }
        if (!BloodTuning.dripEnabledForHost(p.cache.host.base)) {
            p.sideDetachAge = -1;
        }
    }

    public static void startWaterFade(ParticleBlood p, Vec3 onPlane) {
        if (p == null) {
            return;
        }
        if (!BloodTuning.isWallFace(p.stuckFace)) {
            return;
        }
        if (p.cache.fade.waterStartAge >= 0) {
            return;
        }
        if (BloodSurfaceAttach.wallInWater(p.getParticleWorld(), p.stuckPos, p.stuckFace, onPlane)) {
            p.cache.fade.waterStartAge = p.getAge();
            p.cache.fade.waterTicks = 30;
            int needAge = p.cache.fade.waterStartAge + p.cache.fade.waterTicks + 1;
            if (p.getMaxAge() < needAge) {
                p.setMaxAge(needAge);
            }
            p.sideDetachAge = -1;
        }
    }

    public static void postAlpha(ParticleBlood p) {
        if (p == null || !p.isStuck) {
            return;
        }
        if (!BloodTuning.isWallFace(p.stuckFace)) {
            p.sideDetachAge = -1;
            return;
        }
        if (!BloodTuning.dripEnabledForHost(p.cache.host.base)) {
            p.sideDetachAge = -1;
            return;
        }
        if (p.sideDetachAge >= 0 && p.getAge() >= p.sideDetachAge) {
            if (p.getAlpha() >= 0.499999f) {
                BloodSurfaceAttach.detach(p);
            } else {
                p.sideDetachAge = -1;
            }
        }
    }

    public static boolean unsupported(ParticleBlood p) {
        if (p == null) {
            return false;
        }
        if (p.stickMode == ParticleBlood.StickMode.MODEL && BloodTuning.isWallFace(p.stuckFace) && p.cache.support.airBelow && p.cache.support.frac <= 0.02f) {
            p.setExpired();
            return true;
        }
        return false;
    }
}

