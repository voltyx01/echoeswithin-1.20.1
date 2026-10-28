package com.voltyx.efw.gore.particle.common.alpha;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaCommon;
import com.voltyx.efw.gore.particle.state.BloodMagic;
import javax.annotation.Nullable;
final class BloodAlphaMagic {
    static final float MAGIC_FADE_START = 0.4f;
    static final float MAGIC_BLINK_PERIOD = 12.0f;
    static final float MAGIC_BLINK_AMP_MIN = 0.12f;
    static final float MAGIC_BLINK_AMP_MAX = 0.88f;
    static final float MAGIC_BLINK_RAMP = 2.35f;
    static final float MAGIC_BLINK_SHAPE = 1.4f;
    static final float MAGIC_BLINK_JITTER = 0.18f;

    BloodAlphaMagic() {
    }

    static float magicAlpha(int age, int maxAge) {
        return BloodAlphaCommon.envelopeAlpha(age, maxAge, 0.4f);
    }

    static boolean clearWaterFade(@Nullable ParticleBlood p) {
        if (!BloodMagic.isMagic(p)) {
            return false;
        }
        p.cache.fade.waterStartAge = -1;
        p.cache.fade.waterTicks = 0;
        return true;
    }

    static float blinkAlpha(@Nullable ParticleBlood p) {
        BloodMagic.MagicRt rt;
        if (p == null) {
            return 1.0f;
        }
        if (!BloodMagic.isMagic(p)) {
            return 1.0f;
        }
        double mx = p.motionX;
        double my = p.motionY;
        double mz = p.motionZ;
        double speed = Math.sqrt(mx * mx + my * my + mz * mz);
        float slow = BloodMagic.magicSlowProgress01(speed, (rt = BloodMagic.magicRt(p, speed)).getInitSpeed());
        if (slow <= 1.0E-6f) {
            return 1.0f;
        }
        float base = Util.smoothstep01(slow);
        float ramp = (float)Math.pow(base, 2.35f);
        float amp = 0.12f + 0.76f * ramp;
        amp *= rt.getBlinkAmpMul();
        amp = Util.clamp01(amp);
        float period = Math.max(2.0f, 12.0f * rt.getBlinkPeriodMul());
        double omega = Math.PI * 2 / (double)period;
        double jitter = (double)(0.18f * ramp) * Math.sin((double)p.getAge() * (double)rt.getJitterFreq() + (double)rt.getJitterPhase());
        double phase = ((double)p.getAge() + (double)(p.dripSeed * 20.0f)) * omega + (double)p.groundRot + (double)rt.getBlinkPhaseOffset() + jitter;
        float soft = Util.clamp01((float)(0.5 - 0.5 * Math.cos(2.0 * phase)));
        float sharp = (float)Math.abs(Math.sin(phase));
        sharp = Util.smoothstep01(sharp);
        float shape = (float)Math.pow(Util.clamp01(ramp), 1.4f);
        float pulse = soft * (1.0f - shape) + sharp * shape;
        return Util.clamp01(1.0f - amp * pulse);
    }
}

