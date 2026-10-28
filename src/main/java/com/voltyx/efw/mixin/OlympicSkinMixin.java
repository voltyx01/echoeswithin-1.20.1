package com.voltyx.efw.mixin;

import com.voltyx.efw.gear.client.OlympicSkin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player wearing the worn track jacket is drawn with the jacket version of their skin (see OlympicSkin). */
@Mixin(AbstractClientPlayer.class)
public abstract class OlympicSkinMixin {
    @Inject(method = "getSkinTextureLocation", at = @At("RETURN"), cancellable = true)
    private void efw$olympic(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation skin = OlympicSkin.skin((AbstractClientPlayer) (Object) this, cir.getReturnValue());
        if (skin != cir.getReturnValue()) {
            cir.setReturnValue(skin);
        }
    }
}
