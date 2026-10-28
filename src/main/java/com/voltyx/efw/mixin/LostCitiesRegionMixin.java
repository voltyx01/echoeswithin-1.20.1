package com.voltyx.efw.mixin;

import com.voltyx.efw.compat.LostCitiesRegions;
import mcjty.lostcities.worldgen.IDimensionInfo;
import net.minecraft.world.level.WorldGenLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * World generation froze for good (server watchdog, "A single server tick has taken 81064 ms"): LC2H replaces Lost Cities'
 * DefaultDimensionInfo.getWorld() with a thread-local region that is only set on the thread that called setWorld; every other
 * worldgen thread gets the ServerLevel. Parts with block entity data (chests with tags...) then write their data through
 * ServerLevel.getChunk of the very chunk being generated, which waits for that chunk to finish: a deadlock. LC2H fixed the same for
 * the loot, not for these parts. Here the parts get the region of the chunk being generated (LostCitiesRegions), as Lost Cities
 * itself intends. The vines of ChunkFixer have the same problem, see LostCitiesChunkFixerMixin.
 */
@Pseudo
@Mixin(targets = "mcjty.lostcities.worldgen.LostCityTerrainFeature", remap = false)
public abstract class LostCitiesRegionMixin {
    @Redirect(method = "generatePart*", at = @At(value = "INVOKE",
            target = "Lmcjty/lostcities/worldgen/IDimensionInfo;getWorld()Lnet/minecraft/world/level/WorldGenLevel;"),
            remap = false, require = 0)
    private WorldGenLevel efw$generationRegion(IDimensionInfo provider) {
        WorldGenLevel region = LostCitiesRegions.current();
        return region != null ? region : provider.getWorld();
    }
}
