package com.voltyx.efw.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The green night vision screen of Survival Instinct is drawn all the time the helmet is worn; efw draws its own one only while it is switched on. */
@Pseudo
@Mixin(targets = "net.mcreator.survivalinstinct.client.screens.GreenNightVisionGUIOverlay", remap = false)
public abstract class SiNvgOverlayMixin {
    @Inject(method = "eventHandler", at = @At("HEAD"), cancellable = true, remap = false)
    private static void efw$noSiOverlay(CallbackInfo ci) {
        ci.cancel();
    }
}
