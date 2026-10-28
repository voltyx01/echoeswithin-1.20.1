package com.voltyx.efw.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.voltyx.efw.client.dark.DarkGui;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Dark theme: every GUI texture bound through the shader texture slots passes through DarkGui. */
@Mixin(value = RenderSystem.class, remap = false)
public abstract class RenderSystemDarkMixin {
    @ModifyVariable(method = "setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V", at = @At("HEAD"), argsOnly = true)
    private static ResourceLocation efw$dark(ResourceLocation location) {
        return DarkGui.replace(location);
    }
}
