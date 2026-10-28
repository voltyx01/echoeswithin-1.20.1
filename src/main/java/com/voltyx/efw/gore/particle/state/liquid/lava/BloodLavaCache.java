package com.voltyx.efw.gore.particle.state.liquid.lava;

import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceFluid;
import com.voltyx.efw.gore.particle.state.liquid.BloodFluidSurfaceCache;
import javax.annotation.Nullable;
import com.voltyx.efw.gore.particle.common.GoreMaterial;
import net.minecraft.world.level.Level;
public final class BloodLavaCache
extends BloodFluidSurfaceCache {
    public BloodLavaCache(@Nullable Level world) {
        super(world, GoreMaterial.LAVA, 8, 256, 4, BloodSurfaceFluid.LAVA_OPEN_RULE);
    }
}

