package com.voltyx.efw.mixin;

import com.voltyx.efw.client.loading.IntroBlackout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The black between the loading screen and the intro (see {@link IntroBlackout}): after the screen and the overlay, before the toasts. */
@Mixin(GameRenderer.class)
public abstract class IntroBlackoutMixin {
    @Shadow
    @Final
    Minecraft minecraft;

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/toasts/ToastComponent;render(Lnet/minecraft/client/gui/GuiGraphics;)V"),
            require = 0)
    private void efw$introBlackout(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        IntroBlackout.render(minecraft);
    }
}
