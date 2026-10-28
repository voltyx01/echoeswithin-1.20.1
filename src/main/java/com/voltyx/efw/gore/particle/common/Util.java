package com.voltyx.efw.gore.particle.common;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
public final class Util {
    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);

    public static boolean isVerticalFace(@Nullable Direction face) {
        return face != null && face.getAxis().isHorizontal();
    }

    public static float randBetween(RandomSource rand, float min, float max) {
        if (rand == null) {
            return min;
        }
        if (max <= min) {
            return min;
        }
        return min + rand.nextFloat() * (max - min);
    }

    public static float clamp01(float v) {
        if (v < 0.0f) {
            return 0.0f;
        }
        return Math.min(v, 1.0f);
    }

    public static double clamp01(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }

    public static double clamp(double v, double lo, double hi) {
        if (v < lo) {
            return lo;
        }
        return Math.min(v, hi);
    }

    public static int clampInt(int v, int lo, int hi) {
        if (v < lo) {
            return lo;
        }
        return Math.min(v, hi);
    }

    public static float smoothstep01(float t01) {
        float t = Util.clamp01(t01);
        return t * t * (3.0f - 2.0f * t);
    }

    public static float easeOutCubic01(float t01) {
        float t = Util.clamp01(t01);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    public static float progress01(int age, int startAge, int durationTicks, int maxAge) {
        if (durationTicks == 0) {
            return 1.0f;
        }
        int elapsed = age - startAge;
        if (elapsed < 0) {
            elapsed = 0;
        }
        int denom = durationTicks < 0 ? Math.max(1, maxAge - startAge) : Math.max(1, durationTicks);
        return Util.clamp01((float)elapsed / (float)denom);
    }

    public static double snap16(double a) {
        double s = (double)Math.round(a * 16.0) / 16.0;
        return Math.abs(a - s) < 1.0E-9 ? s : a;
    }

    public static Vec3 snap16(Vec3 v) {
        return new Vec3(Util.snap16(v.x), Util.snap16(v.y), Util.snap16(v.z));
    }

    public static double min4(double[] a) {
        return Math.min(Math.min(a[0], a[1]), Math.min(a[2], a[3]));
    }

    public static double max4(double[] a) {
        return Math.max(Math.max(a[0], a[1]), Math.max(a[2], a[3]));
    }

    public static double localP(Direction face, Vertex v) {
        FaceSpace fs = FaceSpace.of(face);
        return fs.p(v.x, v.y, v.z);
    }

    public static double localQ(Direction face, Vertex v) {
        FaceSpace fs = FaceSpace.of(face);
        return fs.q(v.x, v.y, v.z);
    }

    public static double localP(Direction face, Vec3 v) {
        FaceSpace fs = FaceSpace.of(face);
        return fs.p(v.x, v.y, v.z);
    }

    public static double localQ(Direction face, Vec3 v) {
        FaceSpace fs = FaceSpace.of(face);
        return fs.q(v.x, v.y, v.z);
    }

    public static double planeCoord(Direction face, Vec3 pointOnPlane) {
        if (face == null || pointOnPlane == null) {
            return 0.0;
        }
        FaceSpace fs = FaceSpace.of(face);
        return fs.plane(pointOnPlane.x, pointOnPlane.y, pointOnPlane.z);
    }

    public static FaceRect offsetRect(Direction face, FaceRect r, Vec3 off) {
        if (r == null || off == null || face == null) {
            return r;
        }
        FaceSpace fs = FaceSpace.of(face);
        double planeShift = FaceSpace.axisValue(fs.planeAxis, off.x, off.y, off.z);
        double pShift = FaceSpace.axisValue(fs.pAxis, off.x, off.y, off.z);
        double qShift = FaceSpace.axisValue(fs.qAxis, off.x, off.y, off.z);
        return new FaceRect(r.plane + planeShift, r.pMin + pShift, r.pMax + pShift, r.qMin + qShift, r.qMax + qShift);
    }

    public static Vec3 pointFromPlanePQ(Direction face, double plane, double p, double q) {
        return FaceSpace.of(face).point(plane, p, q);
    }

    public static double distanceSqToRect(Direction face, FaceRect r, Vec3 hit) {
        double p = Util.localP(face, hit);
        double q = Util.localQ(face, hit);
        double cp = Util.clamp(p, r.pMin, r.pMax);
        double cq = Util.clamp(q, r.qMin, r.qMax);
        double dp = p - cp;
        double dq = q - cq;
        return dp * dp + dq * dq;
    }

    @Nullable
    public static FaceRect pickBestRect(List<FaceRect> rects, Direction face, Vec3 hit) {
        if (rects == null || rects.isEmpty()) {
            return null;
        }
        if (hit == null) {
            return rects.get(0);
        }
        double p = Util.localP(face, hit);
        double q = Util.localQ(face, hit);
        FaceRect best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (FaceRect r : rects) {
            double cq;
            double dq;
            double cp = Util.clamp(p, r.pMin, r.pMax);
            double dp = p - cp;
            double d = dp * dp + (dq = q - (cq = Util.clamp(q, r.qMin, r.qMax))) * dq;
            if (!(d < bestD)) continue;
            bestD = d;
            best = r;
        }
        return best;
    }

    public static Vec3 rectCenter(BlockPos bp, Direction face, FaceRect r) {
        double pc = 0.5 * (r.pMin + r.pMax);
        double qc = 0.5 * (r.qMin + r.qMax);
        return Util.pointFromPlanePQ(face, r.plane, pc, qc);
    }

    public static Vec3 clampToRectOnFacePlane(Direction face, FaceRect rect, Vec3 raw) {
        double pad = 5.0E-4;
        double p = Util.localP(face, raw);
        double q = Util.localQ(face, raw);
        p = Util.clamp(p, rect.pMin + 5.0E-4, rect.pMax - 5.0E-4);
        q = Util.clamp(q, rect.qMin + 5.0E-4, rect.qMax - 5.0E-4);
        return Util.pointFromPlanePQ(face, rect.plane, p, q);
    }

    public static int blockCoordForPlane(Direction face, double plane) {
        double eps = 1.0E-6;
        switch (face) {
            case SOUTH: 
            case EAST: 
            case UP: {
                return Mth.floor((double)(plane - 1.0E-6));
            }
        }
        return Mth.floor((double)(plane + 1.0E-6));
    }

    public static List<Vertex> clipToRect(List<Vertex> poly, Direction face, FaceRect r) {
        List<Vertex> out = poly != null ? poly : new ArrayList<Vertex>(0);
        out = Util.clipBound(out, face, ClipAxis.P, r.pMin, true);
        out = Util.clipBound(out, face, ClipAxis.P, r.pMax, false);
        out = Util.clipBound(out, face, ClipAxis.Q, r.qMin, true);
        out = Util.clipBound(out, face, ClipAxis.Q, r.qMax, false);
        return out;
    }

    private static double coord(Direction face, Vertex v, ClipAxis axis) {
        return axis == ClipAxis.P ? Util.localP(face, v) : Util.localQ(face, v);
    }

    private static List<Vertex> clipBound(List<Vertex> in, Direction face, ClipAxis axis, double bound, boolean keepGreaterOrEqual) {
        ArrayList<Vertex> out = new ArrayList<Vertex>();
        if (in == null || in.isEmpty()) {
            return out;
        }
        Vertex s = in.get(in.size() - 1);
        double sC = Util.coord(face, s, axis);
        boolean sIn = keepGreaterOrEqual ? sC >= bound : sC <= bound;
        for (Vertex e : in) {
            double eC = Util.coord(face, e, axis);
            boolean eIn = keepGreaterOrEqual ? eC >= bound : eC <= bound;
            if (eIn) {
                if (!sIn) {
                    out.add(Util.intersectAtBound(s, e, sC, eC, bound));
                }
                out.add(e);
            } else if (sIn) {
                out.add(Util.intersectAtBound(s, e, sC, eC, bound));
            }
            s = e;
            sC = eC;
            sIn = eIn;
        }
        return out;
    }

    private static Vertex intersectAtBound(Vertex a, Vertex b, double aC, double bC, double bound) {
        double denom = bC - aC;
        double t = denom == 0.0 ? 0.0 : (bound - aC) / denom;
        return Util.lerp(a, b, t);
    }

    private static Vertex lerp(Vertex a, Vertex b, double t) {
        return new Vertex(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t, (float)((double)a.u + (double)(b.u - a.u) * t), (float)((double)a.v + (double)(b.v - a.v) * t));
    }

    private static enum ClipAxis {
        P,
        Q;

    }

    public static enum FaceSpace {
        UP(Direction.UP, Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z),
        DOWN(Direction.DOWN, Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z),
        NORTH(Direction.NORTH, Direction.Axis.Z, Direction.Axis.X, Direction.Axis.Y),
        SOUTH(Direction.SOUTH, Direction.Axis.Z, Direction.Axis.X, Direction.Axis.Y),
        EAST(Direction.EAST, Direction.Axis.X, Direction.Axis.Z, Direction.Axis.Y),
        WEST(Direction.WEST, Direction.Axis.X, Direction.Axis.Z, Direction.Axis.Y);

        public final Direction face;
        public final Direction.Axis planeAxis;
        public final Direction.Axis pAxis;
        public final Direction.Axis qAxis;

        private FaceSpace(Direction face, Direction.Axis planeAxis, Direction.Axis pAxis, Direction.Axis qAxis) {
            this.face = face;
            this.planeAxis = planeAxis;
            this.pAxis = pAxis;
            this.qAxis = qAxis;
        }

        public static FaceSpace of(@Nullable Direction face) {
            if (face == null) {
                return UP;
            }
            switch (face) {
                case DOWN: {
                    return DOWN;
                }
                case NORTH: {
                    return NORTH;
                }
                case SOUTH: {
                    return SOUTH;
                }
                case EAST: {
                    return EAST;
                }
                case WEST: {
                    return WEST;
                }
            }
            return UP;
        }

        public double plane(double x, double y, double z) {
            return FaceSpace.axisValue(this.planeAxis, x, y, z);
        }

        public double p(double x, double y, double z) {
            return FaceSpace.axisValue(this.pAxis, x, y, z);
        }

        public double q(double x, double y, double z) {
            return FaceSpace.axisValue(this.qAxis, x, y, z);
        }

        public Vec3 point(double plane, double p, double q) {
            double x = this.valueForAxis(Direction.Axis.X, plane, p, q);
            double y = this.valueForAxis(Direction.Axis.Y, plane, p, q);
            double z = this.valueForAxis(Direction.Axis.Z, plane, p, q);
            return new Vec3(x, y, z);
        }

        private static double axisValue(Direction.Axis axis, double x, double y, double z) {
            switch (axis) {
                case X: {
                    return x;
                }
                case Y: {
                    return y;
                }
            }
            return z;
        }

        private double valueForAxis(Direction.Axis axis, double plane, double p, double q) {
            if (axis == this.planeAxis) {
                return plane;
            }
            if (axis == this.pAxis) {
                return p;
            }
            if (axis == this.qAxis) {
                return q;
            }
            return 0.0;
        }
    }

    public static final class SurfaceLayer {
        private static final double SURFACE_EPSILON = 0.001;
        private static final int LAYER_COUNT = 64;
        private static final double LAYER_STEP = 2.0E-5;
        private static int NEXT_LAYER = 0;

        public static double nextSurfaceOffset() {
            int layer = (NEXT_LAYER++ & Integer.MAX_VALUE) % 64;
            return 0.001 + (double)layer * 2.0E-5;
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

    public static final class FaceRect {
        public final double plane;
        public final double pMin;
        public final double pMax;
        public final double qMin;
        public final double qMax;

        public FaceRect(double plane, double pMin, double pMax, double qMin, double qMax) {
            this.plane = plane;
            this.pMin = pMin;
            this.pMax = pMax;
            this.qMin = qMin;
            this.qMax = qMax;
        }
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
}

