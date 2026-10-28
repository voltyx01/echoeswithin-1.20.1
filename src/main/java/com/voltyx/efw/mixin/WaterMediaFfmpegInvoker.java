package com.voltyx.efw.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "org.watermedia.api.media.MediaAPI", remap = false)
public interface WaterMediaFfmpegInvoker {
    @Invoker(value = "startFFmpeg", remap = false)
    static boolean efw$startFFmpeg() {
        throw new AssertionError();
    }
}
