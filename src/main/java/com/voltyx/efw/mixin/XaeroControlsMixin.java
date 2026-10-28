package com.voltyx.efw.mixin;

import com.voltyx.efw.tech.client.XaeroGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Key presses of the minimap and the world map are ignored without a charged portable map. */
@Pseudo
@Mixin(targets = {"xaero.common.controls.ControlsHandler", "xaero.map.controls.ControlsHandler"}, remap = false)
public abstract class XaeroControlsMixin {
    @Inject(method = "keyDown", at = @At("HEAD"), cancellable = true, remap = false)
    private void efw$noMapNoKeyDown(CallbackInfo ci) {
        if (!XaeroGate.hasActiveMap()) {
            ci.cancel();
        }
    }
}
