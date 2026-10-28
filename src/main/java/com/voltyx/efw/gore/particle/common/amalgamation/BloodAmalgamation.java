package com.voltyx.efw.gore.particle.common.amalgamation;

import com.voltyx.efw.gore.json.BloodAmalgamationPolicy;
import com.voltyx.efw.gore.json.BloodStyle;
import com.voltyx.efw.gore.particle.ParticleBlood;
import javax.annotation.Nullable;
import net.minecraft.world.level.Level;
final class BloodAmalgamation {
    static final int MAX_BUCKETS = 4096;

    BloodAmalgamation() {
    }

    static boolean allowDecal(@Nullable BloodAmalgamationPolicy policy, @Nullable BloodStyle style) {
        BloodAmalgamationPolicy p = policy != null ? policy : BloodAmalgamationPolicy.BOTH;
        return p.allowGround() && ParticleBlood.isLightLikeFluid(style);
    }

    static long safeTick(Level w) {
        try {
            return w.getGameTime();
        }
        catch (Throwable ignored) {
            return w.getDayTime();
        }
    }

    static int safeDim(Level w) {
        try {
            return w.dimension().location().hashCode();
        }
        catch (Throwable t) {
            return 0;
        }
    }
}

