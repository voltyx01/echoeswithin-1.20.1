package com.voltyx.efw.mixin;

import com.voltyx.efw.headshot.LsoCompat;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Legendary Survival Overhaul: tells the body part lookup which damage is being handled, and skips LSO's own headshot
 * multiplier on hits the efw head boxes already judged.
 */
@Pseudo
@Mixin(targets = "sfiomn.legendarysurvivaloverhaul.common.events.CommonForgeEvents", remap = false)
public abstract class LsoHurtEventsMixin {
    @Inject(method = "onEntityHurtDamage", at = @At("HEAD"), remap = false, require = 0)
    private static void efw$begin(LivingDamageEvent event, CallbackInfo ci) {
        LsoCompat.begin(event.getSource());
    }

    @Inject(method = "onEntityHurtDamage", at = @At("RETURN"), remap = false, require = 0)
    private static void efw$end(LivingDamageEvent event, CallbackInfo ci) {
        LsoCompat.end();
    }

    @Redirect(method = "onEntityHurtDamage", at = @At(value = "FIELD",
            target = "Lsfiomn/legendarysurvivaloverhaul/config/Config$Baked;headCriticalShotMultiplier:D"), remap = false, require = 0)
    private static double efw$headMultiplier() {
        return LsoCompat.headCriticalShotMultiplier();
    }
}
