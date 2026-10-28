package com.voltyx.efw.mixin;

import com.voltyx.efw.client.HandFade;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The first person hand pass goes through {@link HandFade}, so the hands and what they hold can fade in and out. The pass is
 * bracketed by the light layer calls around it, not by redirecting the hand call itself: Oculus redirects that call already.
 */
@Mixin(GameRenderer.class)
public abstract class HandFadeMixin {
    @Inject(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LightTexture;turnOnLightLayer()V",
            shift = At.Shift.AFTER))
    private void efw$beginHands(CallbackInfo ci) {
        HandFade.begin();
    }

    @Inject(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LightTexture;turnOffLightLayer()V"))
    private void efw$endHands(CallbackInfo ci) {
        HandFade.end();
    }
}
