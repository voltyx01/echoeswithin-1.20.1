package com.voltyx.efw.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No item name above the hotbar when the selected slot changes. */
@Mixin(Gui.class)
public abstract class HotbarItemNameMixin {
    @Inject(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true)
    private void efw$noItemName(GuiGraphics graphics, CallbackInfo ci) {
        ci.cancel();
    }
}
