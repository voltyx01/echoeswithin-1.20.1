package com.voltyx.efw.compat.client;

/** Added to Channel by ChannelVolumeMixin. */
public interface ChannelVolumeAccess {
    /** Plays the sound at this part of the volume it asked for (1 = as asked), from now on. */
    void efw$setVolumeFactor(float factor);
}
