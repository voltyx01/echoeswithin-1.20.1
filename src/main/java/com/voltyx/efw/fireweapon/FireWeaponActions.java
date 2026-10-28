package com.voltyx.efw.fireweapon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * What cloth, flint and steel and alcohol do to a weapon (shared by the in-world right click and the click with the item on the
 * cursor over the weapon in an inventory). Always runs on the server; the stacks are changed in place.
 */
public final class FireWeaponActions {
    public enum Result {
        /** Nothing to do: the click goes on as usual. */
        NONE,
        /** The click belongs to the fire weapon system but changed nothing. */
        BLOCKED,
        /** The weapon (and the used item) were changed. */
        APPLIED
    }

    private FireWeaponActions() {
    }

    /** True when {@code used} could do something to {@code weapon}; client side check, so a click is not swallowed for nothing. */
    public static boolean canAffect(ItemStack weapon, ItemStack used) {
        if (!FireWeaponHelper.isWeapon(weapon) || used.isEmpty()) {
            return false;
        }
        if (FireWeaponHelper.isWrappingCloth(used)) {
            return !FireWeaponHelper.isWrapped(weapon) || FireWeaponHelper.isInCharPhase(weapon);
        }
        if (FireWeaponHelper.isFlint(used)) {
            return FireWeaponHelper.isWrapped(weapon) && !FireWeaponHelper.isIgnited(weapon) || FireWeaponHelper.isInCharPhase(weapon);
        }
        if (FireWeaponHelper.isAlcohol(used)) {
            return FireWeaponHelper.isWrapped(weapon) && !FireWeaponHelper.isInCharPhase(weapon);
        }
        return false;
    }

    public static Result apply(ServerPlayer player, ItemStack weapon, ItemStack used) {
        if (!canAffect(weapon, used)) {
            return Result.NONE;
        }
        long now = player.level().getGameTime();
        boolean creative = player.getAbilities().instabuild;

        // 1. wrap with cloth (a charred wrap is replaced)
        if (FireWeaponHelper.isWrappingCloth(used)) {
            boolean flaming = FireWeaponHelper.isFlamingCloth(used);
            FireWeaponHelper.setWrapped(weapon, true);
            if (flaming) {
                FireWeaponHelper.soakAndIgnite(weapon, now);
            }
            if (!creative) {
                used.shrink(1);
            }
            sound(player, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 1.2F);
            if (flaming) {
                sound(player, SoundEvents.FLINTANDSTEEL_USE, 1.0F, 0.8F);
            }
            return Result.APPLIED;
        }
        // flint on a charred wrap: nothing to light
        if (FireWeaponHelper.isFlint(used) && FireWeaponHelper.isInCharPhase(weapon)) {
            return Result.BLOCKED;
        }
        // 2. light plain cloth
        if (FireWeaponHelper.isFlint(used) && FireWeaponHelper.isWrapped(weapon) && !FireWeaponHelper.isIgnited(weapon)
                && !FireWeaponHelper.isSoakedReady(weapon)) {
            FireWeaponHelper.igniteCloth(weapon, now);
            damageFlint(player, used);
            sound(player, SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.0F);
            return Result.APPLIED;
        }
        // 3. soak with alcohol: not burning -> ready for the flint, burning -> flares up at once in SOAKED mode
        if (FireWeaponHelper.isAlcohol(used) && FireWeaponHelper.isWrapped(weapon) && !FireWeaponHelper.isInCharPhase(weapon)) {
            boolean burning = FireWeaponHelper.isIgnited(weapon);
            if (!burning && FireWeaponHelper.isSoakedReady(weapon)) {
                return Result.BLOCKED; // already soaked
            }
            if (burning) {
                FireWeaponHelper.soakAndIgnite(weapon, now);
                sound(player, SoundEvents.FLINTANDSTEEL_USE, 1.0F, 0.8F);
            } else {
                FireWeaponHelper.markSoaked(weapon);
            }
            if (!creative) {
                boolean bottle = FireWeaponHelper.leavesBottle(used);
                used.shrink(1);
                if (bottle) {
                    ItemStack empty = FireWeaponHelper.emptyBottle();
                    if (!empty.isEmpty() && !player.getInventory().add(empty)) {
                        player.drop(empty, false);
                    }
                }
            }
            sound(player, SoundEvents.BOTTLE_FILL, 0.8F, 0.9F);
            return Result.APPLIED;
        }
        // 4. light soaked cloth
        if (FireWeaponHelper.isFlint(used) && FireWeaponHelper.isSoakedReady(weapon) && !FireWeaponHelper.isIgnited(weapon)) {
            FireWeaponHelper.soakAndIgnite(weapon, now);
            damageFlint(player, used);
            sound(player, SoundEvents.FLINTANDSTEEL_USE, 1.0F, 0.8F);
            return Result.APPLIED;
        }
        return Result.NONE;
    }

    private static void damageFlint(ServerPlayer player, ItemStack flint) {
        if (!player.getAbilities().instabuild) {
            flint.hurtAndBreak(1, player, p -> {
            });
        }
    }

    private static void sound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
