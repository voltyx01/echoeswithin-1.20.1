package com.voltyx.efw.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.voltyx.efw.compat.client.RagdollBodies;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ragdollified draws a dead player (the falling ragdoll and the corpse of ragdollifiedpc) from the skin of the living owner and
 * outside of the player renderer: the blood was missing, the doll on the belt too, and the track jacket followed whatever the
 * owner wore now. The body is drawn here with the look it died with (RagdollBodies).
 */
@Pseudo
@Mixin(targets = "com.raiiiden.ragdollified.client.ClientRagdollRenderer", remap = false)
public abstract class RagdollBodyMixin {
    // the same two signatures as in RagdollBreastsMixin: up to 1.1.x, and 1.2.0 with one more texture after the slim flag
    private static final String BODY_OLD = "renderPlayerBody(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ID"
            + "Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/raiiiden/ragdollified/RagdollTransform;"
            + "Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/raiiiden/ragdollified/RagdollTransform;"
            + "Lnet/minecraft/resources/ResourceLocation;ZLnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/player/AbstractClientPlayer;F"
            + "Ljava/lang/Object;Ljava/util/List;)V";
    private static final String BODY_NEW = "renderPlayerBody(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ID"
            + "Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/raiiiden/ragdollified/RagdollTransform;"
            + "Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/raiiiden/ragdollified/RagdollTransform;"
            + "Lnet/minecraft/resources/ResourceLocation;ZLnet/minecraft/resources/ResourceLocation;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/client/player/AbstractClientPlayer;FLjava/lang/Object;Ljava/util/List;)V";

    @Inject(method = BODY_OLD, at = @At("HEAD"), remap = false, require = 0)
    private static void efw$bodyStart(PoseStack pose, MultiBufferSource buffers, int light, double scale, @Coerce Object head, @Coerce Object torso,
            @Coerce Object rightArm, @Coerce Object leftArm, @Coerce Object rightLeg, @Coerce Object leftLeg, ResourceLocation skin, boolean slim,
            ItemStack helmet, ItemStack chest, ItemStack legs, ItemStack boots, AbstractClientPlayer player, float alpha, Object key,
            List<?> curios, CallbackInfo ci) {
        RagdollBodies.begin(skin, slim, chest, buffers, light, key, curios);
    }

    @Inject(method = BODY_NEW, at = @At("HEAD"), remap = false, require = 0)
    private static void efw$bodyStartNew(PoseStack pose, MultiBufferSource buffers, int light, double scale, @Coerce Object head, @Coerce Object torso,
            @Coerce Object rightArm, @Coerce Object leftArm, @Coerce Object rightLeg, @Coerce Object leftLeg, ResourceLocation skin, boolean slim,
            ResourceLocation cape, ItemStack helmet, ItemStack chest, ItemStack legs, ItemStack boots, AbstractClientPlayer player, float alpha,
            Object key, List<?> curios, CallbackInfo ci) {
        RagdollBodies.begin(skin, slim, chest, buffers, light, key, curios);
    }

    @Inject(method = {BODY_OLD, BODY_NEW}, at = @At("RETURN"), remap = false, require = 0)
    private static void efw$bodyEnd(CallbackInfo ci) {
        RagdollBodies.end();
    }

    @Inject(method = "renderHumanoidPartPhysics(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/model/geom/ModelPart;Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/raiiiden/ragdollified/RagdollTransform;ILcom/raiiiden/ragdollified/RagdollPart;Lcom/raiiiden/ragdollified/client/ClientRagdollRenderer$HumanoidScale;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
                    remap = true), remap = false, require = 0)
    private static void efw$torso(PoseStack pose, VertexConsumer buffer, ModelPart part, @Coerce Object transform, @Coerce Object reference, int light,
            @Coerce Object ragdollPart, @Coerce Object humanoidScale, CallbackInfo ci) {
        if (RagdollBodies.isDrawing() && "TORSO".equals(String.valueOf(ragdollPart))) {
            RagdollBodies.torso(pose, part);
        }
    }
}
