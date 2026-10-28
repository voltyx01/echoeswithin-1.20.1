package com.voltyx.efw.mixin;

import com.voltyx.efw.fireweapon.FireWeaponHelper;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium Dynamic Lights (LambDynamicLights): a burning weapon lights up around the one holding it (or where it lies). Light sources
 * there are per item, a burning weapon only differs by its tag, so the luminance of every stack is raised here: soaked in fuel like
 * a torch, burning cloth only a little.
 */
@Pseudo
@Mixin(targets = "dev.lambdaurora.lambdynlights.api.item.ItemLightSources", remap = false)
public abstract class FireWeaponLightMixin {
    private static final int SOAKED_LIGHT = 14;
    private static final int CLOTH_LIGHT = 6;

    @Inject(method = "getLuminance", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void efw$burningWeapon(ItemStack stack, boolean submergedInWater, CallbackInfoReturnable<Integer> cir) {
        if (com.voltyx.efw.compat.GunOffhandLight.isHidden(stack)) {
            cir.setReturnValue(0);
            return;
        }
        if (submergedInWater || stack.isEmpty() || !FireWeaponHelper.isIgnited(stack)) {
            return;
        }
        int light = FireWeaponHelper.isSoaked(stack) ? SOAKED_LIGHT : CLOTH_LIGHT;
        if (light > cir.getReturnValueI()) {
            cir.setReturnValue(light);
        }
    }
}
