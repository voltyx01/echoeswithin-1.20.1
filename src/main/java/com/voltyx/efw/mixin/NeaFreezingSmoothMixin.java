package com.voltyx.efw.mixin;

import com.voltyx.efw.client.Shiver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Not Enough Animations crosses the arms of a frozen player and shakes them with Math.random() every frame: a new random angle per
 * frame looks like the model glitching. The random numbers are replaced by smooth noise (the left and the right arm by turns).
 */
@Pseudo
@Mixin(targets = "dev.tr7zw.notenoughanimations.animations.fullbody.FreezingAnimation", remap = false)
public abstract class NeaFreezingSmoothMixin {
    @Unique
    private boolean efw$second;

    @Redirect(method = "apply", at = @At(value = "INVOKE", target = "Ljava/lang/Math;random()D"), remap = false, require = 0)
    private double efw$smoothShake() {
        efw$second = !efw$second;
        return Shiver.unit(efw$second ? 0.0f : 2.7f);
    }
}
