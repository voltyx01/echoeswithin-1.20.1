package com.voltyx.efw.mixin;

import com.voltyx.efw.client.HurtFlash;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla shows every drop of the synced health as a hit (camera jolt). Legendary Survival Overhaul lowers the health on its own
 * when it breaks a heart, about half a second after the blow, which looked like a second, phantom hit: a drop without a real hit
 * just before it (HurtFlash) only sets the health.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerHealthCutMixin {
    @Shadow
    private boolean flashOnSetHealth;

    @Inject(method = "hurtTo", at = @At("HEAD"), cancellable = true)
    private void efw$noPhantomHit(float health, CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (flashOnSetHealth && health < self.getHealth() && !HurtFlash.recentlyHit()) {
            self.setHealth(health);
            ci.cancel();
        }
    }
}
