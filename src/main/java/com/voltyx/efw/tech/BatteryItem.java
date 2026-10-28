package com.voltyx.efw.tech;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Batteries and Li-ion cells (port of ItemBattery):
 * - battery: 48 000, disposable, vanishes when empty;
 * - lion_battery_1 (green): 120 000, rechargeable;
 * - lion_battery_2 (brown): 250 000, rechargeable;
 * - lion_battery_3 (pink): 500 000, rechargeable.
 */
public class BatteryItem extends Item {
    public final int maxCharge;
    public final boolean rechargeable;
    public final int tier;

    public BatteryItem(int maxCharge, boolean rechargeable, int tier) {
        super(new Item.Properties().stacksTo(rechargeable ? 1 : 16));
        this.maxCharge = maxCharge;
        this.rechargeable = rechargeable;
        this.tier = tier;
    }

    public static int getCharge(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BatteryItem ib)) {
            return 0;
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("charge")) {
            return Math.max(0, Math.min(ib.maxCharge, tag.getInt("charge")));
        }
        return ib.maxCharge; // a new battery is full
    }

    public static void setCharge(ItemStack stack, int charge) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BatteryItem ib)) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt("charge", Math.max(0, Math.min(ib.maxCharge, charge)));
        tag.putInt("max_charge", ib.maxCharge);
    }

    /** Disposable batteries stack up to 16 only while full. */
    @Override
    public int getMaxStackSize(ItemStack stack) {
        if (rechargeable) {
            return 1;
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("charge") && tag.getInt("charge") < maxCharge) {
            return 1;
        }
        return 16;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getCharge(stack) < maxCharge;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return maxCharge <= 0 ? 0 : Math.round(13.0F * getCharge(stack) / maxCharge);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float pct = maxCharge > 0 ? (float) getCharge(stack) / maxCharge : 0f;
        if (pct > 0.5f) {
            return 0x2ECC71;
        }
        if (pct > 0.2f) {
            return 0xF1C40F;
        }
        return 0xE74C3C;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int charge = getCharge(stack);
        int percent = maxCharge > 0 ? (int) Math.round(charge / (double) maxCharge * 100.0) : 0;
        String color = percent > 50 ? "§a" : (percent > 20 ? "§e" : "§c");
        String chargeStr = String.format("%,d", charge).replace(',', ' ').replace(' ', ' ');
        String maxStr = String.format("%,d", maxCharge).replace(',', ' ').replace(' ', ' ');
        tooltip.add(Component.translatable("tooltip.efw.battery.capacity").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(": §f" + chargeStr + " §7/ §f" + maxStr + " §8(" + color + percent + "%§8)")));
        tooltip.add(Component.translatable(rechargeable ? "tooltip.efw.battery.rechargeable" : "tooltip.efw.battery.disposable"));
        tooltip.add(Component.translatable("tooltip.efw.battery.swap_hint"));
    }
}
