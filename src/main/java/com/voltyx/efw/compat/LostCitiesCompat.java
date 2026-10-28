package com.voltyx.efw.compat;

import mcjty.lostcities.LostCities;
import mcjty.lostcities.api.ILostCityInformation;
import mcjty.lostcities.api.ILostChunkInfo;
import net.minecraft.world.level.WorldGenLevel;

/** Whether a chunk belongs to a Lost Cities city (Lost Cities is optional: any failure means "not a city"). */
public final class LostCitiesCompat {
    private LostCitiesCompat() {
    }

    public static boolean isCityChunk(WorldGenLevel level, int chunkX, int chunkZ) {
        try {
            ILostCityInformation info = LostCities.lostCitiesImp.getLostInfo(level.getLevel());
            if (info == null) {
                return false;
            }
            ILostChunkInfo chunk = info.getChunkInfo(chunkX, chunkZ);
            return chunk != null && chunk.isCity();
        } catch (Throwable t) {
            return false;
        }
    }
}
