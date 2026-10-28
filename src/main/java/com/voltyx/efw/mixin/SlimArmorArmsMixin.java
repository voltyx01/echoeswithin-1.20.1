package com.voltyx.efw.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slim skins have 3 pixel arms, the vanilla armour model has 4: for the armour that uses the vanilla model the arm parts are made
 * 3/4 as wide for the draw (the 1.12 MixinModelRendererSlim). The layer copies the pose of the player model onto the armour model
 * right before the parts are set visible (that copy resets the scale), so the change is made there; the next copy resets it.
 * Armour with its own model (GeckoLib, custom) is left alone.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class SlimArmorArmsMixin {
    @Unique
    private LivingEntity efw$entity;

    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;)V",
            at = @At("HEAD"))
    private void efw$remember(PoseStack pose, MultiBufferSource buffers, LivingEntity entity, EquipmentSlot slot, int light,
                              HumanoidModel<?> model, CallbackInfo ci) {
        efw$entity = entity;
    }

    @Inject(method = "setPartVisibility(Lnet/minecraft/client/model/HumanoidModel;Lnet/minecraft/world/entity/EquipmentSlot;)V", at = @At("HEAD"))
    private void efw$slim(HumanoidModel<?> model, EquipmentSlot slot, CallbackInfo ci) {
        if (efw$entity instanceof AbstractClientPlayer player && "slim".equals(player.getModelName())) {
            String cls = model.getClass().getName();
            if (!cls.contains("bernie") && !cls.contains("geckolib") && !cls.contains("azurelib")) {
                model.rightArm.xScale = 0.75F;
                model.rightArm.x += 0.25F;
                model.leftArm.xScale = 0.75F;
                model.leftArm.x -= 0.25F;
            }
        }
    }
}
