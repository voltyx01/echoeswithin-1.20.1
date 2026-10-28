package com.voltyx.efw.mixin;

import com.github.exopandora.shouldersurfing.api.client.Perspective;
import com.voltyx.efw.compat.SsrTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shoulder Surfing: leaving for first person becomes a smooth pull-back of the camera (SsrTransition). */
@Pseudo
@Mixin(targets = "com.github.exopandora.shouldersurfing.client.ShoulderSurfing", remap = false)
public abstract class SsrChangePerspectiveMixin {
    @Inject(method = "changePerspective(Lcom/github/exopandora/shouldersurfing/api/client/Perspective;Z)V", at = @At("HEAD"),
            cancellable = true, remap = false, require = 0)
    private void efw$smoothExit(Perspective perspective, boolean lookAtCrosshairTarget, CallbackInfo ci) {
        if (SsrTransition.interceptChange(perspective, lookAtCrosshairTarget)) {
            ci.cancel();
        }
    }
}
