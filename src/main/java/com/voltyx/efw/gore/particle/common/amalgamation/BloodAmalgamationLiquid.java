package com.voltyx.efw.gore.particle.common.amalgamation;

import com.voltyx.efw.gore.json.BloodAmalgamationPolicy;
import com.voltyx.efw.gore.json.BloodStyle;
import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.amalgamation.BloodAmalgamation;
import com.voltyx.efw.gore.particle.common.amalgamation.BloodAmalgamationLava;
import com.voltyx.efw.gore.particle.common.amalgamation.BloodAmalgamationWater;
import javax.annotation.Nullable;
import net.minecraft.world.level.Level;
public class BloodAmalgamationLiquid {
    public static final boolean ENABLED = true;
    static final float CELL = 0.25f;
    static final float DIST = 0.45f;
    static final float TARGET = 1.1f;
    public static final float MAX_MUL = 2.4f;
    static final float MAX_ADD = 0.35f;
    public static final int MERGE_TICKS = 10;
    public static final int TARGET_COOLDOWN = 10;
    public static final float EASE = 0.3f;
    public static final float ALPHA_POW = 0.2f;
    public static final float DONOR_SHRINK = 0.15f;
    public static final boolean LAVA_SKIP_BOIL = true;
    static final float MIN_CELL = 0.05f;
    static final float BOIL_EPS = 0.001f;
    static final float GIVE_EPS = 1.0E-6f;
    static final float REMAIN_EPS = 0.001f;
    private static Level world = null;
    private static long tick = Long.MIN_VALUE;

    public static boolean enabled() {
        return true;
    }

    public static boolean allow(@Nullable BloodAmalgamationPolicy policy, @Nullable BloodStyle style) {
        BloodAmalgamationPolicy p = policy != null ? policy : BloodAmalgamationPolicy.BOTH;
        return p.allowLiquid() && ParticleBlood.isLightLikeFluid(style);
    }

    static void beginTick(@Nullable Level w) {
        if (w == null) {
            return;
        }
        long now = BloodAmalgamation.safeTick(w);
        if (w != world || now != tick) {
            world = w;
            tick = now;
            BloodAmalgamationLava.clearCache();
            BloodAmalgamationWater.clearCache();
        }
    }

    static boolean mature(int age, int startAge, float blendTicks) {
        if (startAge < 0) {
            return false;
        }
        int need = (int)Math.ceil(Math.max(0.0f, blendTicks));
        return age - startAge >= need;
    }

    static double area(float surfaceScale, float amalgMul) {
        double s = surfaceScale;
        double m = Math.max(1.0f, amalgMul);
        return s * s * m;
    }

    static double range(float scaleA, float mulA, float scaleB, float mulB, float distMul) {
        double a = (double)scaleA * Math.sqrt(Math.max(1.0f, mulA));
        double b = (double)scaleB * Math.sqrt(Math.max(1.0f, mulB));
        return (double)distMul * 0.1 * (a + b);
    }

    static double dist2(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return dx * dx + dz * dz;
    }

    static long key(int layer, int cellX, int cellZ) {
        long h = 1469598103934665603L;
        h ^= (long)layer;
        h *= 1099511628211L;
        h ^= (long)cellX;
        h *= 1099511628211L;
        h ^= (long)cellZ;
        return h *= 1099511628211L;
    }

    static float give(float recvScale, float recvTargetMul, float donorScale, float donorMul) {
        double recvBaseSq = Math.max(1.0E-8, (double)recvScale * (double)recvScale);
        double donorArea = BloodAmalgamationLiquid.area(donorScale, donorMul);
        float remain = 2.4f - recvTargetMul;
        if (remain <= 0.001f) {
            return 0.0f;
        }
        float give = (float)(donorArea / recvBaseSq);
        if (give > 0.35f) {
            give = 0.35f;
        }
        if (give > remain) {
            give = remain;
        }
        if (give < 0.0f) {
            give = 0.0f;
        }
        return give;
    }
}

