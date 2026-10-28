package com.voltyx.efw.gore.particle.common;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The few block materials of 1.12 the blood looks at (1.20 has no materials): air, water (the water block and bubble columns, not
 * waterlogged blocks, as 1.12 had none) and lava.
 */
public enum GoreMaterial {
    AIR, WATER, LAVA, OTHER;

    public static GoreMaterial of(BlockState state) {
        if (state == null || state.isAir()) {
            return AIR;
        }
        if (state.is(Blocks.WATER) || state.is(Blocks.BUBBLE_COLUMN)) {
            return WATER;
        }
        if (state.is(Blocks.LAVA)) {
            return LAVA;
        }
        return OTHER;
    }

    /** 1.12 Material.isSolid / blocksMovement / isOpaque, as far as these few materials go. */
    public boolean isSolid() {
        return this == OTHER;
    }

    public boolean blocksMovement() {
        return this == OTHER;
    }

    public boolean isOpaque() {
        return this == OTHER;
    }

    public boolean isLiquid() {
        return this == WATER || this == LAVA;
    }
}
