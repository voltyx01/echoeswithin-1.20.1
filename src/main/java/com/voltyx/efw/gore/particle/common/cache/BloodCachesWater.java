package com.voltyx.efw.gore.particle.common.cache;

import com.voltyx.efw.gore.particle.common.cache.BloodCaches;
import com.voltyx.efw.gore.particle.common.cache.BloodCachesCommon;
import com.voltyx.efw.gore.particle.state.liquid.water.BloodWater;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
public class BloodCachesWater {
    private static final BloodCaches.WaterFrame FRAME = new BloodCaches.WaterFrame();

    public static void captureBillboard(@Nonnull BloodWater p, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        BloodCachesCommon.setBillboard(p.cache.billboard, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, BloodWater.getCameraX(), BloodWater.getCameraY(), BloodWater.getCameraZ());
    }

    public static boolean hasBillboard(@Nullable BloodWater p) {
        return p != null && BloodCachesCommon.hasBillboard(p.cache.billboard);
    }

    public static void clearBillboard(@Nullable BloodWater p) {
        if (p == null) {
            return;
        }
        BloodCachesCommon.clearBillboard(p.cache.billboard);
    }

    @Nonnull
    public static BloodCaches.WaterFrame frame() {
        return FRAME;
    }

    public static void clearQuery(@Nonnull BloodCaches.Query q) {
        q.hit.clear();
        q.miss.clear();
    }
}

