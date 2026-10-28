package com.voltyx.efw.doll;

import com.voltyx.efw.tech.CuriosCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Saya's favourite toy. Held, it is drawn by the first person doll renderer; carried (inventory, backpack, Curios slot) it hangs
 * on the belt. With the body covered in blood the doll wakes up when it is taken into the hand (DollAbility). Shift + use puts
 * it into a free Curios slot (it fits any slot, the 1.12 Baubles TRINKET).
 */
public class DollItem extends Item {
    public DollItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && !level.isClientSide && CuriosCompat.tryEquipAny(player, held)) {
            held.shrink(1);
            return InteractionResultHolder.consume(held);
        }
        return InteractionResultHolder.pass(held);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(com.voltyx.efw.doll.client.DollClientHooks.itemExtensions());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.efw.doll.desc"));
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.efw.doll.trauma_title"));
            tooltip.add(Component.translatable("tooltip.efw.doll.trauma_desc"));
        } else {
            tooltip.add(Component.translatable("tooltip.efw.doll.trauma_shift"));
        }
    }

    public static boolean isDoll(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof DollItem;
    }

    /** The stack is the doll or a container (backpack, bag) with the doll in it. */
    public static boolean containsDoll(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (isDoll(stack)) {
            return true;
        }
        try {
            IItemHandler handler = stack.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    if (isDoll(handler.getStackInSlot(i))) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static boolean isHoldingDoll(Player player) {
        return player != null && (isDoll(player.getMainHandItem()) || isDoll(player.getOffhandItem()));
    }

    /** The doll hangs on the belt: carried anywhere but a hidden Curios slot (a doll only in hidden slots is not drawn). */
    public static boolean showsOnBelt(Player player) {
        if (player == null) {
            return false;
        }
        if (!CuriosCompat.findVisible(player, DollItem::containsDoll).isEmpty() || containsDoll(player.getItemBySlot(EquipmentSlot.CHEST))) {
            return true;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (containsDoll(stack)) {
                return true;
            }
        }
        return containsDoll(player.getOffhandItem());
    }

    /** The doll is anywhere on the player: Curios slots, the chest slot (a worn backpack), the inventory or a bag in it. */
    public static boolean hasDoll(Player player) {
        if (player == null) {
            return false;
        }
        if (!CuriosCompat.findFirst(player, DollItem::containsDoll).isEmpty()) {
            return true;
        }
        if (containsDoll(player.getItemBySlot(EquipmentSlot.CHEST))) {
            return true;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (containsDoll(stack)) {
                return true;
            }
        }
        return containsDoll(player.getOffhandItem());
    }
}
