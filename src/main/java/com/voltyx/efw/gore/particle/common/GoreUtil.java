package com.voltyx.efw.gore.particle.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/** Small 1.12 block helpers the blood code was written with, done the 1.20 way. */
public final class GoreUtil {
    public static final Direction[] HORIZONTALS = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};

    private GoreUtil() {
    }

    /** 1.12 IBlockState.getCollisionBoundingBox: the bounds of the collision shape at the origin, null when it has none. */
    @Nullable
    public static AABB collisionBox(BlockState state, BlockGetter level, BlockPos pos) {
        VoxelShape shape = state.getCollisionShape(level, pos);
        return shape.isEmpty() ? null : shape.bounds();
    }

    /** 1.12 IBlockState.getBoundingBox (the selection box) at the origin; a full cube when it has none. */
    public static AABB boundingBox(BlockState state, BlockGetter level, BlockPos pos) {
        VoxelShape shape = state.getShape(level, pos);
        return shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
    }

    /** 1.12 addCollisionBoxToList: the collision boxes of the block (in world coordinates) that touch the mask. */
    public static void addCollisionBoxes(BlockState state, BlockGetter level, BlockPos pos, AABB mask, List<AABB> out) {
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return;
        }
        for (AABB box : shape.toAabbs()) {
            AABB world = box.move(pos);
            if (world.intersects(mask)) {
                out.add(world);
            }
        }
    }

    /**
     * 1.12 World.rayTraceBlocks(start, end, stopOnLiquid, ignoreBlockWithoutBoundingBox, returnLastUncollidableBlock): the block hit,
     * or null when nothing was hit. With ignoreBlockWithoutBoundingBox, as the blood always asks, 1.12 never returned a "missed"
     * result: through air the answer is null, which the blood checks for (e.g. whether the face it lands on is open).
     */
    @Nullable
    public static BlockHitResult rayTrace(Level level, Vec3 start, Vec3 end, boolean stopOnLiquid, boolean ignoreNoBox, boolean returnLast) {
        BlockHitResult hit = level.clip(new ClipContext(start, end, ignoreNoBox ? ClipContext.Block.COLLIDER : ClipContext.Block.OUTLINE,
                stopOnLiquid ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, null));
        return hit.getType() == HitResult.Type.MISS ? null : hit;
    }

    public static BlockHitResult rayTrace(Level level, Vec3 start, Vec3 end, boolean stopOnLiquid) {
        return rayTrace(level, start, end, stopOnLiquid, false, false);
    }

    public static BlockHitResult rayTrace(Level level, Vec3 start, Vec3 end) {
        return rayTrace(level, start, end, false, false, false);
    }

    /** 1.12 ActiveRenderInfo.getBlockStateAtEntityViewpoint: the block the camera is in. */
    @Nullable
    public static BlockState stateAtCamera() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        return mc.level.getBlockState(mc.gameRenderer.getMainCamera().getBlockPosition());
    }

    /** The rendered height (0..1) of the fluid surface in this block (1.12 BlockLiquid.getLiquidHeightPercent turned around). */
    public static float fluidHeight(BlockGetter level, BlockPos pos, BlockState state) {
        return state.getFluidState().getHeight(level, pos);
    }

    /** 1.12 BlockLiquid.getLiquidHeightPercent: how much of the block is empty above the fluid of that level. */
    public static float liquidHeightPercent(int meta) {
        if (meta >= 8) {
            meta = 0;
        }
        return (float) (meta + 1) / 9.0f;
    }
}
