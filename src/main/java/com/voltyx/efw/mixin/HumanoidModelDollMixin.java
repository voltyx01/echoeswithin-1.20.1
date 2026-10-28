package com.voltyx.efw.mixin;

import com.voltyx.efw.doll.client.DollClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third person pose with Saya's doll in the hands: both arms rise and come together in front of the chest (the 1.12
 * MixinModelBiped), blended with the pose the arms had by the progress of the doll.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelDollMixin {
    @Shadow
    @org.spongepowered.asm.mixin.Final
    public ModelPart rightArm;
    @Shadow
    @org.spongepowered.asm.mixin.Final
    public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void efw$dollPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) {
            return;
        }
        float progress = DollClient.holdProgress(player, Minecraft.getInstance().getFrameTime());
        if (progress <= 0.001F) {
            return;
        }
        // arms up by 70 degrees and in towards the chest
        rightArm.xRot += (-1.2217F - rightArm.xRot) * progress;
        rightArm.yRot += (-0.42F - rightArm.yRot) * progress;
        rightArm.zRot += (0.10F - rightArm.zRot) * progress;
        leftArm.xRot += (-1.2217F - leftArm.xRot) * progress;
        leftArm.yRot += (0.42F - leftArm.yRot) * progress;
        leftArm.zRot += (-0.10F - leftArm.zRot) * progress;
    }
}
