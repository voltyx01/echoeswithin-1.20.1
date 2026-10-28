package com.voltyx.efw.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voltyx.efw.hook.ChainHookItem;
import com.voltyx.efw.hook.client.HookClient;
import com.voltyx.efw.hook.client.HookSwingLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The chain hook is not held as an item while it is swung (HookSwingLayer draws it) or thrown (it hangs on the end of the chain). */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerHookMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void efw$hideChainHook(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack pose,
                                   MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (entity instanceof Player player && stack.getItem() instanceof ChainHookItem && arm == player.getMainArm().getOpposite()
                && (HookSwingLayer.swinging(player) || HookClient.hasHookOut(player))) {
            ci.cancel();
        }
    }
}
