package com.voltyx.efw.mixin;

import com.voltyx.efw.compat.SsrTransition;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shoulder Surfing: the player fades out during the pull-back whatever the player transparency setting, and vanishes at the end. */
@Pseudo
@Mixin(targets = "com.github.exopandora.shouldersurfing.client.renderer.CameraEntityRenderer", remap = false)
public abstract class SsrCameraEntityRendererMixin {
    @Shadow(remap = false)
    private float cameraEntityAlpha;

    @Inject(method = "preRenderCameraEntity(Lnet/minecraft/world/entity/Entity;F)Z", at = @At("RETURN"), remap = false, require = 0)
    private void efw$fade(Entity entity, float partialTick, CallbackInfoReturnable<Boolean> cir) {
        if (SsrTransition.isGliding() && !cir.getReturnValue()) {
            cameraEntityAlpha = SsrTransition.glideAlpha();
        }
    }

    @Inject(method = "isCameraEntityRenderingSkipped(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void efw$hideAtEnd(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SsrTransition.isGliding() && SsrTransition.glideAlpha() <= 0.01F) {
            cir.setReturnValue(true);
        }
    }
}
