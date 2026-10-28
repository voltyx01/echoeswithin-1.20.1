package com.voltyx.efw.mixin;

import com.voltyx.efw.client.RollClient;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The roll's movement (as in Combat Roll): no jump and no auto jump while it lasts, the push stays on the ground. */
@Mixin(LocalPlayer.class)
public abstract class RollMovementMixin {
    @Shadow
    public Input input;

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/Input;tick(ZF)V", shift = At.Shift.AFTER))
    private void efw$noJumpWhileRolling(CallbackInfo ci) {
        if (RollClient.isMoving()) {
            input.jumping = false;
        }
    }

    @Inject(method = "canAutoJump", at = @At("HEAD"), cancellable = true)
    private void efw$noAutoJumpWhileRolling(CallbackInfoReturnable<Boolean> cir) {
        if (RollClient.isMoving()) {
            cir.setReturnValue(false);
        }
    }
}
