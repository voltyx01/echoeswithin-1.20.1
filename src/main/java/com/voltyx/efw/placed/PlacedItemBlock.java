package com.voltyx.efw.placed;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * An item lying on the floor or on a table (BlockPlacedItem). The block is invisible, a block entity renderer draws the item. A
 * structure that is placed turned keeps the item where it was: the turns are kept in the {@link #TURNS} property and applied when the
 * item is drawn, because the layout inside the block entity is not turned with the structure.
 */
public class PlacedItemBlock extends Block implements EntityBlock {
    /** Quarter turns clockwise (seen from above) the structure was placed with. */
    public static final IntegerProperty TURNS = IntegerProperty.create("turns", 0, 3);
    private static final VoxelShape DEFAULT_SHAPE = Shapes.box(0.1, 0.0, 0.1, 0.9, 0.25, 0.9);

    public PlacedItemBlock() {
        super(Properties.of().mapColor(MapColor.NONE).strength(0.2F, 1.0F).noCollission().noOcclusion().noLootTable()
                .pushReaction(PushReaction.DESTROY));
        registerDefaultState(stateDefinition.any().setValue(TURNS, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TURNS);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        int add = switch (rotation) {
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
            default -> 0;
        };
        return state.setValue(TURNS, (state.getValue(TURNS) + add) & 3);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlacedItemBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (!(level.getBlockEntity(pos) instanceof PlacedItemBlockEntity placed)) {
            return DEFAULT_SHAPE;
        }
        double half = Math.max(0.15, 0.35 * placed.scale());
        double minX = Math.max(0.0, placed.offsetX() - half);
        double minY = Math.max(0.0, placed.offsetY() - 0.05);
        double minZ = Math.max(0.0, placed.offsetZ() - half);
        double maxX = Math.min(1.0, placed.offsetX() + half);
        double maxY = Math.min(1.0, placed.offsetY() + half);
        double maxZ = Math.min(1.0, placed.offsetZ() + half);
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
            return DEFAULT_SHAPE;
        }
        // the same turn the renderer applies: (x, z) -> (1 - z, x) for each quarter
        for (int i = 0; i < state.getValue(TURNS); i++) {
            double nMinX = 1.0 - maxZ, nMaxX = 1.0 - minZ, nMinZ = minX, nMaxZ = maxX;
            minX = nMinX;
            maxX = nMaxX;
            minZ = nMinZ;
            maxZ = nMaxZ;
        }
        return Shapes.box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof PlacedItemBlockEntity placed)) {
            return InteractionResult.PASS;
        }
        // the tool opens the settings of the item
        if (player.getMainHandItem().getItem() instanceof PlacerToolItem) {
            if (level.isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.voltyx.efw.placed.client.PlacedClient.openConfig(pos));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // otherwise the item is picked up
        if (placed.isLocked() && !player.isCreative()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack item = placed.getStack().copy();
            if (!item.isEmpty()) {
                placed.setStack(ItemStack.EMPTY);
                if (!player.getInventory().add(item)) {
                    player.drop(item, false);
                }
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F,
                        ((level.random.nextFloat() - level.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
                if (player instanceof ServerPlayer sp && PlacedConfig.isAutoInspect(item)) {
                    PlacedContent.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new PlacedContent.OpenInspect(item));
                }
            }
            level.removeBlock(pos, false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A broken (not picked up) block lets go of its item unless it is locked. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof PlacedItemBlockEntity placed
                && !placed.getStack().isEmpty() && !placed.isLocked()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, placed.getStack());
            placed.setStack(ItemStack.EMPTY);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
