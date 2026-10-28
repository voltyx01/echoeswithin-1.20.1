package com.voltyx.efw.mixin;

import com.voltyx.efw.compat.TaczCompat;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * With a TACZ gun in the main hand the off hand item is put away like any item that leaves the hand: for the first person renderer
 * the off hand is empty then, so the item goes down with the vanilla animation (and comes up again when the gun is put away),
 * instead of TACZ hiding it at once. {@link com.voltyx.efw.client.GunOffhandLowering} lets it be drawn while it goes down.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandGunOffhandMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack efw$offhandPutAway(LocalPlayer player) {
        ItemStack off = player.getOffhandItem();
        return TaczCompat.isLoaded() && TaczCompat.isGun(player.getMainHandItem()) ? ItemStack.EMPTY : off;
    }
}
