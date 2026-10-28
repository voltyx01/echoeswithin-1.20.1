package com.voltyx.efw.mixin;

import com.voltyx.efw.compat.GunOffhandLight;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sodium Dynamic Lights: the off hand item does not light while a TACZ gun is in the main hand (see {@link GunOffhandLight}). */
@Pseudo
@Mixin(targets = "toni.sodiumdynamiclights.SodiumDynamicLights", remap = false)
public abstract class GunOffhandLightMixin {
    @Inject(method = "getLivingEntityLuminanceFromItems", at = @At("HEAD"), remap = false, require = 0)
    private static void efw$hideOffhand(LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        GunOffhandLight.begin(entity);
    }

    @Inject(method = "getLivingEntityLuminanceFromItems", at = @At("RETURN"), remap = false, require = 0)
    private static void efw$done(LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        GunOffhandLight.end();
    }
}
