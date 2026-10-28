package com.voltyx.efw.tech;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.ItemStack;

/**
 * Batteries installed into devices (port of DeviceBatteryHelper). The installed battery lives in the device NBT
 * ("InstalledBattery"), its charge is mirrored in "battery_charge" for quick reads (terminal, bodycam layer).
 */
public final class BatteryHelper {
    public static final String NBT_INSTALLED = "InstalledBattery";
    public static final String NBT_CHARGE = "battery_charge";
    public static final String NBT_MAX_CHARGE = "battery_max_charge";

    private BatteryHelper() {
    }

    public static boolean isBattery(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BatteryItem;
    }

    public static boolean isRechargeable(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BatteryItem b && b.rechargeable;
    }

    public static int getMaxCharge(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BatteryItem b ? b.maxCharge : 48000;
    }

    public static boolean isPoweredDevice(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof BodycamItem || stack.getItem() instanceof PortableMapItem
                || com.voltyx.efw.nvg.NvgHelmets.isNvgHelmet(stack));
    }

    /** A rechargeable battery, or a device with one inside (the things a generator can charge). */
    public static boolean isChargeableItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (isBattery(stack)) {
            return isRechargeable(stack);
        }
        if (isPoweredDevice(stack)) {
            ItemStack bat = getInstalledBattery(stack);
            return !bat.isEmpty() && isRechargeable(bat);
        }
        return false;
    }

    /** Chargeable and not full yet. */
    public static boolean canCharge(ItemStack stack) {
        if (!isChargeableItem(stack)) {
            return false;
        }
        ItemStack bat = isBattery(stack) ? stack : getInstalledBattery(stack);
        return BatteryItem.getCharge(bat) < getMaxCharge(bat);
    }

    /** Adds charge to a rechargeable battery or to the one installed in a device; false when nothing changed. */
    public static boolean chargeItem(ItemStack stack, int amount) {
        if (!canCharge(stack)) {
            return false;
        }
        if (isBattery(stack)) {
            BatteryItem.setCharge(stack, Math.min(getMaxCharge(stack), BatteryItem.getCharge(stack) + amount));
            return true;
        }
        ItemStack bat = getInstalledBattery(stack);
        BatteryItem.setCharge(bat, Math.min(getMaxCharge(bat), BatteryItem.getCharge(bat) + amount));
        setInstalledBattery(stack, bat);
        return true;
    }

    /**
     * The battery installed in a device. Old devices with only a "battery_charge" left get a fallback battery so that the
     * charge is not lost.
     */
    public static ItemStack getInstalledBattery(ItemStack device) {
        if (!isPoweredDevice(device)) {
            return ItemStack.EMPTY;
        }
        CompoundTag tag = device.getTag();
        if (tag != null) {
            if (tag.contains(NBT_INSTALLED, 10)) {
                return ItemStack.of(tag.getCompound(NBT_INSTALLED));
            }
            if (tag.contains(NBT_CHARGE)) {
                int oldCharge = tag.getInt(NBT_CHARGE);
                if (oldCharge > 0) {
                    ItemStack fallback = new ItemStack(TechContent.BATTERY.get());
                    BatteryItem.setCharge(fallback, oldCharge);
                    tag.put(NBT_INSTALLED, fallback.save(new CompoundTag()));
                    tag.putInt(NBT_MAX_CHARGE, 48000);
                    return fallback;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static void setInstalledBattery(ItemStack device, ItemStack battery) {
        if (device.isEmpty()) {
            return;
        }
        if (battery.isEmpty()) {
            removeInstalledBattery(device);
            return;
        }
        CompoundTag tag = device.getOrCreateTag();
        ItemStack single = battery.copy();
        single.setCount(1);
        tag.put(NBT_INSTALLED, single.save(new CompoundTag()));
        tag.putInt(NBT_CHARGE, BatteryItem.getCharge(single));
        tag.putInt(NBT_MAX_CHARGE, getMaxCharge(single));
    }

    public static ItemStack removeInstalledBattery(ItemStack device) {
        CompoundTag tag = device.isEmpty() ? null : device.getTag();
        if (tag == null) {
            return ItemStack.EMPTY;
        }
        ItemStack installed = ItemStack.EMPTY;
        if (tag.contains(NBT_INSTALLED, 10)) {
            installed = ItemStack.of(tag.getCompound(NBT_INSTALLED));
            tag.remove(NBT_INSTALLED);
        } else if (tag.contains(NBT_CHARGE) && tag.getInt(NBT_CHARGE) > 0) {
            installed = new ItemStack(TechContent.BATTERY.get());
            BatteryItem.setCharge(installed, tag.getInt(NBT_CHARGE));
        }
        tag.putInt(NBT_CHARGE, 0);
        return installed;
    }

    /**
     * Drains the device. When the charge runs out a disposable battery disappears, a rechargeable one stays inside at 0%.
     * Returns the charge left (0 when empty or without battery).
     */
    public static int consumeCharge(ItemStack device, int amount) {
        CompoundTag tag = device.isEmpty() ? null : device.getTag();
        if (tag == null) {
            return 0;
        }
        ItemStack battery = getInstalledBattery(device);
        if (battery.isEmpty()) {
            tag.putInt(NBT_CHARGE, 0);
            return 0;
        }
        int current = BatteryItem.getCharge(battery);
        if (current <= 0) {
            tag.putInt(NBT_CHARGE, 0);
            return 0;
        }
        int next = Math.max(0, current - amount);
        BatteryItem.setCharge(battery, next);
        if (next <= 0 && !isRechargeable(battery)) {
            removeInstalledBattery(device);
            return 0;
        }
        setInstalledBattery(device, battery);
        return next;
    }

    public static int getChargePercent(ItemStack stack) {
        if (isBattery(stack)) {
            int max = getMaxCharge(stack);
            return max <= 0 ? 0 : (int) Math.round(BatteryItem.getCharge(stack) / (double) max * 100.0);
        }
        if (isPoweredDevice(stack)) {
            ItemStack bat = getInstalledBattery(stack);
            return bat.isEmpty() ? 0 : getChargePercent(bat);
        }
        return 0;
    }

    public static boolean isBarVisible(ItemStack device) {
        ItemStack bat = getInstalledBattery(device);
        return !bat.isEmpty() && BatteryItem.getCharge(bat) < getMaxCharge(bat);
    }

    public static int getBarWidth(ItemStack device) {
        ItemStack bat = getInstalledBattery(device);
        int max = getMaxCharge(bat);
        return bat.isEmpty() || max <= 0 ? 13 : Math.round(13.0F * BatteryItem.getCharge(bat) / max);
    }

    public static int getBarColor(ItemStack device) {
        int percent = getChargePercent(device);
        if (percent > 50) {
            return 0x00FF00;
        }
        if (percent > 20) {
            return 0xFFFF00;
        }
        return 0xFF0000;
    }

    /**
     * Right click on a device in a container (InventoryChargeHandler / PacketChargeDevice): with a battery on the cursor it
     * is installed or swapped, with an empty cursor the installed battery is taken out. Runs on both sides like the bundle.
     */
    public static boolean handleClick(ItemStack device, ItemStack carried, ClickAction action, Player player, SlotAccess carriedAccess) {
        if (action != ClickAction.SECONDARY || !isPoweredDevice(device)) {
            return false;
        }
        if (isBattery(carried)) {
            ItemStack oldBattery = getInstalledBattery(device);
            if (carried.getCount() == 1) {
                setInstalledBattery(device, carried);
                carriedAccess.set(oldBattery);
            } else {
                ItemStack single = carried.split(1);
                setInstalledBattery(device, single);
                carriedAccess.set(carried);
                if (!oldBattery.isEmpty() && !player.getInventory().add(oldBattery)) {
                    player.drop(oldBattery, false);
                }
            }
            player.playSound(SoundEvents.ARMOR_EQUIP_IRON, 0.8F, 1.2F);
            return true;
        }
        if (carried.isEmpty()) {
            ItemStack oldBattery = getInstalledBattery(device);
            if (!oldBattery.isEmpty()) {
                removeInstalledBattery(device);
                carriedAccess.set(oldBattery);
                player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC, 0.8F, 0.9F);
                return true;
            }
        }
        return false;
    }
}
