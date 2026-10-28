package com.voltyx.efw.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voltyx.efw.compat.TaczCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the modification screen is open it draws the gun itself: nothing is drawn for the hands of the player (no double gun). */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRefitHideMixin {
    @Inject(method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V",
            at = @At("HEAD"), cancellable = true)
    private void efw$noHandsOnRefit(float partial, PoseStack pose, MultiBufferSource.BufferSource buffers, LocalPlayer player, int light, CallbackInfo ci) {
        if (TaczCompat.isRefitScreen(Minecraft.getInstance().screen)) {
            ci.cancel();
        }
    }
}
