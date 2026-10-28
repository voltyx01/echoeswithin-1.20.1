package com.voltyx.efw.mixin;

import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sets the camera type field without Options.setCameraType, which Shoulder Surfing turns into a perspective change. */
@Mixin(Options.class)
public interface OptionsAccessor {
    @Accessor("cameraType")
    void efw$setCameraTypeRaw(CameraType type);
}
