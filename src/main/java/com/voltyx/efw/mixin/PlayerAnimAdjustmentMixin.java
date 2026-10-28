package com.voltyx.efw.mixin;

import com.voltyx.efw.client.anim.AnimBlend;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The adjustment modifier (TACZ turns the body, the head and the arms after the camera with it, Better Combat the body):
 * <ul>
 * <li>its fade-in divided by the begin tick of the animation, which is 0 for most of them: the first frame was 0/0 = NaN and the
 * model parts jumped;</li>
 * <li>its fade-out is worked out from the end of the animation, which a looping animation never reaches: on a one-tick loop (the
 * "empty" pose TACZ keeps while a gun is held) it ran from 1 to 0 inside every tick and the whole model twitched. A loop keeps the
 * adjustment at full strength, unless the mod asked for a fade-out;</li>
 * <li>it stayed at full strength while the layer under it faded out (putting a gun away: the head and the arms got the pitch of
 * the camera twice, then snapped back when the fade ended). It now fades with the layer.</li>
 * </ul>
 */
@Pseudo
@Mixin(targets = "dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier", remap = false)
public abstract class PlayerAnimAdjustmentMixin {
    @Shadow(remap = false)
    protected int instructedFadeout;

    private boolean efw$onLoop() {
        return ((AbstractModifier) (Object) this).getAnim() instanceof KeyframeAnimationPlayer player && player.isInfinite();
    }

    @Inject(method = "getFadeIn", at = @At("RETURN"), cancellable = true, remap = false)
    private void efw$fadeIn(float delta, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(efw$onLoop() ? 1.0f : AnimBlend.clamp01(cir.getReturnValueF()));
    }

    @Inject(method = "getFadeOut", at = @At("RETURN"), cancellable = true, remap = false)
    private void efw$fadeOut(float delta, CallbackInfoReturnable<Float> cir) {
        // instructedFadeout is only set while a fade-out asked for by the mod is running
        float own = instructedFadeout <= 0 && efw$onLoop() ? 1.0f : AnimBlend.clamp01(cir.getReturnValueF());
        cir.setReturnValue(own * AnimBlend.chainWeight(((AbstractModifier) (Object) this).getAnim(), delta));
    }
}
