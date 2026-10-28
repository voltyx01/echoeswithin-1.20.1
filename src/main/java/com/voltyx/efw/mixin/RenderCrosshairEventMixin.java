package com.voltyx.efw.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.client.event.RenderCrosshairEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RenderCrosshairEvent.class, remap = false)
public abstract class RenderCrosshairEventMixin {

    @Shadow
    private static long killTimestamp;

    /**
     * Redirects RenderSystem.setShaderColor inside RenderCrosshairEvent.renderHitMarker.
     * If an entity was killed (within 300 ms), hitmarker is RED (1.0F, 0.0F, 0.0F, alpha).
     * In all other cases (normal hit, headshot without kill), hitmarker is WHITE (1.0F, 1.0F, 1.0F, alpha).
     */
    @Redirect(
            method = "renderHitMarker",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V"
            ),
            remap = false
    )
    private static void efw$redirectShaderColor(float red, float green, float blue, float alpha) {
        long dtKill = System.currentTimeMillis() - killTimestamp;
        if (dtKill <= 300L) {
            // Kill -> RED hitmarker
            RenderSystem.setShaderColor(1.0F, 0.0F, 0.0F, alpha);
        } else {
            // All other hits -> WHITE hitmarker
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        }
    }
}
