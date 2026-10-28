package com.voltyx.efw.mixin;

import com.voltyx.efw.client.Shiver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The limbs and the head of a shivering player tremble a little, each on its own (third person, {@link Shiver}). */
@Mixin(HumanoidModel.class)
public abstract class ShiverModelMixin {
    @Shadow
    @Final
    public ModelPart head;
    @Shadow
    @Final
    public ModelPart rightArm;
    @Shadow
    @Final
    public ModelPart leftArm;
    @Shadow
    @Final
    public ModelPart rightLeg;
    @Shadow
    @Final
    public ModelPart leftLeg;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void efw$tremble(LivingEntity entity, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player)) {
            return;
        }
        float a = Shiver.amount(entity);
        if (a <= 0.001f) {
            return;
        }
        float pt = Minecraft.getInstance().getFrameTime();
        rightArm.xRot += Shiver.wave(entity, pt, 1.0f) * 0.07f * a;
        rightArm.zRot += Shiver.wave(entity, pt, 2.0f) * 0.05f * a;
        leftArm.xRot += Shiver.wave(entity, pt, 3.0f) * 0.07f * a;
        leftArm.zRot += Shiver.wave(entity, pt, 5.0f) * 0.05f * a;
        rightLeg.xRot += Shiver.wave(entity, pt, 6.0f) * 0.035f * a;
        leftLeg.xRot += Shiver.wave(entity, pt, 7.0f) * 0.035f * a;
        head.xRot += Shiver.wave(entity, pt, 8.0f) * 0.03f * a;
        head.yRot += Shiver.wave(entity, pt, 9.0f) * 0.03f * a;
    }
}
