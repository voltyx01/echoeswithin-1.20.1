package com.voltyx.efw.mixin;

import dev.lambdaurora.lambdynlights.api.DynamicLightHandlers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import toni.sodiumdynamiclights.SodiumDynamicLights;

/**
 * Sodium Dynamic Lights: a burning entity is a light source of 15, like a torch, lighting up both its surroundings and itself (its
 * own mixin on EntityRenderer.getBlockLightLevel reads it directly). The fire gives no light here: the luminance it reports while
 * burning is only what it holds or what it is. Changed in the getter Sodium Dynamic Lights adds to the entities, so every place that
 * reads it agrees; applied after that mod (priority), the getter does not exist before.
 */
@Mixin(value = {Entity.class, LivingEntity.class, Player.class}, priority = 2000)
public abstract class DynamicLightsFireMixin {
    @Inject(method = "sdl$getLuminance", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void efw$noFireLight(CallbackInfoReturnable<Integer> cir) {
        Entity entity = (Entity) (Object) this;
        int luminance = cir.getReturnValueI();
        if (luminance <= 0 || !entity.isOnFire()) {
            return;
        }
        int own = DynamicLightHandlers.getLuminanceFrom(entity);
        if (entity instanceof LivingEntity living) {
            own = Math.max(own, SodiumDynamicLights.getLivingEntityLuminanceFromItems(living));
        }
        if (own < luminance) {
            cir.setReturnValue(own);
        }
    }
}
