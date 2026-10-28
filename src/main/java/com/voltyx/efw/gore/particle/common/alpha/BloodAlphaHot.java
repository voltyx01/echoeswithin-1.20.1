package com.voltyx.efw.gore.particle.common.alpha;

import com.voltyx.efw.gore.json.BloodStyle;
import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.Util;
import javax.annotation.Nullable;
public class BloodAlphaHot {
    public static float hotProgress(@Nullable ParticleBlood p, float partialTicks) {
        if (p == null || p.hotSurfaceStartAge < 0) {
            return 0.0f;
        }
        int start = p.hotSurfaceStartAge;
        int end = Math.max(start + 1, p.getMaxAge());
        float t = ((float)p.getAge() + partialTicks - (float)start) / (float)(end - start);
        return Util.smoothstep01(Util.clamp01(t));
    }

    public static float hotAlpha(@Nullable ParticleBlood p, float partialTicks) {
        if (p == null) {
            return 1.0f;
        }
        float remain = 1.0f - BloodAlphaHot.hotProgress(p, partialTicks);
        if (ParticleBlood.normalizeWeight(p.fluidWeight) == BloodStyle.HEAVY) {
            return Util.clamp01(remain);
        }
        return Util.clamp01(remain * remain);
    }
}

