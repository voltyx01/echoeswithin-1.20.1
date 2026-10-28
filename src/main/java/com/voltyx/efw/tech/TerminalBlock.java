package com.voltyx.efw.tech;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

/**
 * Wall terminal (port of BlockTerminal): hangs on a solid wall, the hitbox is the display only. Use it at eye level to sit
 * at the console; with an internet module in hand the module is installed; sneak + use shuts it down.
 */
public class TerminalBlock extends HorizontalDirectionalBlock implements EntityBlock {
    // the display cube of terminal.geo.json (origin [-8, 4, 7], size [16, 11, 1])
    private static final VoxelShape NORTH = Shapes.box(0.0, 0.25, 0.9375, 1.0, 0.9375, 1.0);
    private static final VoxelShape SOUTH = Shapes.box(0.0, 0.25, 0.0, 1.0, 0.9375, 0.0625);
    private static final VoxelShape WEST = Shapes.box(0.9375, 0.25, 0.0, 1.0, 0.9375, 1.0);
    private static final VoxelShape EAST = Shapes.box(0.0, 0.25, 0.0, 0.0625, 0.9375, 1.0);

    public TerminalBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(2.0F, 10.0F).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    private static boolean isValidWall(LevelReader level, BlockPos pos, Direction side) {
        if (side == null || !side.getAxis().isHorizontal()) {
            return false;
        }
        BlockPos wallPos = pos.relative(side.getOpposite());
        BlockState wall = level.getBlockState(wallPos);
        return wall.isFaceSturdy(level, wallPos, side) && wall.isCollisionShapeFullBlock(level, wallPos) && wall.canOcclude();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return isValidWall(level, pos, state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (isValidWall(level, pos, ctx.getClickedFace())) {
            return defaultBlockState().setValue(FACING, ctx.getClickedFace());
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (isValidWall(level, pos, side)) {
                return defaultBlockState().setValue(FACING, side);
            }
        }
        return null;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.SUCCESS;
        }
        // only at eye level
        if (pos.getY() != Mth.floor(player.getY()) + 1) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getMainHandItem();
        if (held.is(TechContent.INTERNET_MODULE.get())) {
            if (!terminal.hasInternetModule()) {
                terminal.installInternetModule(player.getName().getString());
                if (!level.isClientSide) {
                    if (!player.isCreative()) {
                        held.shrink(1);
                    }
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.0F, 1.6F);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isShiftKeyDown()) {
            terminal.close();
            if (level.isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.voltyx.efw.tech.client.TechClientHooks.shutdownTerminal());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.voltyx.efw.tech.client.TechClientHooks.toggleTerminal(pos, state.getValue(FACING), terminal));
        }
        terminal.setOpen(true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerminalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == TechContent.TERMINAL_BE.get() ? (l, p, s, be) -> ((TerminalBlockEntity) be).tick() : null;
    }
}
