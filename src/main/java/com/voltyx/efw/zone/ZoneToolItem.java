package com.voltyx.efw.zone;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Zone marking tool: left click a block = point 1, right click = point 2, sneak + right click = create the zone, sneak +
 * left click = delete the zone there.
 */
public class ZoneToolItem extends Item {
    public ZoneToolItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static BlockPos getPos(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(key) ? BlockPos.of(tag.getLong(key)) : null;
    }

    private static void setPos(ItemStack stack, String key, BlockPos pos) {
        stack.getOrCreateTag().putLong(key, pos.asLong());
    }

    private static Component prefix() {
        return Component.literal("[Зоны] ").withStyle(ChatFormatting.GOLD);
    }

    private static String coords(BlockPos p) {
        return "[" + p.getX() + ", " + p.getY() + ", " + p.getZ() + "]";
    }

    /** Left click on a block; returning false keeps the block from being broken. */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        ItemStack stack = player.getMainHandItem();
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                QuestZones.CHANNEL.sendToServer(new QuestZones.DeleteZoneAt(pos));
            }
        } else {
            setPos(stack, "Pos1", pos);
            if (level.isClientSide) {
                player.displayClientMessage(prefix().copy().append(Component.literal("Точка 1 (Pos1) установлена: ").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(coords(pos)).withStyle(ChatFormatting.YELLOW)), true);
                level.playSound(player, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 1.2F);
            }
        }
        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        Level level = ctx.getLevel();
        ItemStack stack = ctx.getItemInHand();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            return createZone(level, player, stack) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.FAIL;
        }
        BlockPos pos = ctx.getClickedPos();
        setPos(stack, "Pos2", pos);
        if (level.isClientSide) {
            player.displayClientMessage(prefix().copy().append(Component.literal("Точка 2 (Pos2) установлена: ").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(coords(pos)).withStyle(ChatFormatting.YELLOW)), true);
            level.playSound(player, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 1.5F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Sneak + right click in the air also creates the zone when both points are set. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && getPos(stack, "Pos1") != null && getPos(stack, "Pos2") != null) {
            createZone(level, player, stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    private static boolean createZone(Level level, Player player, ItemStack stack) {
        BlockPos p1 = getPos(stack, "Pos1");
        BlockPos p2 = getPos(stack, "Pos2");
        if (p1 == null || p2 == null) {
            if (level.isClientSide) {
                player.displayClientMessage(Component.literal("[Зоны] Сначала установите обе точки! (ЛКМ = Pos1, ПКМ = Pos2)")
                        .withStyle(ChatFormatting.RED), true);
                level.playSound(player, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
            }
            return false;
        }
        if (level.isClientSide) {
            QuestZones.CHANNEL.sendToServer(new QuestZones.CreateZone("Комната", p1, p2));
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Инструмент разметки защищенных зон").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Защищает комнаты от любых разрушений и взрывов.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Управление:").withStyle(ChatFormatting.YELLOW));
        tooltip.add(line("• ЛКМ по блоку: ", "установить точку 1 (Pos1)", ChatFormatting.GRAY));
        tooltip.add(line("• ПКМ по блоку: ", "установить точку 2 (Pos2)", ChatFormatting.GRAY));
        tooltip.add(line("• Shift + ПКМ: ", "создать/сохранить защищенную зону", ChatFormatting.GREEN));
        tooltip.add(line("• Shift + ЛКМ: ", "удалить зону под прицелом", ChatFormatting.RED));
        tooltip.add(Component.empty());
        tooltip.add(posLine("Pos 1: ", getPos(stack, "Pos1")));
        tooltip.add(posLine("Pos 2: ", getPos(stack, "Pos2")));
    }

    private static Component line(String key, String text, ChatFormatting color) {
        return Component.literal(key).withStyle(ChatFormatting.WHITE).append(Component.literal(text).withStyle(color));
    }

    private static Component posLine(String label, BlockPos p) {
        return Component.literal(label).withStyle(ChatFormatting.DARK_AQUA).append(p != null
                ? Component.literal(p.getX() + ", " + p.getY() + ", " + p.getZ()).withStyle(ChatFormatting.WHITE)
                : Component.literal("не установлена").withStyle(ChatFormatting.DARK_GRAY));
    }
}
