package com.voltyx.efw.gore.particle.common.cache;

import com.voltyx.efw.gore.particle.common.cache.BloodCachesCommon;
import com.voltyx.efw.gore.particle.state.liquid.lava.BloodLava;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
public class BloodCachesLava {
    public static void captureBillboard(@Nonnull BloodLava p, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        BloodCachesCommon.setBillboard(p.cache.billboard, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, BloodLava.getCameraX(), BloodLava.getCameraY(), BloodLava.getCameraZ());
    }

    public static boolean hasBillboard(@Nullable BloodLava p) {
        return p != null && BloodCachesCommon.hasBillboard(p.cache.billboard);
    }

    public static void clearBillboard(@Nullable BloodLava p) {
        if (p == null) {
            return;
        }
        BloodCachesCommon.clearBillboard(p.cache.billboard);
    }
}

