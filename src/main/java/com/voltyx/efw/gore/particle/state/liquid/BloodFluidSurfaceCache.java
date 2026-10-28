package com.voltyx.efw.gore.particle.state.liquid;

import com.voltyx.efw.gore.particle.common.geometry.BloodGeometryFluid;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceFluid;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.voltyx.efw.gore.particle.common.GoreMaterial;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
public class BloodFluidSurfaceCache {
    @Nullable
    public final Level world;
    @Nonnull
    public final GoreMaterial fluidMaterial;
    public final int scanDownMax;
    public final int scanUpMax;
    public final int rescanTicks;
    @Nonnull
    public final AboveOpenRule aboveOpenRule;
    @Nullable
    public BlockPos cachedTop;
    public boolean cachedAboveOpen;
    public float hNW = Float.NaN;
    public float hNE = Float.NaN;
    public float hSW = Float.NaN;
    public float hSE = Float.NaN;
    public int rescanCooldownTicks;

    public BloodFluidSurfaceCache(@Nullable Level world, @Nonnull GoreMaterial fluidMaterial, int scanDownMax, int scanUpMax, int rescanTicks, @Nonnull AboveOpenRule aboveOpenRule) {
        this.world = world;
        this.fluidMaterial = fluidMaterial;
        this.scanDownMax = Math.max(0, scanDownMax);
        this.scanUpMax = Math.max(1, scanUpMax);
        this.rescanTicks = Math.max(1, rescanTicks);
        this.aboveOpenRule = aboveOpenRule;
    }

    public void invalidate() {
        BloodSurfaceFluid.reset(this);
    }

    public boolean hasSurface() {
        return this.cachedTop != null && BloodSurfaceFluid.hasHeights(this);
    }

    @Nullable
    public BlockPos getCachedTop() {
        return this.cachedTop;
    }

    public boolean isCachedAboveOpen() {
        return this.cachedAboveOpen;
    }

    public void tickAndUpdate(double x, double y, double z) {
        BloodSurfaceFluid.tick(this, x, y, z);
    }

    public void forceScanAt(double x, double y, double z) {
        BloodSurfaceFluid.force(this, x, y, z);
    }

    @Nullable
    public SurfacePlane planeAt(double worldX, double worldZ) {
        return BloodSurfaceFluid.plane(this, worldX, worldZ);
    }

    public static class SurfaceData {
        public final BlockPos top;
        public final boolean aboveOpen;
        @Deprecated
        public final boolean aboveAir;
        public final float hNW;
        public final float hNE;
        public final float hSW;
        public final float hSE;

        public SurfaceData(BlockPos top, boolean aboveOpen, float hNW, float hNE, float hSW, float hSE) {
            this.top = top;
            this.aboveOpen = aboveOpen;
            this.aboveAir = aboveOpen;
            this.hNW = hNW;
            this.hNE = hNE;
            this.hSW = hSW;
            this.hSE = hSE;
        }
    }

    public static final class Basis {
        public final double t1x;
        public final double t1y;
        public final double t1z;
        public final double t2x;
        public final double t2y;
        public final double t2z;

        public Basis(double t1x, double t1y, double t1z, double t2x, double t2y, double t2z) {
            this.t1x = t1x;
            this.t1y = t1y;
            this.t1z = t1z;
            this.t2x = t2x;
            this.t2y = t2y;
            this.t2z = t2z;
        }
    }

    public static final class Plane {
        public final double ax;
        public final double ay;
        public final double az;
        public final double nx;
        public final double ny;
        public final double nz;

        public Plane(double ax, double ay, double az, double nx, double ny, double nz) {
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }

        public double yAt(double x, double z) {
            return BloodGeometryFluid.samplePlaneY(this, x, z);
        }

        public static Plane fromTriangle(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz) {
            return BloodGeometryFluid.buildPlaneFromTriangle(ax, ay, az, bx, by, bz, cx, cy, cz);
        }
    }

    public static final class SurfacePlane {
        public final int bx;
        public final int by;
        public final int bz;
        public final double yNW;
        public final double yNE;
        public final double ySW;
        public final double ySE;
        public final double nx;
        public final double ny;
        public final double nz;

        public SurfacePlane(int bx, int by, int bz, double yNW, double yNE, double ySW, double ySE, double nx, double ny, double nz) {
            this.bx = bx;
            this.by = by;
            this.bz = bz;
            this.yNW = yNW;
            this.yNE = yNE;
            this.ySW = ySW;
            this.ySE = ySE;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }

        public double yAt(double x, double z) {
            return BloodGeometryFluid.sampleSurfaceY(this, x, z);
        }
    }

    @FunctionalInterface
    public static interface AboveOpenRule {
        public boolean isOpen(@Nullable BlockState var1);
    }
}

