package com.voltyx.efw.mixin;

import com.voltyx.efw.client.HurtFlash;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers a real hit of the local player (the damage event and the hurt animation from the server), see HurtFlash. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtSeenMixin {
    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void efw$damageEvent(DamageSource source, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer) {
            HurtFlash.hit();
        }
    }

    @Inject(method = "animateHurt", at = @At("HEAD"))
    private void efw$hurtAnimation(float yaw, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer) {
            HurtFlash.hit();
        }
    }
}
