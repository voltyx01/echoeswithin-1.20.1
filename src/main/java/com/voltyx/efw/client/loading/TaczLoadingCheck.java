package com.voltyx.efw.client.loading;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.ItemStack;

/** Only loaded when TACZ is present (see {@link LoadingCard}). */
final class TaczLoadingCheck {
    private TaczLoadingCheck() {
    }

    /**
     * TACZ fills its client index from the server's gun packs only when the player joins, so during world creation and
     * loading a gun has neither its model nor its name yet (missing texture, raw id).
     */
    static boolean ready(ItemStack stack) {
        if (stack.getItem() instanceof IGun gun) {
            return TimelessAPI.getClientGunIndex(gun.getGunId(stack)).isPresent();
        }
        if (stack.getItem() instanceof IAttachment attachment) {
            return TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(stack)).isPresent();
        }
        if (stack.getItem() instanceof IAmmo ammo) {
            return TimelessAPI.getClientAmmoIndex(ammo.getAmmoId(stack)).isPresent();
        }
        return true;
    }
}
