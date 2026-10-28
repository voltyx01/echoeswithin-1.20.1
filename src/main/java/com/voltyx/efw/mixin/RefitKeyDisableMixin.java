package com.voltyx.efw.mixin;

import net.minecraftforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The TACZ modification screen is opened from the inventory (X over a gun) only: the in-game key does nothing. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.input.RefitKey", remap = false)
public abstract class RefitKeyDisableMixin {
    @Inject(method = "onRefitPress(Lnet/minecraftforge/client/event/InputEvent$Key;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void efw$disabled(InputEvent.Key event, CallbackInfo ci) {
        ci.cancel();
    }
}
