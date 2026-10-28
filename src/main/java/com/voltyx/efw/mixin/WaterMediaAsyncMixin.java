package com.voltyx.efw.mixin;

import com.voltyx.efw.client.FfmpegAsync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * WaterMedia starts FFmpeg inside its mod constructor, and loading the JavaCPP natives takes 22-26 s here (it searches every mod
 * jar for each library variant). Mod construction cannot finish before it, so the whole start waited for it. The start now runs
 * on its own thread while the game keeps loading; everything that needs FFmpeg waits for that thread first (by the time
 * the title screen asks for its video the natives are normally there already).
 */
@Pseudo
@Mixin(targets = "org.watermedia.api.media.MediaAPI", remap = false)
public abstract class WaterMediaAsyncMixin {
    @Inject(method = "startFFmpeg", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void efw$startInBackground(CallbackInfoReturnable<Boolean> cir) {
        if (FfmpegAsync.launch(WaterMediaFfmpegInvoker::efw$startFFmpeg)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = {"createPlayer*", "ffmpegLoaded", "ffmpegError", "vulkanDecode"}, at = @At("HEAD"), remap = false, require = 0)
    private static void efw$awaitFfmpeg(CallbackInfoReturnable<?> cir) {
        FfmpegAsync.await();
    }
}
