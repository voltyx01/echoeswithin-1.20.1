package com.voltyx.efw.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.voltyx.efw.gender.render.GenderLayer;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
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
 * Ragdollified draws a dead player as loose skin-textured parts, so the breasts of the gender layer were missing on it: they are
 * drawn here together with the ragdoll's torso (its first, skin pass), with the dead player's own settings.
 */
@Pseudo
@Mixin(targets = "com.raiiiden.ragdollified.client.ClientRagdollRenderer", remap = false)
public abstract class RagdollBreastsMixin {
    private static UUID efw$ragdollOwner;
    private static UUID efw$bodyOwner;
    private static ItemStack efw$chest = ItemStack.EMPTY;
    private static boolean efw$torsoDone;
    private static Field efw$uuidField;

    @Inject(method = "renderPlayerRagdoll", at = @At("HEAD"), remap = false, require = 0)
    private static void efw$ragdollStart(@Coerce Object ragdoll, @Coerce Object snapshot, PoseStack pose, MultiBufferSource buffers, int light,
            float partialTick, double scale, CallbackInfo ci) {
        efw$ragdollOwner = efw$owner(ragdoll);
    }

    @Inject(method = "renderPlayerRagdoll", at = @At("RETURN"), remap = false, require = 0)
    private static void efw$ragdollEnd(@Coerce Object ragdoll, @Coerce Object snapshot, PoseStack pose, MultiBufferSource buffers, int light,
            float partialTick, double scale, CallbackInfo ci) {
        efw$ragdollOwner = null;
    }

    // renderPlayerBody up to Ragdollified 1.1.x
    private static final String BODY_OLD = "renderPlayerBody(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ID"
            + "Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/raiiiden/ragdollified/RagdollTransform;"
            + "Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/raiiiden/ragdollified/RagdollTransform;"
            + "Lnet/minecraft/resources/ResourceLocation;ZLnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/player/AbstractClientPlayer;F"
            + "Ljava/lang/Object;Ljava/util/List;)V";
    // 1.2.0 put one more texture after the slim flag
    private static final String BODY_NEW = "renderPlayerBody(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ID"
            + "Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/raiiiden/ragdollified/RagdollTransform;"
            + "Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/raiiiden/ragdollified/RagdollTransform;"
            + "Lnet/minecraft/resources/ResourceLocation;ZLnet/minecraft/resources/ResourceLocation;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/client/player/AbstractClientPlayer;FLjava/lang/Object;Ljava/util/List;)V";

    @Inject(method = BODY_OLD, at = @At("HEAD"), remap = false, require = 0)
    private static void efw$bodyStart(PoseStack pose, MultiBufferSource buffers, int light, double scale, @Coerce Object head, @Coerce Object torso,
            @Coerce Object rightArm, @Coerce Object leftArm, @Coerce Object rightLeg, @Coerce Object leftLeg, ResourceLocation skin, boolean slim,
            ItemStack helmet, ItemStack chest, ItemStack legs, ItemStack boots, AbstractClientPlayer player, float alpha, Object blood,
            List<?> curios, CallbackInfo ci) {
        efw$enterBody(player, chest);
    }

    @Inject(method = BODY_NEW, at = @At("HEAD"), remap = false, require = 0)
    private static void efw$bodyStartNew(PoseStack pose, MultiBufferSource buffers, int light, double scale, @Coerce Object head, @Coerce Object torso,
            @Coerce Object rightArm, @Coerce Object leftArm, @Coerce Object rightLeg, @Coerce Object leftLeg, ResourceLocation skin, boolean slim,
            ResourceLocation texture, ItemStack helmet, ItemStack chest, ItemStack legs, ItemStack boots, AbstractClientPlayer player, float alpha,
            Object blood, List<?> curios, CallbackInfo ci) {
        efw$enterBody(player, chest);
    }

    @Inject(method = {BODY_OLD, BODY_NEW}, at = @At("RETURN"), remap = false, require = 0)
    private static void efw$bodyEnd(CallbackInfo ci) {
        efw$bodyOwner = null;
        efw$chest = ItemStack.EMPTY;
    }

    private static void efw$enterBody(AbstractClientPlayer player, ItemStack chest) {
        efw$bodyOwner = player != null ? player.getUUID() : efw$ragdollOwner;
        efw$chest = chest == null ? ItemStack.EMPTY : chest;
        efw$torsoDone = false;
    }

    @Inject(method = "renderHumanoidPartPhysics(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/model/geom/ModelPart;Lcom/raiiiden/ragdollified/RagdollTransform;Lcom/raiiiden/ragdollified/RagdollTransform;ILcom/raiiiden/ragdollified/RagdollPart;Lcom/raiiiden/ragdollified/client/ClientRagdollRenderer$HumanoidScale;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
                    remap = true), remap = false, require = 0)
    private static void efw$torso(PoseStack pose, VertexConsumer buffer, ModelPart part, @Coerce Object transform, @Coerce Object reference, int light,
            @Coerce Object ragdollPart, @Coerce Object humanoidScale, CallbackInfo ci) {
        if (efw$bodyOwner == null || efw$torsoDone || !"TORSO".equals(String.valueOf(ragdollPart))) {
            return;
        }
        // the first torso pass is the skin, the second the jacket layer (the breasts draw their own)
        efw$torsoDone = true;
        GenderLayer.renderRagdoll(efw$bodyOwner, part, efw$chest, pose, buffer, light);
    }

    private static UUID efw$owner(Object ragdoll) {
        try {
            if (efw$uuidField == null) {
                Field f = ragdoll.getClass().getDeclaredField("playerUUID");
                f.setAccessible(true);
                efw$uuidField = f;
            }
            return (UUID) efw$uuidField.get(ragdoll);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
