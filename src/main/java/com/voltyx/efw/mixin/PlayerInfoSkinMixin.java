package com.voltyx.efw.mixin;

import com.mojang.authlib.GameProfile;
import com.voltyx.efw.skin.client.ClientSkins;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The skin from the launcher (SkinSync) replaces the account skin. PlayerInfo is where AbstractClientPlayer, the tab list and other
 * mods get the skin and the arm model from, so everything that draws the player takes it.
 */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoSkinMixin {
    @Shadow
    public abstract GameProfile getProfile();

    @Inject(method = "getSkinLocation", at = @At("HEAD"), cancellable = true)
    private void efw$skin(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation skin = ClientSkins.texture(getProfile().getId());
        if (skin != null) {
            cir.setReturnValue(skin);
        }
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void efw$model(CallbackInfoReturnable<String> cir) {
        String model = ClientSkins.model(getProfile().getId());
        if (model != null) {
            cir.setReturnValue(model);
        }
    }

    @Inject(method = "isSkinLoaded", at = @At("HEAD"), cancellable = true)
    private void efw$loaded(CallbackInfoReturnable<Boolean> cir) {
        if (ClientSkins.texture(getProfile().getId()) != null) {
            cir.setReturnValue(true);
        }
    }
}
