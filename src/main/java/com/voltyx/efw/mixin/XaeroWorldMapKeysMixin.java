package com.voltyx.efw.mixin;

import com.voltyx.efw.tech.client.XaeroGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The world map handles its key events only with a charged portable map. */
@Pseudo
@Mixin(targets = "xaero.map.controls.ControlsHandler", remap = false)
public abstract class XaeroWorldMapKeysMixin {
    @Inject(method = "handleKeyEvents", at = @At("HEAD"), cancellable = true, remap = false)
    private void efw$noMapNoKeyEvents(CallbackInfo ci) {
        if (!XaeroGate.hasActiveMap()) {
            ci.cancel();
        }
    }
}
