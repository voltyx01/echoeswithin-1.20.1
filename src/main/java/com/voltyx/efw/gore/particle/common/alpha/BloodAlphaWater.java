package com.voltyx.efw.gore.particle.common.alpha;

import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaCommon;
import com.voltyx.efw.gore.particle.state.liquid.water.BloodWater;
import javax.annotation.Nullable;
public class BloodAlphaWater {
    public static float waterCarryAlpha(float initialAlpha, int age, int maxAge) {
        return BloodAlphaCommon.carryAlpha(initialAlpha, age, maxAge, 1.0f, 0.7f);
    }

    public static float waterBaseAlpha(float carriedAlpha, int age, int maxAge) {
        return BloodAlphaCommon.baseAlpha(carriedAlpha, age, maxAge, 0.7f);
    }

    public static boolean updateWaterAlpha(@Nullable BloodWater p) {
        if (p == null) {
            return false;
        }
        float alpha = BloodAlphaWater.waterBaseAlpha(p.getCarriedAlphaMul(), p.getAge(), p.getMaxAge());
        p.setParticleAlpha(Util.clamp01(alpha));
        if (p.getParticleAlpha() <= 0.001f) {
            p.expireAndUntrack();
            return false;
        }
        return true;
    }
}

