package com.voltyx.efw.gore.particle.common.surface;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.amalgamation.BloodAmalgamationGround;
import com.voltyx.efw.gore.particle.state.BloodHeavy;
public class BloodSurfaceGround {
    static final float GROW_MUL = 1.8f;
    private static final int GROUND_GROW_TIME_TICKS = -1;

    static float easeOut(float t01) {
        float t = Util.clamp01(t01);
        float inv = 1.0f - t;
        return 1.0f - inv * inv;
    }

    static float scale(float spawnScale, float ease, boolean allowGrow) {
        if (!allowGrow) {
            return spawnScale;
        }
        float growMul = 1.8f;
        if (growMul < 1.0f) {
            growMul = 1.0f;
        }
        return spawnScale * (1.0f + (growMul - 1.0f) * ease);
    }

    public static void update(ParticleBlood p) {
        if (p == null) {
            return;
        }
        if (p.stuckStartAge < 0) {
            p.stuckStartAge = p.getAge();
        }
        if (p.isGroundTop() && p.isAmalgamConsuming()) {
            int start = p.amalgamConsumeStartAge;
            int dur = Math.max(1, p.amalgamConsumeDurationTicks);
            int elapsed = p.getAge() - start;
            if (elapsed < 0) {
                elapsed = 0;
            }
            float t = Util.clamp01((float)elapsed / (float)dur);
            float e = Util.easeOutCubic01(t);
            float shrink = 1.0f - e;
            float s0 = Math.max(0.001f, p.amalgamConsumeStartScale);
            float s = s0 * (0.15f + 0.85f * shrink);
            p.setScale(s);
            p.dripAmount = 0.0f;
            p.sideDetachAge = -1;
            return;
        }
        if (BloodHeavy.isHeavyFluid(p.fluidWeight)) {
            if (p.amalgamMass != 1.0f || p.amalgamVisualMass != 1.0f || p.amalgamAnimTicks != 0 || p.amalgamScaleMul != 1.0f || p.amalgamLastMergeAge != -1) {
                p.resetAmalgamState(false);
            }
            p.setScale(p.spawnScale);
            p.dripAmount = 0.0f;
            p.sideDetachAge = -1;
            return;
        }
        float t = Util.progress01(p.getAge(), p.stuckStartAge, -1, p.getMaxAge());
        float ease = BloodSurfaceGround.easeOut(t);
        float baseScale = BloodSurfaceGround.scale(p.spawnScale, ease, true);
        baseScale = p.spawnScale + (baseScale - p.spawnScale) * p.groundExtendMul;
        if (p.isGroundTop()) {
            boolean allowAmalgam = BloodAmalgamationGround.allow(p.getAmalgamationPolicy(), p.fluidWeight);
            if (!allowAmalgam) {
                if (p.amalgamMass != 1.0f || p.amalgamVisualMass != 1.0f || p.amalgamAnimTicks != 0 || p.amalgamScaleMul != 1.0f || p.amalgamLastMergeAge != -1) {
                    p.resetAmalgamState(false);
                }
            } else {
                int dt;
                if (p.amalgamAnimTicks > 0) {
                    p.amalgamVisualMass += (p.amalgamMass - p.amalgamVisualMass) * 0.22f;
                    --p.amalgamAnimTicks;
                    if (p.amalgamAnimTicks <= 0) {
                        p.amalgamAnimTicks = 0;
                        p.amalgamVisualMass = p.amalgamMass;
                    }
                } else {
                    p.amalgamVisualMass = p.amalgamMass;
                }
                float targetMul = BloodAmalgamationGround.scaleMul(p.amalgamVisualMass);
                p.amalgamScaleMul += (targetMul - p.amalgamScaleMul) * 0.22f;
                float mul = p.amalgamScaleMul;
                if (p.amalgamLastMergeAge >= 0 && (dt = p.getAge() - p.amalgamLastMergeAge) >= 0 && dt < 6) {
                    float tt = (float)dt / 6.0f;
                    float pulse = 1.0f + (1.0f - Util.smoothstep01(tt)) * 0.07f;
                    mul *= pulse;
                }
                if (mul < 1.0f) {
                    mul = 1.0f;
                }
                if (mul > 1.75f) {
                    mul = 1.75f;
                }
                baseScale *= mul;
            }
        }
        p.setScale(baseScale);
        p.dripAmount = 0.0f;
        p.sideDetachAge = -1;
    }
}

