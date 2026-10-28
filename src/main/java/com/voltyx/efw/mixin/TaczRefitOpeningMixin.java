package com.voltyx.efw.mixin;

import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import com.tacz.guns.util.math.SecondOrderDynamics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The gun flickered for about a second each time the modification screen opened. The opening progress goes through a second order
 * dynamics that overshoots and undershoots, and the gun transform lerps its view matrices with that value, so values below 0 or above
 * 1 throw the matrices around. The progress is kept inside 0..1 (the aiming dynamics, the next call in the method, is not touched).
 */
@Mixin(value = FirstPersonRenderGunEvent.class, remap = false)
public abstract class TaczRefitOpeningMixin {
    @Redirect(method = "applyFirstPersonGunTransform", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/util/math/SecondOrderDynamics;update(F)F", ordinal = 0), remap = false)
    private static float efw$clampOpening(SecondOrderDynamics dynamics, float target) {
        return Math.max(0.0f, Math.min(1.0f, dynamics.update(target)));
    }
}
