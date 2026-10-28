package com.voltyx.efw.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

/** Locks Reforged, only touched when it is installed (its classes stay unloaded otherwise). */
public final class LocksCompat {
    private static final boolean LOADED = ModList.get().isLoaded("locks");

    private LocksCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    /** A closed lock hangs on the block (the lockables are synced, so this works on the client too). */
    public static boolean isLocked(Level level, BlockPos pos) {
        return LOADED && Impl.locked(level, pos);
    }

    private static final class Impl {
        static boolean locked(Level level, BlockPos pos) {
            return melonslise.locks.common.util.LocksUtil.locked(level, pos);
        }
    }
}
