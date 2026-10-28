package com.voltyx.efw.mixin;

import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** No "Enchantment Cost: N" line on the anvil: there are no levels to pay it with (AnvilNoLevelsMixin). */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenNoCostMixin {
    @Redirect(method = "renderLabels", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AnvilMenu;getCost()I"))
    private int efw$noCost(AnvilMenu menu) {
        return 0;
    }
}
