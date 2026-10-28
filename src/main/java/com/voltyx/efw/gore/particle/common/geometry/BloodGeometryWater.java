package com.voltyx.efw.gore.particle.common.geometry;

import com.voltyx.efw.gore.particle.state.liquid.BloodFluidSurfaceCache;
import com.voltyx.efw.gore.particle.state.liquid.water.BloodWaterUtil;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
public class BloodGeometryWater {
    @Nonnull
    public static List<BloodWaterUtil.Vertex> buildFootprintQuad(double cx, double cz, @Nonnull BloodFluidSurfaceCache.Basis basis, @Nonnull TextureAtlasSprite sprite, float rotRad, float quadScale, boolean flipU) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float uLeft = flipU ? u0 : u1;
        float uRight = flipU ? u1 : u0;
        double halfSize = 0.1 * (double)quadScale;
        double cos = Math.cos(rotRad);
        double sin = Math.sin(rotRad);
        ArrayList<BloodWaterUtil.Vertex> out = new ArrayList<BloodWaterUtil.Vertex>(4);
        BloodGeometryWater.addFootprintVertex(out, cx, cz, basis, -halfSize, -halfSize, cos, sin, uLeft, v1);
        BloodGeometryWater.addFootprintVertex(out, cx, cz, basis, -halfSize, halfSize, cos, sin, uLeft, v0);
        BloodGeometryWater.addFootprintVertex(out, cx, cz, basis, halfSize, halfSize, cos, sin, uRight, v0);
        BloodGeometryWater.addFootprintVertex(out, cx, cz, basis, halfSize, -halfSize, cos, sin, uRight, v1);
        return out;
    }

    @Nonnull
    public static List<BloodWaterUtil.Vertex> clipToRectXZ(@Nullable List<BloodWaterUtil.Vertex> poly, double minX, double maxX, double minZ, double maxZ) {
        List<BloodWaterUtil.Vertex> out = poly != null ? poly : new ArrayList<BloodWaterUtil.Vertex>(0);
        out = BloodGeometryWater.clipBound(out, Axis2D.X, minX, true);
        out = BloodGeometryWater.clipBound(out, Axis2D.X, maxX, false);
        out = BloodGeometryWater.clipBound(out, Axis2D.Z, minZ, true);
        out = BloodGeometryWater.clipBound(out, Axis2D.Z, maxZ, false);
        return out;
    }

    @Nonnull
    public static List<BloodWaterUtil.Vertex> clipDiag(@Nullable List<BloodWaterUtil.Vertex> in, int bx, int bz, boolean keepLessOrEqual) {
        ArrayList<BloodWaterUtil.Vertex> out = new ArrayList<BloodWaterUtil.Vertex>();
        if (in == null || in.isEmpty()) {
            return out;
        }
        BloodWaterUtil.Vertex s = in.get(in.size() - 1);
        double sV = BloodGeometryWater.diagVal(s, bx, bz);
        boolean sIn = keepLessOrEqual ? sV <= 0.0 : sV >= 0.0;
        for (BloodWaterUtil.Vertex e : in) {
            double t;
            double denom;
            double eV = BloodGeometryWater.diagVal(e, bx, bz);
            boolean eIn = keepLessOrEqual ? eV <= 0.0 : eV >= 0.0;
            if (eIn) {
                if (!sIn) {
                    denom = eV - sV;
                    t = Math.abs(denom) < 1.0E-12 ? 0.0 : -sV / denom;
                    out.add(BloodGeometryWater.lerpVertex(s, e, t));
                }
                out.add(e);
            } else if (sIn) {
                denom = eV - sV;
                t = Math.abs(denom) < 1.0E-12 ? 0.0 : -sV / denom;
                out.add(BloodGeometryWater.lerpVertex(s, e, t));
            }
            s = e;
            sV = eV;
            sIn = eIn;
        }
        return out;
    }

    private static void addFootprintVertex(@Nonnull List<BloodWaterUtil.Vertex> out, double cx, double cz, @Nonnull BloodFluidSurfaceCache.Basis basis, double ox, double oz, double cos, double sin, float u, float v) {
        double rx = ox * cos - oz * sin;
        double rz = ox * sin + oz * cos;
        double dx = basis.t1x * rx + basis.t2x * rz;
        double dz = basis.t1z * rx + basis.t2z * rz;
        out.add(new BloodWaterUtil.Vertex(cx + dx, 0.0, cz + dz, u, v));
    }

    private static double coord(@Nonnull BloodWaterUtil.Vertex v, @Nonnull Axis2D axis) {
        return axis == Axis2D.X ? v.x : v.z;
    }

    @Nonnull
    private static List<BloodWaterUtil.Vertex> clipBound(@Nullable List<BloodWaterUtil.Vertex> in, @Nonnull Axis2D axis, double bound, boolean keepGreaterOrEqual) {
        ArrayList<BloodWaterUtil.Vertex> out = new ArrayList<BloodWaterUtil.Vertex>();
        if (in == null || in.isEmpty()) {
            return out;
        }
        BloodWaterUtil.Vertex s = in.get(in.size() - 1);
        double sC = BloodGeometryWater.coord(s, axis);
        boolean sIn = keepGreaterOrEqual ? sC >= bound : sC <= bound;
        for (BloodWaterUtil.Vertex e : in) {
            double eC = BloodGeometryWater.coord(e, axis);
            boolean eIn = keepGreaterOrEqual ? eC >= bound : eC <= bound;
            if (eIn) {
                if (!sIn) {
                    out.add(BloodGeometryWater.intersectAtBound(s, e, sC, eC, bound, axis));
                }
                out.add(e);
            } else if (sIn) {
                out.add(BloodGeometryWater.intersectAtBound(s, e, sC, eC, bound, axis));
            }
            s = e;
            sC = eC;
            sIn = eIn;
        }
        return out;
    }

    @Nonnull
    private static BloodWaterUtil.Vertex intersectAtBound(@Nonnull BloodWaterUtil.Vertex a, @Nonnull BloodWaterUtil.Vertex b, double aC, double bC, double bound, @Nonnull Axis2D axis) {
        double denom = bC - aC;
        double t = Math.abs(denom) < 1.0E-12 ? 0.0 : (bound - aC) / denom;
        return BloodGeometryWater.lerpVertex(a, b, t, axis, bound);
    }

    @Nonnull
    private static BloodWaterUtil.Vertex lerpVertex(@Nonnull BloodWaterUtil.Vertex a, @Nonnull BloodWaterUtil.Vertex b, double t, @Nonnull Axis2D axis, double forcedCoord) {
        double x = a.x + (b.x - a.x) * t;
        double y = a.y + (b.y - a.y) * t;
        double z = a.z + (b.z - a.z) * t;
        if (axis == Axis2D.X) {
            x = forcedCoord;
        } else {
            z = forcedCoord;
        }
        float u = (float)((double)a.u + (double)(b.u - a.u) * t);
        float v = (float)((double)a.v + (double)(b.v - a.v) * t);
        return new BloodWaterUtil.Vertex(x, y, z, u, v);
    }

    private static double diagVal(@Nonnull BloodWaterUtil.Vertex v, int bx, int bz) {
        return v.x - (double)bx - (v.z - (double)bz);
    }

    @Nonnull
    private static BloodWaterUtil.Vertex lerpVertex(@Nonnull BloodWaterUtil.Vertex a, @Nonnull BloodWaterUtil.Vertex b, double t) {
        return new BloodWaterUtil.Vertex(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t, (float)((double)a.u + (double)(b.u - a.u) * t), (float)((double)a.v + (double)(b.v - a.v) * t));
    }

    private static enum Axis2D {
        X,
        Z;

    }
}

