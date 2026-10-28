package com.voltyx.efw.mixin;

import com.voltyx.efw.tech.ChargingSlots;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The window of the electricity generator gets the two charging slots after all its own slots (so the slot numbers of the
 * generator and the player inventory stay as they are). On the client the menu is built with a dummy container, which gets a
 * dummy charging container too.
 */
@Pseudo
@Mixin(targets = "com.mrcrayfish.furniture.refurbished.inventory.ElectricityGeneratorMenu", remap = false)
public abstract class GeneratorMenuMixin {
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/Container;Lnet/minecraft/world/inventory/ContainerData;)V",
            at = @At("TAIL"), remap = false)
    private void efw$chargeSlots(int id, Inventory inventory, Container container, ContainerData data, CallbackInfo ci) {
        SimpleContainer slots = container instanceof ChargingSlots.Holder holder ? holder.efw$chargeSlots() : new SimpleContainer(ChargingSlots.COUNT);
        for (int i = 0; i < ChargingSlots.COUNT; i++) {
            ((MenuAddSlotInvoker) this).efw$addSlot(new ChargingSlots.ChargeSlot(slots, i, ChargingSlots.X + i * ChargingSlots.STEP, ChargingSlots.Y));
        }
    }
}
