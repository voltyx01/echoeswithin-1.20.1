package com.voltyx.efw.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voltyx.efw.tech.client.BodycamFeed;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Like MixinLayerArmorBase of 1.12: in the bodycam picture the carrier shows only its chest armour. */
@Mixin(HumanoidArmorLayer.class)
public abstract class BodycamCarrierArmorMixin {
    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void efw$carrierArmor(PoseStack pose, MultiBufferSource buffers, LivingEntity entity, EquipmentSlot slot, int light,
                                  HumanoidModel<?> model, CallbackInfo ci) {
        if (slot != EquipmentSlot.CHEST && BodycamFeed.isRendering() && entity == BodycamFeed.getCurrentCarrier()) {
            ci.cancel();
        }
    }
}
