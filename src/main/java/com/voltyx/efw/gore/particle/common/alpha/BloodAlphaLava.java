package com.voltyx.efw.gore.particle.common.alpha;

import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaCommon;
import com.voltyx.efw.gore.particle.state.liquid.lava.BloodLava;
import javax.annotation.Nullable;
public class BloodAlphaLava {
    public static float lavaCarryAlpha(float initialAlpha, int age, int maxAge, boolean heavy) {
        float cap = heavy ? 0.9f : 0.9f;
        return BloodAlphaCommon.carryAlpha(initialAlpha, age, maxAge, 0.9f, cap);
    }

    public static float lavaBaseAlpha(float carriedAlpha, int age, int maxAge, boolean heavy) {
        float cap = heavy ? 0.9f : 0.9f;
        return BloodAlphaCommon.baseAlpha(carriedAlpha, age, maxAge, cap);
    }

    public static float lavaSurfaceAlpha(int age, int maxAge, int growStartAge, boolean heavy) {
        float remain = BloodAlphaCommon.lifeRemain(age, maxAge, growStartAge);
        return heavy ? remain : Util.clamp01(remain * remain);
    }

    public static float lavaExitAlpha(int age, float startAlpha, int startAge, int blendTicks) {
        if (startAge < 0 || blendTicks <= 0) {
            return 1.0f;
        }
        int dt = age - startAge;
        if (dt <= 0) {
            return Util.clamp01(startAlpha);
        }
        if (dt >= blendTicks) {
            return 1.0f;
        }
        float t = Util.smoothstep01(Util.clamp01((float)dt / (float)blendTicks));
        return Util.clamp01(startAlpha + (1.0f - startAlpha) * t);
    }

    public static void startLavaExitFade(@Nullable BloodLava p) {
        if (p == null) {
            return;
        }
        if (p.isHeavyInLava()) {
            p.clearSurfaceExitAlphaBlend();
            return;
        }
        if (p.getSurfaceGrowStartAge() < 0) {
            p.clearSurfaceExitAlphaBlend();
            return;
        }
        int ticks = 5;
        if (ticks <= 0) {
            p.clearSurfaceExitAlphaBlend();
            return;
        }
        float start = BloodAlphaLava.lavaSurfaceAlpha(p.getAge(), p.getMaxAge(), p.getSurfaceGrowStartAge(), false);
        if (start >= 0.999f) {
            p.clearSurfaceExitAlphaBlend();
            return;
        }
        p.startSurfaceExitAlphaBlend(start, ticks);
    }

    public static boolean updateLavaAlpha(@Nullable BloodLava p) {
        if (p == null) {
            return false;
        }
        boolean heavy = p.isHeavyInLava();
        float alpha = BloodAlphaLava.lavaBaseAlpha(p.getCarriedAlphaMul(), p.getAge(), p.getMaxAge(), heavy);
        if (p.isOnSurface() && p.getSurfaceGrowStartAge() >= 0) {
            alpha *= BloodAlphaLava.lavaSurfaceAlpha(p.getAge(), p.getMaxAge(), p.getSurfaceGrowStartAge(), heavy);
        } else if (p.hasSurfaceExitAlphaBlend()) {
            alpha *= p.getSurfaceExitAlphaMul01();
        }
        if (heavy && (alpha *= 1.0f) > 0.9f) {
            alpha = 0.9f;
        }
        p.setParticleAlpha(Util.clamp01(alpha));
        if (p.getParticleAlpha() <= 0.001f) {
            p.expireAndUntrack();
            return false;
        }
        return true;
    }
}

