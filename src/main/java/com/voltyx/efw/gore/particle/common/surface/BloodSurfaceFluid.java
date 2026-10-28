package com.voltyx.efw.gore.particle.common.surface;

import com.voltyx.efw.gore.particle.common.GoreUtil;

import com.voltyx.efw.gore.particle.state.liquid.BloodFluidSurfaceCache;
import com.voltyx.efw.gore.particle.state.liquid.BloodLiquidUtil;
import com.voltyx.efw.gore.particle.state.liquid.water.BloodWaterCache;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.LiquidBlock;
import com.voltyx.efw.gore.particle.common.GoreMaterial;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
public class BloodSurfaceFluid {
    public static final BloodFluidSurfaceCache.AboveOpenRule LAVA_OPEN_RULE = aboveState -> {
        if (aboveState == null) {
            return false;
        }
        GoreMaterial m = GoreMaterial.of(aboveState);
        return m != GoreMaterial.LAVA && !m.blocksMovement();
    };
    public static final BloodFluidSurfaceCache.AboveOpenRule WATER_OPEN_RULE = aboveState -> {
        if (aboveState == null) {
            return false;
        }
        return GoreMaterial.of(aboveState) != GoreMaterial.WATER;
    };

    public static void reset(@Nullable BloodFluidSurfaceCache cache) {
        if (cache == null) {
            return;
        }
        cache.cachedTop = null;
        cache.cachedAboveOpen = false;
        cache.hSE = Float.NaN;
        cache.hSW = Float.NaN;
        cache.hNE = Float.NaN;
        cache.hNW = Float.NaN;
        cache.rescanCooldownTicks = 0;
    }

    public static boolean hasHeights(@Nullable BloodFluidSurfaceCache cache) {
        if (cache == null) {
            return false;
        }
        return !Float.isNaN(cache.hNW) && !Float.isNaN(cache.hNE) && !Float.isNaN(cache.hSW) && !Float.isNaN(cache.hSE);
    }

    public static void tick(@Nullable BloodFluidSurfaceCache cache, double x, double y, double z) {
        if (cache == null) {
            return;
        }
        if (cache.world == null) {
            BloodSurfaceFluid.reset(cache);
            return;
        }
        if (cache.rescanCooldownTicks > 0) {
            --cache.rescanCooldownTicks;
        }
        if (cache.cachedTop != null) {
            int bx = Mth.floor((double)x);
            int bz = Mth.floor((double)z);
            if (bx != ((net.minecraft.core.Vec3i) cache.cachedTop).getX() || bz != ((net.minecraft.core.Vec3i) cache.cachedTop).getZ()) {
                cache.rescanCooldownTicks = 0;
            }
        }
        if (cache.rescanCooldownTicks <= 0 || cache.cachedTop == null || !BloodSurfaceFluid.hasHeights(cache)) {
            BloodSurfaceFluid.scan(cache, x, y, z);
            cache.rescanCooldownTicks = cache.rescanTicks;
        }
    }

    public static void force(@Nullable BloodFluidSurfaceCache cache, double x, double y, double z) {
        if (cache == null) {
            return;
        }
        if (cache.world == null) {
            BloodSurfaceFluid.reset(cache);
            return;
        }
        BloodSurfaceFluid.scan(cache, x, y, z);
        cache.rescanCooldownTicks = cache.rescanTicks;
    }

    @Nullable
    public static BloodFluidSurfaceCache.SurfacePlane plane(@Nullable BloodFluidSurfaceCache cache, double worldX, double worldZ) {
        if (cache == null || cache.cachedTop == null || !BloodSurfaceFluid.hasHeights(cache)) {
            return null;
        }
        int bx = ((net.minecraft.core.Vec3i) cache.cachedTop).getX();
        int by = ((net.minecraft.core.Vec3i) cache.cachedTop).getY();
        int bz = ((net.minecraft.core.Vec3i) cache.cachedTop).getZ();
        double fx = BloodLiquidUtil.clamp(worldX - (double)bx, 0.0, 1.0);
        double fz = BloodLiquidUtil.clamp(worldZ - (double)bz, 0.0, 1.0);
        double yNW = (double)by + (double)cache.hNW;
        double yNE = (double)by + (double)cache.hNE;
        double ySW = (double)by + (double)cache.hSW;
        double ySE = (double)by + (double)cache.hSE;
        BloodFluidSurfaceCache.Plane tri = fx <= fz ? BloodFluidSurfaceCache.Plane.fromTriangle(bx, yNW, bz, bx, ySW, (double)bz + 1.0, (double)bx + 1.0, ySE, (double)bz + 1.0) : BloodFluidSurfaceCache.Plane.fromTriangle(bx, yNW, bz, (double)bx + 1.0, ySE, (double)bz + 1.0, (double)bx + 1.0, yNE, bz);
        return new BloodFluidSurfaceCache.SurfacePlane(bx, by, bz, yNW, yNE, ySW, ySE, tri.nx, tri.ny, tri.nz);
    }

    @Nullable
    public static BloodWaterCache.SurfaceSample sample(@Nullable BloodWaterCache cache, double worldX, double worldZ) {
        BloodFluidSurfaceCache.SurfacePlane plane = BloodSurfaceFluid.plane(cache, worldX, worldZ);
        if (plane == null) {
            return null;
        }
        double y = plane.yAt(worldX, worldZ);
        return new BloodWaterCache.SurfaceSample(y, plane.nx, plane.ny, plane.nz);
    }

    @Nullable
    public static BloodFluidSurfaceCache.SurfaceData surfaceData(@Nullable BloodWaterCache cache, int bx, int bz, int yHint, int yRef) {
        if (cache == null) {
            return null;
        }
        long k = BloodSurfaceFluid.keyXZ(bx, bz);
        BloodFluidSurfaceCache.SurfaceData cached = cache.queryHit.get(k);
        if (cached != null) {
            return cached;
        }
        if (cache.queryMiss.contains(k)) {
            return null;
        }
        BloodFluidSurfaceCache.SurfaceData sd = cache.cachedTop != null && bx == ((net.minecraft.core.Vec3i) cache.cachedTop).getX() && bz == ((net.minecraft.core.Vec3i) cache.cachedTop).getZ() && BloodSurfaceFluid.hasHeights(cache) ? new BloodFluidSurfaceCache.SurfaceData(cache.cachedTop, cache.cachedAboveOpen, cache.hNW, cache.hNE, cache.hSW, cache.hSE) : BloodSurfaceFluid.findWaterData(cache, bx, bz, yHint, yRef);
        if (sd != null) {
            cache.queryHit.put(k, sd);
        } else {
            cache.queryMiss.add(k);
        }
        return sd;
    }

    public static long keyXZ(int x, int z) {
        return (long)x << 32 ^ (long)z & 0xFFFFFFFFL;
    }

    public static void scan(@Nonnull BloodFluidSurfaceCache cache, double x, double y, double z) {
        cache.cachedTop = null;
        cache.cachedAboveOpen = false;
        cache.hSE = Float.NaN;
        cache.hSW = Float.NaN;
        cache.hNE = Float.NaN;
        cache.hNW = Float.NaN;
        if (cache.world == null) {
            return;
        }
        BlockPos base = BlockPos.containing(x, y, z);
        if (!cache.world.isLoaded(base)) {
            return;
        }
        BlockPos fluidPos = BloodSurfaceFluid.findAtOrBelow(cache, base, cache.fluidMaterial, cache.scanDownMax);
        if (fluidPos == null) {
            return;
        }
        BlockPos top = BloodSurfaceFluid.findTop(cache, fluidPos, cache.fluidMaterial, cache.scanUpMax);
        boolean aboveOpen = false;
        BlockPos above = top.above();
        if (cache.world.isLoaded(above)) {
            BlockState aState = BloodSurfaceFluid.safeState(cache, above);
            aboveOpen = cache.aboveOpenRule.isOpen(aState);
        }
        cache.cachedTop = top;
        cache.cachedAboveOpen = aboveOpen;
        cache.hNW = BloodSurfaceFluid.cornerHeight((BlockGetter)cache.world, top, cache.fluidMaterial);
        cache.hNE = BloodSurfaceFluid.cornerHeight((BlockGetter)cache.world, top.east(), cache.fluidMaterial);
        cache.hSE = BloodSurfaceFluid.cornerHeight((BlockGetter)cache.world, top.south().east(), cache.fluidMaterial);
        cache.hSW = BloodSurfaceFluid.cornerHeight((BlockGetter)cache.world, top.south(), cache.fluidMaterial);
    }

    @Nullable
    public static BlockPos findAtOrBelow(@Nonnull BloodFluidSurfaceCache cache, @Nonnull BlockPos base, @Nonnull GoreMaterial mat, int maxDown) {
        BlockState s0 = BloodSurfaceFluid.safeState(cache, base);
        if (s0 != null && GoreMaterial.of(s0) == mat) {
            return base;
        }
        for (int d = 1; d <= maxDown; ++d) {
            BlockPos p = base.below(d);
            if (cache.world == null || !cache.world.isLoaded(p)) break;
            BlockState st = BloodSurfaceFluid.safeState(cache, p);
            if (st == null || GoreMaterial.of(st) != mat) continue;
            return p;
        }
        return null;
    }

    @Nonnull
    public static BlockPos findTop(@Nonnull BloodFluidSurfaceCache cache, @Nonnull BlockPos start, @Nonnull GoreMaterial mat, int maxUp) {
        BlockPos top = start;
        for (int i = 0; i < maxUp; ++i) {
            BlockState st;
            BlockPos up = top.above();
            if (cache.world == null || !cache.world.isLoaded(up) || (st = BloodSurfaceFluid.safeState(cache, up)) == null || GoreMaterial.of(st) != mat) break;
            top = up;
        }
        return top;
    }

    @Nullable
    public static BlockState safeState(@Nonnull BloodFluidSurfaceCache cache, @Nonnull BlockPos pos) {
        try {
            return cache.world != null ? cache.world.getBlockState(pos) : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    public static float cornerHeight(@Nonnull BlockGetter world, @Nonnull BlockPos pos, @Nonnull GoreMaterial material) {
        int weight = 0;
        float acc = 0.0f;
        for (int j = 0; j < 4; ++j) {
            BlockPos p = pos.offset(-(j & 1), 0, -(j >> 1 & 1));
            BlockState up = world.getBlockState(p.above());
            if (GoreMaterial.of(up) == material) {
                return 1.0f;
            }
            BlockState st = world.getBlockState(p);
            GoreMaterial m = GoreMaterial.of(st);
            if (m == material) {
                int lvl = 0;
                if (st.getBlock() instanceof LiquidBlock) {
                    Integer lv = (Integer)st.getValue((Property)LiquidBlock.LEVEL);
                    lvl = lv != null ? lv : 0;
                }
                float pct = GoreUtil.liquidHeightPercent((int)lvl);
                if (lvl >= 8 || lvl == 0) {
                    acc += pct * 10.0f;
                    weight += 10;
                }
                acc += pct;
                ++weight;
                continue;
            }
            if (m.isSolid()) continue;
            acc += 1.0f;
            ++weight;
        }
        if (weight == 0) {
            return 0.0f;
        }
        return 1.0f - acc / (float)weight;
    }

    @Nullable
    private static BloodFluidSurfaceCache.SurfaceData findWaterData(@Nonnull BloodWaterCache cache, int bx, int bz, int yHint, int yRef) {
        if (cache.world == null) {
            return null;
        }
        BlockPos base = BlockPos.containing(bx, yHint, bz);
        if (!cache.world.isLoaded(base)) {
            return null;
        }
        BlockPos waterPos = BloodSurfaceFluid.findAtOrBelow(cache, base, GoreMaterial.WATER, 8);
        if (waterPos == null) {
            return null;
        }
        BlockPos top = BloodSurfaceFluid.findTop(cache, waterPos, GoreMaterial.WATER, 256);
        if (Math.abs(((net.minecraft.core.Vec3i) top).getY() - yRef) > 1) {
            return null;
        }
        boolean aboveOpen = false;
        BlockPos above = top.above();
        if (cache.world.isLoaded(above)) {
            BlockState aState = BloodSurfaceFluid.safeState(cache, above);
            aboveOpen = aState != null && GoreMaterial.of(aState) != GoreMaterial.WATER;
        }
        float nw = BloodSurfaceFluid.cornerHeight((BlockGetter)cache.world, top, GoreMaterial.WATER);
        float ne = BloodSurfaceFluid.cornerHeight((BlockGetter)cache.world, top.east(), GoreMaterial.WATER);
        float se = BloodSurfaceFluid.cornerHeight((BlockGetter)cache.world, top.south().east(), GoreMaterial.WATER);
        float sw = BloodSurfaceFluid.cornerHeight((BlockGetter)cache.world, top.south(), GoreMaterial.WATER);
        return new BloodFluidSurfaceCache.SurfaceData(top, aboveOpen, nw, ne, sw, se);
    }
}

