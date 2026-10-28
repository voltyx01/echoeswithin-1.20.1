package com.voltyx.efw.gore.particle.common.cache;

import com.voltyx.efw.gore.particle.common.cache.BloodCaches;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
final class BloodCachesCommon {
    BloodCachesCommon() {
    }

    static void setBillboard(@Nonnull BloodCaches.Billboard b, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ, double interpX, double interpY, double interpZ) {
        b.valid = true;
        b.rotX = rotationX;
        b.rotZ = rotationZ;
        b.rotYZ = rotationYZ;
        b.rotXY = rotationXY;
        b.rotXZ = rotationXZ;
        b.interpX = interpX;
        b.interpY = interpY;
        b.interpZ = interpZ;
    }

    static boolean hasBillboard(@Nullable BloodCaches.Billboard b) {
        return b != null && b.valid;
    }

    static void clearBillboard(@Nullable BloodCaches.Billboard b) {
        if (b == null) {
            return;
        }
        b.valid = false;
    }
}

