package com.voltyx.efw.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.voltyx.efw.client.Shiver;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Players: the vanilla frozen shake turns the body by cos(tick * 3.25) * 72 degrees, a new extreme every tick without anything in
 * between, which looks like a bug. It is replaced by a small smooth sway of the body ({@link Shiver}).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class ShiverRendererMixin {
    @Inject(method = "isShaking", at = @At("HEAD"), cancellable = true)
    private void efw$noVanillaShake(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void efw$sway(LivingEntity entity, PoseStack pose, float ageInTicks, float bodyYaw, float partialTick, CallbackInfo ci) {
        if (!(entity instanceof Player)) {
            return;
        }
        float a = Shiver.amount(entity);
        if (a > 0.001f) {
            pose.mulPose(Axis.YP.rotationDegrees(Shiver.wave(entity, partialTick, 0.0f) * 2.5f * a));
            pose.mulPose(Axis.ZP.rotationDegrees(Shiver.wave(entity, partialTick, 4.1f) * 1.2f * a));
        }
    }
}
