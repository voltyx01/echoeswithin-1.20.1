package com.voltyx.efw.tech;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The portable map (ItemPortableMap): worn in a Curios slot (it fits any, like the 1.12 Baubles TRINKET) it switches the Xaero minimap,
 * radar, waypoints and world map on while its battery has charge. Without it the Xaero screens and the minimap do nothing.
 */
public class PortableMapItem extends Item {
    public PortableMapItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!level.isClientSide && CuriosCompat.tryEquipAny(player, held)) {
            held.shrink(1);
            return InteractionResultHolder.consume(held);
        }
        return InteractionResultHolder.pass(held);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack carried, Slot slot, ClickAction action, Player player, SlotAccess access) {
        return BatteryHelper.handleClick(stack, carried, action, player, access);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return BatteryHelper.isBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return BatteryHelper.getBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BatteryHelper.getBarColor(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int percent = BatteryHelper.getChargePercent(stack);
        ItemStack installed = BatteryHelper.getInstalledBattery(stack);
        if (installed.isEmpty() || percent <= 0) {
            tooltip.add(Component.translatable("tooltip.efw.battery.required").withStyle(ChatFormatting.RED));
        } else {
            ChatFormatting color = percent > 50 ? ChatFormatting.GREEN : (percent > 20 ? ChatFormatting.YELLOW : ChatFormatting.RED);
            tooltip.add(Component.translatable("tooltip.efw.battery.charge", percent).withStyle(color)
                    .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                    .append(installed.getHoverName().copy().withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY)));
        }
        tooltip.add(Component.translatable("tooltip.efw.portable_map.desc").withStyle(ChatFormatting.GRAY));
    }

    /** The entity wears a map with charge in a Curios slot. */
    public static boolean hasActiveMap(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        ItemStack map = CuriosCompat.findFirst(entity, s -> s.getItem() instanceof PortableMapItem
                && BatteryItem.getCharge(BatteryHelper.getInstalledBattery(s)) > 0);
        return !map.isEmpty();
    }

    /** Once a second a worn map drains its battery (an empty disposable one disappears). */
    static void tickWorn(Player player) {
        if (player.tickCount % 20 != 0) {
            return;
        }
        ItemStack map = CuriosCompat.findFirst(player, s -> s.getItem() instanceof PortableMapItem);
        if (!map.isEmpty()) {
            BatteryHelper.consumeCharge(map, DeviceConfig.MAP_DRAIN_PER_SECOND.get());
        }
    }
}
