package com.voltyx.efw.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voltyx.efw.client.BodyGuns;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The guns TACZ draws on the body (off hand, hotbar) are drawn by {@link BodyGuns}: one pistol and one other gun, carried by the torso. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.renderer.other.HumanoidOffhandRender", remap = false)
public abstract class TaczBodyGunsMixin {
    @Inject(method = "renderGun", at = @At("HEAD"), cancellable = true, remap = false)
    private static void efw$bodyGuns(LivingEntity entity, PoseStack pose, MultiBufferSource buffer, int light, CallbackInfo ci) {
        ci.cancel();
        BodyGuns.render(entity, pose, buffer, light);
    }
}
