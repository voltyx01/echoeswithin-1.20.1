package com.voltyx.efw.mixin;

import com.voltyx.efw.nvg.client.NvgVisor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The visor of a night vision helmet goes up and down with the switch (the 1.12 NVGAnimationHelper on the helmet model). The
 * helmet's own model only comes out of Forge's armour model hook, after the layer has posed the default model (survival_instinct
 * builds that model anew every frame), so the visor is put into its position on the model the hook returns.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class NvgVisorMixin {
    @Inject(method = "getArmorModelHook", at = @At("RETURN"), remap = false)
    private void efw$nvgVisor(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> model,
                              CallbackInfoReturnable<Model> cir) {
        if (slot == EquipmentSlot.HEAD && cir.getReturnValue() instanceof HumanoidModel<?> helmet) {
            NvgVisor.apply(helmet, entity);
        }
    }
}
