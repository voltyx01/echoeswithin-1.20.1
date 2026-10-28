package com.voltyx.efw.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.voltyx.efw.compat.client.ChannelVolumeAccess;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keeps the volume each sound asked for, so it can be played at a part of it (the ambience silenced in a concussion, ConcussionVolume). */
@Mixin(Channel.class)
public abstract class ChannelVolumeMixin implements ChannelVolumeAccess {
    @Shadow
    @Final
    private int source;

    @Unique
    private float efw$volume = 1.0F;
    @Unique
    private float efw$factor = 1.0F;

    @ModifyArg(method = "setVolume", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL10;alSourcef(IIF)V", remap = false), index = 2)
    private float efw$volume(float volume) {
        efw$volume = volume;
        return volume * efw$factor;
    }

    @Override
    public void efw$setVolumeFactor(float factor) {
        if (factor == efw$factor) {
            return;
        }
        efw$factor = factor;
        try {
            if (AL10.alIsSource(source)) {
                AL10.alSourcef(source, AL10.AL_GAIN, efw$volume * factor);
            }
        } catch (RuntimeException ignored) {
        }
    }
}
