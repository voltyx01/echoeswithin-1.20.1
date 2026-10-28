package com.voltyx.efw.mixin;

import com.voltyx.efw.tech.client.XaeroGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The key bindings of the Xaero mods do nothing without a charged portable map. */
@Pseudo
@Mixin(targets = "xaero.hud.controls.key.KeyMappingTickHandler", remap = false)
public abstract class XaeroKeyTickMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void efw$noMapNoKeys(CallbackInfo ci) {
        if (!XaeroGate.hasActiveMap()) {
            ci.cancel();
        }
    }
}
