package com.voltyx.efw.mixin;

import com.voltyx.efw.headshot.LsoCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Legendary Survival Overhaul: the body part of a hit comes from the efw hit detection when it judged the hit. */
@Pseudo
@Mixin(targets = "sfiomn.legendarysurvivaloverhaul.util.PlayerModelUtil", remap = false)
public abstract class LsoPlayerModelUtilMixin {
    @Inject(method = "getPreciseEntityImpact", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void efw$precise(Entity hitEntity, Player player, CallbackInfoReturnable<List<Object>> cir) {
        List<Object> part = LsoCompat.hitPart();
        if (part != null) {
            cir.setReturnValue(part);
        }
    }

    @Inject(method = "getEntityImpact", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void efw$impact(Entity hitEntity, Player player, CallbackInfoReturnable<List<Object>> cir) {
        List<Object> part = LsoCompat.hitPart();
        if (part != null) {
            cir.setReturnValue(part);
        }
    }
}
