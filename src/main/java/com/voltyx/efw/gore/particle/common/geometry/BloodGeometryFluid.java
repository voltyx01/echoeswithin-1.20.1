package com.voltyx.efw.gore.particle.common.geometry;

import com.voltyx.efw.gore.particle.state.liquid.BloodFluidSurfaceCache;
import com.voltyx.efw.gore.particle.state.liquid.BloodLiquidUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
public class BloodGeometryFluid {
    public static double sampleSurfaceY(@Nonnull BloodFluidSurfaceCache.SurfacePlane plane, double x, double z) {
        double fz;
        double fx = BloodLiquidUtil.clamp(x - (double)plane.bx, 0.0, 1.0);
        if (fx <= (fz = BloodLiquidUtil.clamp(z - (double)plane.bz, 0.0, 1.0))) {
            return plane.yNW + (plane.ySE - plane.ySW) * fx + (plane.ySW - plane.yNW) * fz;
        }
        return plane.yNW + (plane.yNE - plane.yNW) * fx + (plane.ySE - plane.yNE) * fz;
    }

    public static double samplePlaneY(@Nonnull BloodFluidSurfaceCache.Plane plane, double x, double z) {
        if (Math.abs(plane.ny) < 1.0E-6) {
            return plane.ay;
        }
        return plane.ay - (plane.nx * (x - plane.ax) + plane.nz * (z - plane.az)) / plane.ny;
    }

    @Nonnull
    public static BloodFluidSurfaceCache.Plane buildPlaneFromTriangle(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz) {
        double aby = by - ay;
        double acz = cz - az;
        double abz = bz - az;
        double acy = cy - ay;
        double nx = aby * acz - abz * acy;
        double acx = cx - ax;
        double abx = bx - ax;
        double ny = abz * acx - abx * acz;
        double nz = abx * acy - aby * acx;
        double lenSq = nx * nx + ny * ny + nz * nz;
        if (lenSq < 1.0E-16) {
            return new BloodFluidSurfaceCache.Plane(ax, ay, az, 0.0, 1.0, 0.0);
        }
        double inv = 1.0 / Math.sqrt(lenSq);
        nx *= inv;
        ny *= inv;
        nz *= inv;
        if (ny < 0.0) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        return new BloodFluidSurfaceCache.Plane(ax, ay, az, nx, ny, nz);
    }

    @Nonnull
    public static BloodFluidSurfaceCache.Plane buildTriPlaneA(int bx, int by, int bz, float hNW, float hSW, float hSE) {
        return BloodGeometryFluid.buildPlaneFromTriangle(bx, (double)by + (double)hNW, bz, bx, (double)by + (double)hSW, (double)bz + 1.0, (double)bx + 1.0, (double)by + (double)hSE, (double)bz + 1.0);
    }

    @Nonnull
    public static BloodFluidSurfaceCache.Plane buildTriPlaneB(int bx, int by, int bz, float hNW, float hNE, float hSE) {
        return BloodGeometryFluid.buildPlaneFromTriangle(bx, (double)by + (double)hNW, bz, (double)bx + 1.0, (double)by + (double)hSE, (double)bz + 1.0, (double)bx + 1.0, (double)by + (double)hNE, bz);
    }

    @Nullable
    public static BloodFluidSurfaceCache.Basis buildBasis(double nx, double ny, double nz) {
        double t2z;
        double t2y;
        double invLen;
        double t2x;
        double len2Sq;
        double dot;
        double lenSq;
        double t1x = 1.0;
        double t1y = 0.0;
        double t1z = 0.0;
        if ((lenSq = (t1x -= (dot = t1x * nx + t1y * ny + t1z * nz) * nx) * t1x + (t1y -= dot * ny) * t1y + (t1z -= dot * nz) * t1z) < 1.0E-10) {
            t1x = 0.0;
            t1y = 0.0;
            t1z = 1.0;
            if ((lenSq = (t1x -= (dot = t1x * nx + t1y * ny + t1z * nz) * nx) * t1x + (t1y -= dot * ny) * t1y + (t1z -= dot * nz) * t1z) < 1.0E-10) {
                return null;
            }
        }
        if ((len2Sq = (t2x = ny * (t1z *= (invLen = 1.0 / Math.sqrt(lenSq))) - nz * (t1y *= invLen)) * t2x + (t2y = nz * (t1x *= invLen) - nx * t1z) * t2y + (t2z = nx * t1y - ny * t1x) * t2z) < 1.0E-12) {
            return null;
        }
        double invLen2 = 1.0 / Math.sqrt(len2Sq);
        return new BloodFluidSurfaceCache.Basis(t1x, t1y, t1z, t2x *= invLen2, t2y *= invLen2, t2z *= invLen2);
    }
}

