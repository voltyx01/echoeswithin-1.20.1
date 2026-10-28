package com.voltyx.efw.mixin;

import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.playeranimator.animation.PlayerAnimatorAssetManager;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TACZ plays a one-shot upper body animation only when the layer is free, and shots, reloads and melee hits share that layer. A
 * reload right after shooting (or after anything else on the layer) was simply dropped. A reload or a melee hit now takes the
 * layer over: the layer is cleared just before TACZ looks at it, and the layer fix fades from the cleared animation.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.compat.playeranimator.animation.AnimationManager", remap = false)
public abstract class TaczOnceAnimationMixin {
    @Inject(method = "playOnceAnimation", at = @At("HEAD"), remap = false)
    @SuppressWarnings("unchecked")
    private static void efw$interrupt(AbstractClientPlayer player, GunDisplayInstance display, ResourceLocation dataId, String name, CallbackInfo ci) {
        if (!name.contains("reload") && !name.contains("melee")) {
            return;
        }
        ResourceLocation id = display.getPlayerAnimator3rd();
        if (id == null || !PlayerAnimatorAssetManager.get().containsKey(id)
                || ((TaczPlayerAnimAssetsInvoker) PlayerAnimatorAssetManager.get()).efw$getAnimations(id, name).isEmpty()) {
            return;
        }
        IAnimation layer = PlayerAnimationAccess.getPlayerAssociatedData(player).get(dataId);
        if (layer instanceof ModifierLayer<?> modifierLayer && modifierLayer.getAnimation() != null && modifierLayer.getAnimation().isActive()) {
            ((ModifierLayer<IAnimation>) modifierLayer).setAnimation(null);
        }
    }
}
