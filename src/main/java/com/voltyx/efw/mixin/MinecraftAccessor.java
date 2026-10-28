package com.voltyx.efw.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Swaps the main render target and the camera entity while the bodycam feed renders the world. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Mutable
    @Accessor("mainRenderTarget")
    void efw$setMainRenderTarget(RenderTarget target);

    @Accessor("cameraEntity")
    void efw$setCameraEntityRaw(Entity entity);
}
