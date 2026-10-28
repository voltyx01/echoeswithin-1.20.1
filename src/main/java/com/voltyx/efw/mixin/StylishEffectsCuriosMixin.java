package com.voltyx.efw.mixin;

import com.voltyx.efw.client.InventoryMirror;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stylish Effects draws its own effect widgets on every container screen, whatever vanilla does, so the cancelled vanilla list
 * is not enough: on the expanded Curios screen it must not see the screen as one that shows effects.
 */
@Pseudo
@Mixin(targets = "fuzs.stylisheffects.client.handler.EffectScreenHandlerImpl", remap = false)
public abstract class StylishEffectsCuriosMixin {
    @Inject(method = "supportsEffectsDisplay(Lnet/minecraft/client/gui/screens/Screen;)Z", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void efw$noEffectsOnCurios(Screen screen, CallbackInfoReturnable<Boolean> cir) {
        if (InventoryMirror.isCuriosScreen(screen)) {
            cir.setReturnValue(false);
        }
    }
}
