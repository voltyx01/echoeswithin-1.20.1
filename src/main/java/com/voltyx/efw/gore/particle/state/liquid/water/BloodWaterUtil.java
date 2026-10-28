package com.voltyx.efw.gore.particle.state.liquid.water;

import com.voltyx.efw.gore.particle.state.liquid.BloodLiquidUtil;
public final class BloodWaterUtil {
    public static float solveBasePassAlphaForTarget(float targetAlpha) {
        return BloodLiquidUtil.solveBasePassAlphaForTarget(targetAlpha, true, 0.85f);
    }

    public static final class Vertex {
        public final double x;
        public final double y;
        public final double z;
        public final float u;
        public final float v;

        public Vertex(double x, double y, double z, float u, float v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
        }
    }

    public static final class RangeF {
        public final float min;
        public final float max;

        private RangeF(float min, float max) {
            this.min = min;
            this.max = max;
        }

        public static RangeF of(float a, float b) {
            return a <= b ? new RangeF(a, b) : new RangeF(b, a);
        }

        public RangeF clampMin(float minClamp) {
            float nMin = Math.max(minClamp, this.min);
            float nMax = Math.max(nMin, this.max);
            if (nMin == this.min && nMax == this.max) {
                return this;
            }
            return new RangeF(nMin, nMax);
        }
    }
}

