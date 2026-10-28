package com.voltyx.efw.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * With a TACZ gun in the main hand the off hand holds the gun too, so a glowing item there is put away and gives no dynamic light
 * (as with the two-handed weapons). Sodium Dynamic Lights measures the items of an entity one by one; while it does, the off hand
 * stack to skip is kept here.
 */
public final class GunOffhandLight {
    private static ItemStack hidden;

    private GunOffhandLight() {
    }

    public static void begin(LivingEntity entity) {
        ItemStack off = entity.getOffhandItem();
        hidden = !off.isEmpty() && TaczCompat.isLoaded() && TaczCompat.isGun(entity.getMainHandItem()) ? off : null;
    }

    public static void end() {
        hidden = null;
    }

    public static boolean isHidden(ItemStack stack) {
        return stack != null && stack == hidden;
    }
}
