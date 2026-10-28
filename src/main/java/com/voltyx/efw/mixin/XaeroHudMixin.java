package com.voltyx.efw.mixin;

import com.voltyx.efw.tech.client.XaeroGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Xaero hud (the minimap with the radar) is drawn only while a charged portable map is worn. */
@Pseudo
@Mixin(targets = "xaero.hud.render.HudRenderer", remap = false)
public abstract class XaeroHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void efw$noMapNoHud(CallbackInfo ci) {
        if (!XaeroGate.hasActiveMap()) {
            ci.cancel();
        }
    }
}
