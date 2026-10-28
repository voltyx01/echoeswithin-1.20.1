package com.voltyx.efw.mixin;

import com.voltyx.efw.compat.SsrTransition;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shoulder Surfing: the target offset of the pull-back, and the player fade of it. */
@Pseudo
@Mixin(targets = "com.github.exopandora.shouldersurfing.client.EventHooks", remap = false)
public abstract class SsrEventHooksMixin {
    @Inject(method = "getTargetOffset(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/Camera;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/BlockGetter;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void efw$exitOffset(Vec3 defaultOffset, Camera camera, Entity cameraEntity, BlockGetter level, CallbackInfoReturnable<Vec3> cir) {
        if (SsrTransition.isExiting()) {
            cir.setReturnValue(SsrTransition.exitOffset());
        }
    }

    @Inject(method = "getCameraEntityAlpha(Lnet/minecraft/world/entity/Entity;F)F", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void efw$exitAlpha(Entity entity, float partialTick, CallbackInfoReturnable<Float> cir) {
        if (SsrTransition.isGliding()) {
            cir.setReturnValue(SsrTransition.glideAlpha());
        }
    }
}
