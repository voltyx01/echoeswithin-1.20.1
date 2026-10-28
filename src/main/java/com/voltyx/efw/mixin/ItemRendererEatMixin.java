package com.voltyx.efw.mixin;

import com.voltyx.efw.client.eat.BittenModels;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Procedural eating animation: the food being eaten is drawn bitten (see BittenModels). */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererEatMixin {
    @Inject(method = "getModel", at = @At("RETURN"), cancellable = true)
    private void efw$bitten(ItemStack stack, @Nullable Level level, @Nullable LivingEntity entity, int seed, CallbackInfoReturnable<BakedModel> cir) {
        BakedModel bitten = BittenModels.select(cir.getReturnValue(), stack, entity);
        if (bitten != cir.getReturnValue()) {
            cir.setReturnValue(bitten);
        }
    }
}
