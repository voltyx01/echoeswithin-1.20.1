package com.voltyx.efw.mixin;

import com.voltyx.efw.nvg.NvgHelmets;
import com.voltyx.efw.tech.BatteryHelper;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Items of other mods that efw turns into battery devices (the night vision helmets) cannot override the click themselves: a
 * battery on the cursor is installed with a right click on the helmet in any container, an empty cursor takes it out.
 */
@Mixin(ItemStack.class)
public abstract class PoweredDeviceStackMixin {
    @Inject(method = "overrideOtherStackedOnMe(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/inventory/Slot;Lnet/minecraft/world/inventory/ClickAction;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/SlotAccess;)Z",
            at = @At("HEAD"), cancellable = true)
    private void efw$batteryClick(ItemStack carried, Slot slot, ClickAction action, Player player, SlotAccess access, CallbackInfoReturnable<Boolean> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (NvgHelmets.isNvgHelmet(self) && BatteryHelper.handleClick(self, carried, action, player, access)) {
            cir.setReturnValue(true);
        }
    }
}
