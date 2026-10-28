package com.voltyx.efw.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voltyx.efw.fireweapon.client.FireWeaponItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Weapons wrapped in cloth (burning, charred) are drawn from their composited textures. */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererFireMixin {
    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"), cancellable = true)
    private void efw$fireWeapon(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose, MultiBufferSource buffers, int light,
                                int overlay, BakedModel model, CallbackInfo ci) {
        if (FireWeaponItemRenderer.render(stack, context, leftHand, pose, buffers, light, overlay, model)) {
            ci.cancel();
        }
    }
}
