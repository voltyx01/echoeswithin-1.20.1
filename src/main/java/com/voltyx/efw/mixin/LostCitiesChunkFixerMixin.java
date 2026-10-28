package com.voltyx.efw.mixin;

import com.voltyx.efw.compat.LostCitiesRegions;
import mcjty.lostcities.worldgen.IDimensionInfo;
import net.minecraft.world.level.WorldGenLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The same freeze as in LostCitiesRegionMixin, from the vines on buildings: ChunkFixer.fix gives generateVines the world from
 * IDimensionInfo.getWorld(), under LC2H the ServerLevel, and generateVines asks it for the neighbour chunks. The server thread then
 * loads the neighbour, which waits for the chunk being generated, which waits for the server thread: opening a world to LAN or
 * walking into new city chunks froze the server for good. The fixer works in the region of the chunk being generated instead.
 */
@Pseudo
@Mixin(targets = "mcjty.lostcities.worldgen.ChunkFixer", remap = false)
public abstract class LostCitiesChunkFixerMixin {
    @Redirect(method = {"fix", "generateVines"}, at = @At(value = "INVOKE",
            target = "Lmcjty/lostcities/worldgen/IDimensionInfo;getWorld()Lnet/minecraft/world/level/WorldGenLevel;"),
            remap = false, require = 0)
    private static WorldGenLevel efw$generationRegion(IDimensionInfo provider) {
        WorldGenLevel region = LostCitiesRegions.current();
        return region != null ? region : provider.getWorld();
    }
}
