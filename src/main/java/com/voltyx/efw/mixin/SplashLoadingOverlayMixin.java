package com.voltyx.efw.mixin;

import com.voltyx.efw.client.loading.EfwSplash;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Later resource reloads (resource packs, F3+T) show the efw splash instead of the Mojang screen, with the vanilla fades. */
@Mixin(LoadingOverlay.class)
public abstract class SplashLoadingOverlayMixin {
    @Shadow
    private long fadeOutStart;
    @Shadow
    private long fadeInStart;
    @Shadow
    @Final
    private boolean fadeIn;

    @Inject(method = "render", at = @At("TAIL"))
    private void efw$splash(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        long now = Util.getMillis();
        float out = fadeOutStart > -1L ? (now - fadeOutStart) / 1000.0f : -1.0f;
        float in = fadeInStart > -1L ? (now - fadeInStart) / 500.0f : -1.0f;
        float alpha;
        if (out >= 1.0f) {
            alpha = 1.0f - Math.max(0.0f, Math.min(1.0f, out - 1.0f));
        } else if (fadeIn) {
            alpha = Math.max(0.0f, Math.min(1.0f, in));
        } else {
            alpha = 1.0f;
        }
        EfwSplash.renderOverlay(g, alpha);
    }
}
