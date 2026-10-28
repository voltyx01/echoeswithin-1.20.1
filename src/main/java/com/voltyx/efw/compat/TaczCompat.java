package com.voltyx.efw.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

/** TACZ is optional: everything that touches its classes goes through here after a loaded check. */
public final class TaczCompat {
    private static final boolean LOADED = ModList.get().isLoaded("tacz");

    private TaczCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static boolean isReloading(LivingEntity entity) {
        return LOADED && Impl.isReloading(entity);
    }

    public static boolean isAiming(LivingEntity entity) {
        return LOADED && Impl.isAiming(entity);
    }

    public static boolean isHoldingGun(LivingEntity entity) {
        return LOADED && Impl.isHoldingGun(entity);
    }

    /** First person: the item TACZ still draws in the main hand (a gun stays a few ticks while it is put away). Client only. */
    public static boolean keepsGunInHand() {
        return LOADED && Impl.keepsGunInHand();
    }

    /** The gun type of the pack data (TACZ: pistol, rifle, shotgun, smg, ...) is "shotgun". */
    public static boolean isShotgun(net.minecraft.world.item.ItemStack stack) {
        return LOADED && Impl.isShotgun(stack);
    }

    /** The gun type of the pack data (pistol, rifle, shotgun, smg, sniper, mg, rpg ...), null when not a TACZ gun. */
    public static String gunType(net.minecraft.world.item.ItemStack stack) {
        return LOADED ? Impl.gunType(stack) : null;
    }

    /** The stack is a TACZ gun. */
    public static boolean isGun(net.minecraft.world.item.ItemStack stack) {
        return LOADED && Impl.isGun(stack);
    }

    /** Rounds in the magazine of the gun, -1 when the stack is not a TACZ gun. */
    public static int currentAmmo(net.minecraft.world.item.ItemStack stack) {
        return LOADED ? Impl.currentAmmo(stack) : -1;
    }

    /**
     * The gun has a shot in it: rounds in the magazine, one in the barrel (a single round is chambered and the magazine reads 0), or
     * it fires straight from the inventory and there is ammo there.
     */
    public static boolean canFire(LivingEntity holder, net.minecraft.world.item.ItemStack stack) {
        return LOADED && Impl.canFire(holder, stack);
    }

    /** The gun does not allow attachments to be changed. */
    public static boolean hasAttachmentLock(net.minecraft.world.item.ItemStack stack) {
        return LOADED && Impl.hasAttachmentLock(stack);
    }

    /** The EFW modification screen for the gun in the given inventory slot. */
    public static net.minecraft.client.gui.screens.Screen newRefitScreen(int gunSlot) {
        return Impl.newRefitScreen(gunSlot);
    }

    /** The TACZ modification screen or the EFW one built on it. */
    public static boolean isRefitScreen(net.minecraft.client.gui.screens.Screen screen) {
        return LOADED && screen != null && Impl.isRefitScreen(screen);
    }

    /** Server: next fire mode of the gun in the inventory slot of the player. */
    public static void cycleFireMode(net.minecraft.server.level.ServerPlayer player, int slot) {
        if (LOADED) {
            Impl.cycleFireMode(player, slot);
        }
    }

    private static final class Impl {
        static boolean isGun(net.minecraft.world.item.ItemStack stack) {
            return com.tacz.guns.api.item.IGun.getIGunOrNull(stack) != null;
        }

        static int currentAmmo(net.minecraft.world.item.ItemStack stack) {
            try {
                com.tacz.guns.api.item.IGun gun = com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
                return gun == null ? -1 : gun.getCurrentAmmoCount(stack);
            } catch (Throwable t) {
                return -1;
            }
        }

        static boolean canFire(LivingEntity holder, net.minecraft.world.item.ItemStack stack) {
            try {
                com.tacz.guns.api.item.IGun gun = com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
                if (gun == null) {
                    return false;
                }
                if (gun.getCurrentAmmoCount(stack) > 0 || gun.hasBulletInBarrel(stack)) {
                    return true;
                }
                return gun.useInventoryAmmo(stack) && gun.hasInventoryAmmo(holder, stack, false);
            } catch (Throwable t) {
                return false;
            }
        }

        static boolean hasAttachmentLock(net.minecraft.world.item.ItemStack stack) {
            com.tacz.guns.api.item.IGun gun = com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
            return gun != null && gun.hasAttachmentLock(stack);
        }

        static net.minecraft.client.gui.screens.Screen newRefitScreen(int gunSlot) {
            return new com.voltyx.efw.tacz.client.EfwRefitScreen(gunSlot);
        }

        static boolean isRefitScreen(net.minecraft.client.gui.screens.Screen screen) {
            return screen instanceof com.tacz.guns.client.gui.GunRefitScreen;
        }

        static void cycleFireMode(net.minecraft.server.level.ServerPlayer player, int slot) {
            if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
                return;
            }
            net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(slot);
            com.tacz.guns.api.item.IGun gun = com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
            if (gun == null) {
                return;
            }
            java.util.List<com.tacz.guns.api.item.gun.FireMode> modes = com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gun.getGunId(stack))
                    .map(i -> i.getGunData().getFireModeSet()).orElse(java.util.List.of());
            if (modes.size() < 2) {
                return;
            }
            int next = (modes.indexOf(gun.getFireMode(stack)) + 1) % modes.size();
            gun.setFireMode(stack, modes.get(next));
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
        }

        static String gunType(net.minecraft.world.item.ItemStack stack) {
            try {
                com.tacz.guns.api.item.IGun gun = com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
                if (gun == null) {
                    return null;
                }
                return com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(index -> index.getType()).orElse(null);
            } catch (Throwable t) {
                return null;
            }
        }

        static boolean isShotgun(net.minecraft.world.item.ItemStack stack) {
            try {
                com.tacz.guns.api.item.IGun gun = com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
                if (gun == null) {
                    return false;
                }
                return com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gun.getGunId(stack))
                        .map(index -> "shotgun".equals(index.getType())).orElse(false);
            } catch (Throwable t) {
                return false;
            }
        }

        static boolean isReloading(LivingEntity entity) {
            try {
                return com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(entity).getSynReloadState().getStateType().isReloading();
            } catch (Throwable t) {
                return false;
            }
        }

        static boolean isAiming(LivingEntity entity) {
            try {
                return com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(entity).getSynIsAiming();
            } catch (Throwable t) {
                return false;
            }
        }

        static boolean keepsGunInHand() {
            try {
                return com.tacz.guns.api.client.other.KeepingItemRenderer.getRenderer().getCurrentItem().getItem()
                        instanceof com.tacz.guns.api.item.IGun;
            } catch (Throwable t) {
                return false;
            }
        }

        static boolean isHoldingGun(LivingEntity entity) {
            try {
                return com.tacz.guns.api.item.IGun.getIGunOrNull(entity.getMainHandItem()) != null;
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
