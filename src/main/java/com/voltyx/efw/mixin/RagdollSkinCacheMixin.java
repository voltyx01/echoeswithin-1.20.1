package com.voltyx.efw.mixin;

import com.voltyx.efw.gear.client.OlympicSkin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Ragdollified remembers the skins of the players around (also on disk) to draw their bodies with: it gets the plain skin, not the
 * track jacket version a player in the jacket is drawn with, or every later body of that player would wear the jacket.
 */
@Pseudo
@Mixin(targets = "com.raiiiden.ragdollified.client.ClientPlayerSkinCache", remap = false)
public abstract class RagdollSkinCacheMixin {
    @Redirect(method = "fromLivingPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;getSkinTextureLocation()Lnet/minecraft/resources/ResourceLocation;",
            remap = true), remap = false, require = 0)
    private static ResourceLocation efw$plainSkin(AbstractClientPlayer player) {
        return OlympicSkin.plain(player.getSkinTextureLocation());
    }
}
