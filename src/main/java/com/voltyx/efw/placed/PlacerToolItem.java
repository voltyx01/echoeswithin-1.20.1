package com.voltyx.efw.placed;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The item placer (ItemPlacerTool): use in the air picks the hotbar slot whose item is put down (sneak = backwards), use on a block
 * puts that item there and opens the settings, use on an item that is already there opens its settings.
 */
public class PlacerToolItem extends Item {
    public PlacerToolItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static int getSelectedSlot(ItemStack tool) {
        CompoundTag tag = tool.getTag();
        return tag != null && tag.contains("TargetSlot") ? Math.max(0, Math.min(8, tag.getInt("TargetSlot"))) : 0;
    }

    private static void setSelectedSlot(ItemStack tool, int slot) {
        tool.getOrCreateTag().putInt("TargetSlot", Math.max(0, Math.min(8, slot)));
    }

    private static boolean usable(ItemStack stack, ItemStack tool) {
        return !stack.isEmpty() && !(stack.getItem() instanceof PlacerToolItem) && stack != tool;
    }

    /** The hotbar slot the item comes from: the selected one, else the first hotbar slot with something in it. */
    public static int getSourceSlot(Player player, ItemStack tool) {
        int target = getSelectedSlot(tool);
        if (usable(player.getInventory().getItem(target), tool)) {
            return target;
        }
        for (int i = 0; i < 9; i++) {
            if (usable(player.getInventory().getItem(i), tool)) {
                return i;
            }
        }
        return target;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.efw.placer.title").withStyle(ChatFormatting.GOLD));
        Player player = net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient() ? clientPlayer() : null;
        if (player != null) {
            int slot = getSourceSlot(player, stack);
            ItemStack chosen = player.getInventory().getItem(slot);
            if (chosen.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.efw.placer.empty").withStyle(ChatFormatting.RED));
            } else {
                tooltip.add(Component.translatable("tooltip.efw.placer.slot", slot + 1, chosen.getHoverName()).withStyle(ChatFormatting.AQUA));
            }
        }
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.efw.placer.air").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.efw.placer.floor").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.efw.placer.placed").withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    private static Player clientPlayer() {
        return net.minecraft.client.Minecraft.getInstance().player;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack tool = player.getItemInHand(hand);
        int step = player.isShiftKeyDown() ? -1 : 1;
        int current = getSelectedSlot(tool);
        for (int i = 1; i <= 9; i++) {
            int candidate = ((current + i * step) % 9 + 9) % 9;
            ItemStack s = player.getInventory().getItem(candidate);
            if (usable(s, tool)) {
                setSelectedSlot(tool, candidate);
                if (level.isClientSide) {
                    player.displayClientMessage(Component.translatable("message.efw.placer.selected", candidate + 1, s.getHoverName())
                            .withStyle(ChatFormatting.YELLOW), true);
                    level.playSound(player, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5F, 1.2F);
                }
                return InteractionResultHolder.sidedSuccess(tool, level.isClientSide);
            }
        }
        if (level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.efw.placer.nothing").withStyle(ChatFormatting.RED), true);
        }
        return InteractionResultHolder.fail(tool);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        Level level = ctx.getLevel();
        if (player == null) {
            return InteractionResult.PASS;
        }
        BlockPos clicked = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        ItemStack tool = ctx.getItemInHand();
        int slot = getSourceSlot(player, tool);
        ItemStack source = player.getInventory().getItem(slot);
        if (!usable(source, tool)) {
            if (level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.efw.placer.empty_slot").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        BlockState clickedState = level.getBlockState(clicked);
        BlockPos target = clickedState.canBeReplaced() ? clicked : clicked.relative(face);
        BlockState targetState = level.getBlockState(target);
        if (!player.mayUseItemAt(target, face, tool) || !targetState.canBeReplaced() || !level.isInWorldBounds(target)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        level.setBlock(target, PlacedContent.PLACED_ITEM.get().defaultBlockState(), 3);
        if (level.getBlockEntity(target) instanceof PlacedItemBlockEntity placed) {
            ItemStack one = source.copy();
            one.setCount(1);
            placed.setStack(one);
            if (!player.isCreative()) {
                source.shrink(1);
            }
            Vec3 hit = ctx.getClickLocation();
            boolean top = face == Direction.UP;
            float yaw = Math.round(player.getYRot() / 15.0f) * 15.0f;
            yaw = (yaw % 360.0f + 360.0f) % 360.0f;
            placed.setLayout(top ? (float) (hit.x - clicked.getX()) : 0.5f, 0.02f, top ? (float) (hit.z - clicked.getZ()) : 0.5f,
                    yaw, 0.0f, 0.0f, 1.0f, false, 0.0f, 0.0f, 0.0f);
            level.playSound(null, target, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);
            if (player instanceof ServerPlayer sp) {
                PlacedContent.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new PlacedContent.OpenConfig(target));
            }
        }
        return InteractionResult.CONSUME;
    }
}
