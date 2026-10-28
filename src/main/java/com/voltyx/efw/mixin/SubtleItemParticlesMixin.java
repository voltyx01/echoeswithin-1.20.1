package com.voltyx.efw.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Subtle Effects: every item lying on the ground gives off the same white particles, whatever its rarity or name colour
 * (the particles mark loot, they do not grade it).
 */
@Pseudo
@Mixin(targets = "einstein.subtle_effects.ticking.tickers.entity.ItemRarityTicker", remap = false)
public abstract class SubtleItemParticlesMixin {
    @Shadow(remap = false)
    @Final
    private List<Integer> colors;

    @Inject(method = "getItemNameColors", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void efw$white(CallbackInfo ci) {
        colors.add(0xFFFFFF);
        ci.cancel();
    }
}
