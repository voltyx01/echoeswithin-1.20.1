package com.voltyx.efw.gore.particle.state.liquid.water;

import com.voltyx.efw.gore.particle.common.cache.BloodCaches;
import com.voltyx.efw.gore.particle.common.cache.BloodCachesWater;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceFluid;
import com.voltyx.efw.gore.particle.state.liquid.BloodFluidSurfaceCache;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import com.voltyx.efw.gore.particle.common.GoreMaterial;
import net.minecraft.world.level.Level;
public final class BloodWaterCache
extends BloodFluidSurfaceCache {
    public final BloodCaches.Query query = new BloodCaches.Query();
    public final Map<Long, BloodFluidSurfaceCache.SurfaceData> queryHit;
    public final Set<Long> queryMiss;

    public BloodWaterCache(@Nullable Level world) {
        super(world, GoreMaterial.WATER, 8, 256, 4, BloodSurfaceFluid.WATER_OPEN_RULE);
        this.queryHit = this.query.hit;
        this.queryMiss = this.query.miss;
    }

    public void beginRenderQueries() {
        BloodCachesWater.clearQuery(this.query);
    }

    @Deprecated
    public boolean isCachedAboveAir() {
        return this.isCachedAboveOpen();
    }

    @Nullable
    public SurfaceSample sampleAt(double worldX, double worldZ) {
        return BloodSurfaceFluid.sample(this, worldX, worldZ);
    }

    @Nullable
    public BloodFluidSurfaceCache.SurfaceData getSurfaceDataForColumn(int bx, int bz, int yHint, int yRef) {
        return BloodSurfaceFluid.surfaceData(this, bx, bz, yHint, yRef);
    }

    public static final class SurfaceSample {
        public final double y;
        public final double nx;
        public final double ny;
        public final double nz;

        public SurfaceSample(double y, double nx, double ny, double nz) {
            this.y = y;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }
    }
}

