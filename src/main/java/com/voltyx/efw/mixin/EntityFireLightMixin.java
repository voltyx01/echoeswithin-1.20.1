package com.voltyx.efw.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A burning entity is drawn in the light around it, not fully lit by its own fire: vanilla gives it block light 15 here. Sodium Dynamic
 * Lights then adds its own luminance on top, which does not count the fire either (DynamicLightsFireMixin).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityFireLightMixin {
    @Redirect(method = "getBlockLightLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isOnFire()Z"))
    private boolean efw$noFireLight(Entity entity) {
        return false;
    }
}
