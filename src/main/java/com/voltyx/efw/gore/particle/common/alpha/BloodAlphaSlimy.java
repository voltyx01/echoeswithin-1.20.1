package com.voltyx.efw.gore.particle.common.alpha;

import com.voltyx.efw.gore.json.BloodStyle;
import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaCommon;
import javax.annotation.Nullable;
final class BloodAlphaSlimy {
    static final float SLIMY_FADE_START = 0.8f;
    static final float SLIMY_WATER_FADE_MUL = 1.35f;

    BloodAlphaSlimy() {
    }

    static float slimyAlpha(int age, int maxAge) {
        return BloodAlphaCommon.envelopeAlpha(age, maxAge, 0.8f);
    }

    static int waterFadeTicks(@Nullable ParticleBlood p, int baseTicks) {
        int base = Math.max(1, baseTicks);
        if (p == null || ParticleBlood.normalizeWeight(p.fluidWeight) != BloodStyle.SLIMY) {
            return base;
        }
        int scaled = Math.round((float)base * 1.35f);
        return Math.max(base, scaled);
    }
}

