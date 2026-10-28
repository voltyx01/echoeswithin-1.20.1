package com.voltyx.efw.gore.particle.blocksupport;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.Util;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
public final class PistonSupport {
    @Nullable
    public static MovingInfo getMovingInfo(@Nullable Level world, @Nullable BlockPos pos, float partialTicks) {
        return PistonSupport.getMovingInfo(world, pos, partialTicks, null);
    }

    private static boolean isPistonBaseState(@Nullable BlockState blockState) {
        if (blockState == null) {
            return false;
        }
        Block block = blockState.getBlock();
        return block == Blocks.PISTON || block == Blocks.STICKY_PISTON || block instanceof PistonBaseBlock;
    }

    @Nullable
    private static BlockState forceExtendedIfPossible(@Nullable BlockState blockState) {
        if (blockState == null) {
            return null;
        }
        if (!(blockState.getBlock() instanceof PistonBaseBlock)) {
            return blockState;
        }
        try {
            return blockState.setValue(PistonBaseBlock.EXTENDED, Boolean.TRUE);
        }
        catch (Throwable throwable) {
            return blockState;
        }
    }

    private static boolean isRetractingBaseTE(PistonMovingBlockEntity piston) {
        try {
            return piston.isSourcePiston() && !piston.isExtending();
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    @Nullable
    public static MovingInfo getMovingInfo(@Nullable Level world, @Nullable BlockPos pos, float partialTicks, @Nullable ParticleBlood blood) {
        Vec3 off;
        BlockState moved;
        BlockEntity tileEntity;
        BlockState blockState;
        if (world == null || pos == null) {
            return null;
        }
        try {
            blockState = world.getBlockState(pos);
        }
        catch (Throwable throwable) {
            return null;
        }
        if (blockState.getBlock() != Blocks.MOVING_PISTON) {
            return null;
        }
        try {
            tileEntity = world.getBlockEntity(pos);
        }
        catch (Throwable throwable) {
            return null;
        }
        if (!(tileEntity instanceof PistonMovingBlockEntity)) {
            return null;
        }
        PistonMovingBlockEntity piston = (PistonMovingBlockEntity)tileEntity;
        if (blood != null && PistonSupport.isPistonBaseState(blood.cache.host.base) && PistonSupport.isRetractingBaseTE(piston)) {
            BlockState base = PistonSupport.forceExtendedIfPossible(blood.cache.host.base);
            return new MovingInfo(base, Util.ZERO, true);
        }
        try {
            moved = piston.getMovedState();
        }
        catch (Throwable throwable) {
            return null;
        }
        try {
            off = new Vec3((double)piston.getXOff(partialTicks), (double)piston.getYOff(partialTicks), (double)piston.getZOff(partialTicks));
        }
        catch (Throwable throwable) {
            off = Util.ZERO;
        }
        return new MovingInfo(moved, off, false);
    }

    public static final class MovingInfo {
        public final BlockState movedState;
        public final Vec3 offset;
        public final boolean staticBaseNoOffset;

        public MovingInfo(BlockState movedState, Vec3 offset, boolean staticBaseNoOffset) {
            this.movedState = movedState;
            this.offset = offset;
            this.staticBaseNoOffset = staticBaseNoOffset;
        }
    }
}

