package com.voltyx.efw.mixin;

import com.voltyx.efw.client.dark.DarkGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Dark theme for the corpse screen of Ragdollified: Corpses. It draws its panel, dividers and slots by code in vanilla greys, not with
 * a texture, so the theme (which recolours textures) left it light: the greys are turned into the theme's here.
 */
@Pseudo
@Mixin(targets = "com.raiiiden.ragdollifiedpc.client.screen.CorpseScreen", remap = false)
public abstract class RagdollCorpseDarkMixin {
    @ModifyArg(method = {"drawPanel", "drawDivider", "drawSlot"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", remap = true), index = 4, remap = false, require = 0)
    private static int efw$darkFill(int color) {
        return DarkGui.fillColor(color);
    }
}
