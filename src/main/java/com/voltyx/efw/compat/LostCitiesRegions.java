package com.voltyx.efw.compat;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;

import java.lang.reflect.Method;

/**
 * The region of the chunk Lost Cities is generating on this thread: Lost Cities' own generation context, else LC2H's region context.
 * LC2H hands every other worldgen thread the ServerLevel from IDimensionInfo.getWorld(), and a ServerLevel.getChunk from inside
 * generation can wait for the very chunk being generated: the server freezes for good. See LostCitiesRegionMixin and
 * LostCitiesChunkFixerMixin.
 */
public final class LostCitiesRegions {
    private static Method lcWorld;
    private static Method lc2hRegion;
    private static boolean resolved;

    private LostCitiesRegions() {
    }

    /** The current generation region, or null outside Lost Cities generation. */
    public static WorldGenLevel current() {
        resolve();
        for (Method m : new Method[]{lcWorld, lc2hRegion}) {
            if (m == null) {
                continue;
            }
            try {
                if (m.invoke(null) instanceof WorldGenRegion region) {
                    return region;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // fall through to the next source
            }
        }
        return null;
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Method m = Class.forName("mcjty.lostcities.worldgen.GenerationContext").getDeclaredMethod("currentWorld");
            m.setAccessible(true);
            lcWorld = m;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // older Lost Cities without a generation context
        }
        try {
            lc2hRegion = Class.forName("org.admany.lc2h.worldgen.lostcities.LostCityGenerationRegionContext").getMethod("current");
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // LC2H not installed
        }
    }
}
