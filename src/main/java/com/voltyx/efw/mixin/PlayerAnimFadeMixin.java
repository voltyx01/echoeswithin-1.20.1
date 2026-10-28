package com.voltyx.efw.mixin;

import com.voltyx.efw.client.anim.AnimBlend;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The fade between two animations of the player animation library: rotations are blended along the shorter way round (the
 * straight blend of the clamped angles turned the arms through the back when lying down or getting up), and the fade can tell how
 * much animation is in its output, so that the adjustments on top of it fade with it.
 */
@Pseudo
@Mixin(targets = "dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier", remap = false)
public abstract class PlayerAnimFadeMixin extends AbstractModifier implements AnimBlend.Weighted {
    @Shadow(remap = false)
    @Nullable
    protected IAnimation beginAnimation;

    @Shadow(remap = false)
    protected int length;

    @Override
    public int efw$length() {
        return length;
    }

    @Shadow(remap = false)
    protected abstract float getAlpha(String modelName, TransformType type, float progress);

    @Shadow(remap = false)
    protected abstract float calculateProgress(float f);

    @Inject(method = "get3DTransform", at = @At("HEAD"), cancellable = true, remap = false)
    private void efw$blend(String modelName, TransformType type, float tickDelta, Vec3f value0, CallbackInfoReturnable<Vec3f> cir) {
        Vec3f target = anim == null ? value0 : anim.get3DTransform(modelName, type, tickDelta, value0);
        float progress = calculateProgress(tickDelta);
        if (progress >= 1.0f) {
            cir.setReturnValue(target);
            return;
        }
        float a = AnimBlend.clamp01(getAlpha(modelName, type, Math.max(0.0f, progress)));
        Vec3f source = beginAnimation != null && beginAnimation.isActive() ? beginAnimation.get3DTransform(modelName, type, tickDelta, value0) : value0;
        if (type == TransformType.ROTATION) {
            cir.setReturnValue(new Vec3f(AnimBlend.angleLerp(source.getX(), target.getX(), a), AnimBlend.angleLerp(source.getY(), target.getY(), a),
                    AnimBlend.angleLerp(source.getZ(), target.getZ(), a)));
        } else if (type == TransformType.BEND) {
            // x is the direction of the bend, an angle; y is its amount
            cir.setReturnValue(new Vec3f(AnimBlend.angleLerp(source.getX(), target.getX(), a), source.getY() + (target.getY() - source.getY()) * a,
                    source.getZ() + (target.getZ() - source.getZ()) * a));
        } else {
            cir.setReturnValue(target.scale(a).add(source.scale(1 - a)));
        }
    }

    @Override
    public IAnimation efw$begin() {
        return beginAnimation;
    }

    @Override
    public float efw$contentWeight(float tickDelta) {
        boolean to = anim != null && anim.isActive();
        boolean from = beginAnimation != null && beginAnimation.isActive();
        float progress = calculateProgress(tickDelta);
        if (progress >= 1.0f) {
            return to ? 1.0f : 0.0f;
        }
        float a = AnimBlend.clamp01(getAlpha("body", TransformType.ROTATION, Math.max(0.0f, progress)));
        return (to ? a : 0.0f) + (from ? 1.0f - a : 0.0f);
    }
}
