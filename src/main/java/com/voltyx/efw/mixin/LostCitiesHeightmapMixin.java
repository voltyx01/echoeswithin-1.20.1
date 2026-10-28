package com.voltyx.efw.mixin;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumSet;

/**
 * Floating paths, water columns, geysers and the like over Lost Cities: the city is built in the very first decoration step and
 * reshapes the terrain (hills cut down to street level, ground raised), but it only tells the chunk's heightmaps about it through
 * Heightmap.update, which can raise a height and never lower it, and it leaves the worldgen heightmaps (WORLD_SURFACE_WG,
 * OCEAN_FLOOR_WG) alone. Every feature placed after it by height then stands on the old terrain. Here all heightmaps of the chunk are
 * made again from its real blocks right after Lost Cities has written them.
 */
@Pseudo
@Mixin(targets = "mcjty.lostcities.worldgen.ChunkDriver", remap = false)
public abstract class LostCitiesHeightmapMixin {
    @Unique
    private static final EnumSet<Heightmap.Types> EFW$TYPES = EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG,
            Heightmap.Types.WORLD_SURFACE, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);

    @Inject(method = "actuallyGenerate", at = @At("TAIL"), remap = false, require = 0)
    private void efw$rebuildHeightmaps(ChunkAccess chunk, CallbackInfo ci) {
        if (chunk != null) {
            Heightmap.primeHeightmaps(chunk, EFW$TYPES);
        }
    }
}
