package com.voltyx.efw.gore.particle.common.alpha;

import com.voltyx.efw.gore.json.BloodStyle;
import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaCommon;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaMagic;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaSlimy;
import javax.annotation.Nullable;
public final class BloodAlpha {
    public static float styleAlpha(@Nullable BloodStyle style, int age, int maxAge) {
        BloodStyle s = ParticleBlood.normalizeWeight(style);
        if (s == BloodStyle.MAGIC) {
            return BloodAlphaMagic.magicAlpha(age, maxAge);
        }
        if (s == BloodStyle.SLIMY) {
            return BloodAlphaSlimy.slimyAlpha(age, maxAge);
        }
        return BloodAlphaCommon.lifeAlpha(age, maxAge);
    }

    public static float predictAlpha(@Nullable ParticleBlood p, int age, int maxAge) {
        BloodStyle style = p != null ? p.fluidWeight : BloodStyle.LIGHT;
        return BloodAlpha.styleAlpha(style, age, maxAge);
    }
}

