package com.voltyx.efw.gore.particle.common.surface;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.BloodTuning;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.cache.BloodCachesParticle;
import com.voltyx.efw.gore.particle.common.geometry.BloodGeometry;
import com.voltyx.efw.gore.particle.common.geometry.BloodGeometryCeiling;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceAttach;
import com.voltyx.efw.gore.particle.state.BloodHeavy;
import net.minecraft.core.Direction;
public class BloodSurfaceCeiling {
    static final float DRIP_CHANCE = 0.4f;
    public static final int COOLDOWN_MIN_TICKS = 20;
    public static final int COOLDOWN_MAX_TICKS = 80;
    public static final int GROW_MIN_TICKS = 40;
    public static final int GROW_MAX_TICKS = 160;
    public static final float BODY_MIN = 0.03f;
    public static final float BODY_MAX = 0.75f;
    public static final double DROP_SPEED_MIN = 0.04;
    public static final double DROP_SPEED_MAX = 0.08;
    public static final double DRIP_TOP_OVERLAP = 1.5E-4;

    static float capHeight(ParticleBlood p) {
        return p != null ? BloodGeometryCeiling.computeCapHeight(p) : 0.03f;
    }

    static double dripTipY(ParticleBlood p, float bodyLen) {
        return p != null ? BloodGeometryCeiling.computeDripTipY(p, bodyLen) : 0.0;
    }

    static double dropSpeed(ParticleBlood p) {
        if (p == null) {
            return 0.04;
        }
        float t = Util.clamp01(p.planCeilingDropSpeed01);
        return 0.04 + (double)t * 0.04;
    }

    static void capture(ParticleBlood p) {
        if (p == null) {
            return;
        }
        p.resetCeilingDripRuntime();
        if (BloodHeavy.isHeavy(p)) {
            return;
        }
        if (!p.isStuck || p.stuckFace != Direction.DOWN) {
            return;
        }
        if (!BloodTuning.dripEnabledForHost(p.cache.host.base)) {
            return;
        }
        if (p.ceilingDripConsumed) {
            return;
        }
        if (p.planCeilingRoll >= 0.4f) {
            return;
        }
        p.ceilingDripEnabled = true;
        int delay = Math.max(0, p.planCeilingDelayTicks);
        p.ceilingNextDripAge = p.getAge() + delay;
        int hang = Math.max(0, p.planCeilingHangTicks);
        int needAge = p.getAge() + delay + Math.max(1, p.planCeilingGrowTicks) + hang + 4;
        if (p.getMaxAge() < needAge) {
            p.setMaxAge(needAge);
        }
    }

    public static void update(ParticleBlood p) {
        float cubic;
        if (p == null) {
            return;
        }
        if (!p.isStuck || p.stuckFace != Direction.DOWN) {
            return;
        }
        if (BloodHeavy.isHeavyFluid(p.fluidWeight)) {
            if (p.ceilingDripEnabled || p.ceilingNextDripAge >= 0 || p.ceilingDripStartAge >= 0) {
                p.resetCeilingDripRuntime();
            } else {
                p.dripAmount = 0.0f;
            }
            return;
        }
        if (!p.ceilingDripEnabled || p.ceilingDripConsumed) {
            p.dripAmount = 0.0f;
            return;
        }
        if (!BloodTuning.dripEnabledForHost(p.cache.host.base)) {
            p.resetCeilingDripRuntime();
            return;
        }
        int age = p.getAge();
        if (p.ceilingDripStartAge < 0) {
            if (p.ceilingNextDripAge < 0) {
                p.ceilingNextDripAge = age + Math.max(0, p.planCeilingDelayTicks);
            }
            if (age < p.ceilingNextDripAge) {
                p.dripAmount = 0.0f;
                return;
            }
            p.ceilingDripStartAge = age;
            p.ceilingDripTargetLen = p.planCeilingBodyLen;
            p.ceilingDripBuildTicks = Math.max(1, p.planCeilingGrowTicks);
            p.ceilingNextDripAge = -1;
            p.dripAmount = Math.min(p.ceilingDripTargetLen, 0.002f);
            BloodCachesParticle.invalidateShape(p);
            return;
        }
        int dur = Math.max(1, p.ceilingDripBuildTicks);
        int elapsed = age - p.ceilingDripStartAge;
        if (elapsed < 0) {
            elapsed = 0;
        }
        float t = Util.clamp01((float)elapsed / (float)dur);
        float viscRaw = Util.clamp01(p.viscosity01);
        float visc = Util.smoothstep01(viscRaw);
        float smooth = Util.smoothstep01(t);
        float ease = smooth + ((cubic = Util.easeOutCubic01(t)) - smooth) * visc;
        float len = p.ceilingDripTargetLen * ease;
        if (len < 0.002f) {
            len = 0.002f;
        }
        if (len > p.ceilingDripTargetLen) {
            len = p.ceilingDripTargetLen;
        }
        p.dripAmount = len;
    }

    public static void postAlpha(ParticleBlood p) {
        int hang;
        int dur;
        if (p == null) {
            return;
        }
        if (!p.isStuck || p.stuckFace != Direction.DOWN) {
            return;
        }
        if (!p.ceilingDripEnabled || p.ceilingDripConsumed) {
            return;
        }
        if (p.ceilingDripStartAge < 0) {
            return;
        }
        int age = p.getAge();
        if (age - p.ceilingDripStartAge < (dur = Math.max(1, p.ceilingDripBuildTicks)) + (hang = Math.max(0, p.planCeilingHangTicks))) {
            return;
        }
        BloodSurfaceCeiling.dropSelf(p);
    }

    static void dropSelf(ParticleBlood p) {
        float body;
        if (p == null) {
            return;
        }
        if (!p.isStuck || p.stuckFace != Direction.DOWN) {
            return;
        }
        float capH = BloodSurfaceCeiling.capHeight(p);
        float totalLen = capH + (body = Math.max(0.0f, p.dripAmount));
        if (totalLen < 1.0E-6f) {
            totalLen = capH;
        }
        double tipY = BloodSurfaceCeiling.dripTipY(p, body);
        BloodSurfaceAttach.clearStuck(p);
        p.resetCeilingDripRuntime();
        p.ceilingDripConsumed = true;
        p.fallingDripActive = true;
        p.fallingDripStartAge = p.getAge();
        p.fallingDripShrinkTicks = Math.max(1, p.planFallingShrinkTicks);
        p.fallingDripStartLen = totalLen;
        p.fallingDripLen = totalLen;
        p.dripAmount = 0.0f;
        p.noAirFlutter = true;
        p.detachWobbleTicks = 0;
        BloodSurfaceAttach.beginFall(p, 0, false);
        p.setPosition(p.posX, tipY, p.posZ);
        double v = BloodSurfaceCeiling.dropSpeed(p);
        p.motionX = 0.0;
        p.motionZ = 0.0;
        p.motionY = -v;
        int needAge = p.getAge() + Math.max(20, p.fallingDripShrinkTicks) + 60;
        if (p.getMaxAge() < needAge) {
            p.setMaxAge(needAge);
        }
        BloodCachesParticle.invalidateShape(p);
        BloodCachesParticle.invalidateView(p);
        BloodGeometry.rebuildDecalPolys(p);
        BloodGeometry.cacheRenderPos(p);
        BloodSurfaceAttach.syncPrev(p);
    }
}

