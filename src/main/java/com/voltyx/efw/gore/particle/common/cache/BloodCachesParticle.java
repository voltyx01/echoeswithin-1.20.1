package com.voltyx.efw.gore.particle.common.cache;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.amalgamation.BloodAmalgamationGround;
import com.voltyx.efw.gore.particle.common.cache.BloodCachesCommon;
import com.voltyx.efw.gore.particle.state.BloodSlimy;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
public class BloodCachesParticle {
    public static void captureBillboard(@Nonnull ParticleBlood p, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        BloodCachesCommon.setBillboard(p.cache.billboard, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, ParticleBlood.getInterpX(), ParticleBlood.getInterpY(), ParticleBlood.getInterpZ());
    }

    public static void invalidateView(@Nonnull ParticleBlood p) {
        p.cache.view.x = Double.NaN;
        p.cache.view.y = Double.NaN;
        p.cache.view.z = Double.NaN;
    }

    public static void invalidateShape(@Nonnull ParticleBlood p) {
        p.cache.shape.scale = Float.NaN;
        p.cache.shape.drip = Float.NaN;
        p.cache.shape.amalgam = Float.NaN;
    }

    public static void resetSupport(@Nonnull ParticleBlood p) {
        p.cache.support.frac = 1.0f;
        p.cache.support.airBelow = false;
    }

    public static void resetGate(@Nonnull ParticleBlood p) {
        p.cache.gate.has = false;
        p.cache.gate.open = false;
        p.cache.gate.part = 0;
    }

    public static void resetModelFade(@Nonnull ParticleBlood p) {
        p.cache.fade.modelStartAge = -1;
        p.cache.fade.modelTicks = 0;
    }

    public static void resetWaterFade(@Nonnull ParticleBlood p) {
        p.cache.fade.waterStartAge = -1;
        p.cache.fade.waterTicks = 0;
    }

    public static boolean needsShape(@Nullable ParticleBlood p) {
        if (p == null) {
            return true;
        }
        if (BloodSlimy.shouldForcePolyRebuild(p)) {
            return true;
        }
        if (BloodSlimy.isRapidSurfacePopAnimating(p)) {
            return true;
        }
        if (p.fallingDripActive) {
            return true;
        }
        if (p.isStuck && p.stuckFace == Direction.DOWN && p.ceilingDripEnabled && p.ceilingDripStartAge >= 0) {
            return true;
        }
        if (p.isGroundTop() && BloodAmalgamationGround.enabled()) {
            if (p.amalgamAnimTicks > 0) {
                return true;
            }
            if (Float.isNaN(p.cache.shape.amalgam)) {
                return true;
            }
            if (Math.abs(p.amalgamVisualMass - p.cache.shape.amalgam) > 0.05f) {
                return true;
            }
        }
        if (Float.isNaN(p.cache.shape.scale) || Float.isNaN(p.cache.shape.drip)) {
            return true;
        }
        return Math.abs(p.getScale() - p.cache.shape.scale) > 0.001f || Math.abs(p.dripAmount - p.cache.shape.drip) > 0.001f;
    }
}

