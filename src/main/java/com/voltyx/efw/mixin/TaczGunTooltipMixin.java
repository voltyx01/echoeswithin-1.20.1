package com.voltyx.efw.mixin;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The TACZ gun tooltip shows only its base block (damage, type, level) unless Shift is held; the description, the ammo,
 * the armor/headshot/weight block, the refit tip and the pack name come with Shift (see TooltipDetails for the hint line).
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.tooltip.ClientGunTooltip", remap = false)
public abstract class TaczGunTooltipMixin {
    /** Taken once per tooltip: its texts, height and drawing must agree on the same parts. */
    @Unique
    private Boolean efw$details;

    @Inject(method = "shouldShow", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void efw$detailsOnShift(@Coerce Object part, CallbackInfoReturnable<Boolean> cir) {
        if (efw$details == null) {
            efw$details = Screen.hasShiftDown();
        }
        if (!efw$details && !"BASE_INFO".equals(((Enum<?>) part).name())) {
            cir.setReturnValue(false);
        }
    }
}
