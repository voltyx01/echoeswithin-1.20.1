package com.voltyx.efw.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voltyx.efw.tech.client.BodycamFeed;
import com.voltyx.efw.tech.client.BodycamPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the bodycam picture the items in the carrier's hands (seen from inside its chest) can be left out (BodycamPose.hideHeld). */
@Mixin(ItemInHandLayer.class)
public abstract class BodycamCarrierItemsMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void efw$hideCarrierItems(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float limbSwing,
                                      float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch, CallbackInfo ci) {
        if (BodycamFeed.isRendering() && entity == BodycamFeed.getCurrentCarrier() && BodycamPose.hideHeld()) {
            ci.cancel();
        }
    }
}
