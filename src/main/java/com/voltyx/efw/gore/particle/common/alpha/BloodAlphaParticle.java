package com.voltyx.efw.gore.particle.common.alpha;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.BloodTuning;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlpha;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaCommon;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaMagic;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaSlimy;
import com.voltyx.efw.gore.particle.state.BloodMagic;
import javax.annotation.Nullable;
public class BloodAlphaParticle {
    private static final float WALL_MODEL_UNSUPPORTED_FADE_SUPPORT_FRAC = 0.4f;
    private static final int WALL_MODEL_UNSUPPORTED_FADE_TICKS = 20;

    public static void updateParticleAlpha(@Nullable ParticleBlood p) {
        if (p == null || p.isExpiredSafe()) {
            return;
        }
        BloodAlphaParticle.startUnsupportedFade(p);
        float alpha = BloodAlphaParticle.particleAlpha(p);
        if (alpha > p.alphaMonotonic) {
            alpha = p.alphaMonotonic;
        }
        p.alphaMonotonic = alpha;
        float out = alpha;
        if (BloodMagic.isMagic(p)) {
            out *= BloodAlphaMagic.blinkAlpha(p);
        }
        out *= BloodAlphaParticle.consumeFade(p);
        if (!Float.isFinite(out *= BloodAlphaParticle.waterFade(p))) {
            out = 0.0f;
        }
        p.setAlpha(Util.clamp01(out));
        if (alpha <= 1.0E-4f || BloodAlphaParticle.consumeDone(p) || BloodAlphaParticle.waterFadeDone(p)) {
            BloodAlphaCommon.expireNow(p);
        }
    }

    public static void startUnsupportedFade(ParticleBlood p) {
        float trigger;
        if (p.cache.fade.modelStartAge >= 0) {
            return;
        }
        if (!p.isStuckDecal()) {
            return;
        }
        if (p.stickMode != ParticleBlood.StickMode.MODEL) {
            return;
        }
        if (!BloodTuning.isWallFace(p.stuckFace)) {
            return;
        }
        if (!p.cache.support.airBelow) {
            return;
        }
        float support = Util.clamp01(p.cache.support.frac);
        if (support > (trigger = 0.4f) + 1.0E-6f) {
            return;
        }
        int ticks = 20;
        if (ticks < 0) {
            ticks = 0;
        }
        p.cache.fade.modelStartAge = p.getAge();
        if (ticks <= 0) {
            p.cache.fade.modelTicks = 0;
            return;
        }
        int remaining = Math.max(0, p.getMaxAge() - p.getAge());
        if (remaining > 0 && ticks > remaining) {
            ticks = remaining;
        }
        if (ticks < 1) {
            ticks = 1;
        }
        p.cache.fade.modelTicks = ticks;
    }

    public static float particleAlpha(ParticleBlood p) {
        float alpha = BloodAlpha.predictAlpha(p, p.getAge(), p.getMaxAge());
        return Util.clamp01(alpha *= BloodAlphaParticle.unsupportedFade(p));
    }

    private static float unsupportedFade(@Nullable ParticleBlood p) {
        if (p == null) {
            return 1.0f;
        }
        if (p.cache.fade.modelStartAge < 0) {
            return 1.0f;
        }
        int dur = p.cache.fade.modelTicks;
        if (dur <= 0) {
            return 0.0f;
        }
        int elapsed = p.getAge() - p.cache.fade.modelStartAge;
        if (elapsed <= 0) {
            return 1.0f;
        }
        float t = Util.clamp01((float)elapsed / (float)dur);
        return 1.0f - Util.smoothstep01(t);
    }

    private static float consumeFade(@Nullable ParticleBlood p) {
        if (p == null || p.amalgamConsumeStartAge < 0) {
            return 1.0f;
        }
        int dur = Math.max(1, p.amalgamConsumeDurationTicks);
        int elapsed = p.getAge() - p.amalgamConsumeStartAge;
        if (elapsed < 0) {
            elapsed = 0;
        }
        float t = Util.clamp01((float)elapsed / (float)dur);
        return 1.0f - Util.smoothstep01(t);
    }

    private static boolean consumeDone(@Nullable ParticleBlood p) {
        float t;
        if (p == null || p.amalgamConsumeStartAge < 0) {
            return false;
        }
        int dur = Math.max(1, p.amalgamConsumeDurationTicks);
        int elapsed = p.getAge() - p.amalgamConsumeStartAge;
        if (elapsed < 0) {
            elapsed = 0;
        }
        return (t = Util.clamp01((float)elapsed / (float)dur)) >= 0.999999f;
    }

    private static float waterFade(@Nullable ParticleBlood p) {
        if (p == null) {
            return 1.0f;
        }
        if (BloodAlphaMagic.clearWaterFade(p)) {
            return 1.0f;
        }
        if (p.cache.fade.waterStartAge < 0) {
            return 1.0f;
        }
        int dur = Math.max(1, p.cache.fade.waterTicks);
        dur = BloodAlphaSlimy.waterFadeTicks(p, dur);
        int elapsed = p.getAge() - p.cache.fade.waterStartAge;
        if (elapsed < 0) {
            elapsed = 0;
        }
        float t = Util.clamp01((float)elapsed / (float)dur);
        return 1.0f - Util.smoothstep01(t);
    }

    private static boolean waterFadeDone(@Nullable ParticleBlood p) {
        float t;
        if (p == null) {
            return false;
        }
        if (BloodAlphaMagic.clearWaterFade(p)) {
            return false;
        }
        if (p.cache.fade.waterStartAge < 0) {
            return false;
        }
        int dur = Math.max(1, p.cache.fade.waterTicks);
        dur = BloodAlphaSlimy.waterFadeTicks(p, dur);
        int elapsed = p.getAge() - p.cache.fade.waterStartAge;
        if (elapsed < 0) {
            elapsed = 0;
        }
        return (t = Util.clamp01((float)elapsed / (float)dur)) >= 0.999999f;
    }
}

