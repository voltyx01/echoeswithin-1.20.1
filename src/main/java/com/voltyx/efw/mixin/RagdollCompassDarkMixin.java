package com.voltyx.efw.mixin;

import com.voltyx.efw.client.dark.DarkGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Dark theme for the corpse compass screen of Ragdollified: Corpses, drawn by code in vanilla greys like the corpse screen
 * ({@link RagdollCorpseDarkMixin}); not a container, so its dark grey labels are made light here too.
 */
@Pseudo
@Mixin(targets = "com.raiiiden.ragdollifiedpc.client.screen.CorpseCompassScreen", remap = false)
public abstract class RagdollCompassDarkMixin {
    @ModifyArg(method = {"drawPanel", "drawVDivider", "drawInsetBorder"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", remap = true), index = 4, remap = false, require = 0)
    private static int efw$darkFill(int color) {
        return DarkGui.fillColor(color);
    }

    // render: the box behind the location
    @ModifyArg(method = "m_88315_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", remap = true), index = 4, remap = false, require = 0)
    private int efw$darkRenderFill(int color) {
        return DarkGui.fillColor(color);
    }

    @ModifyArg(method = "m_88315_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",
            remap = true), index = 4, remap = false, require = 0)
    private int efw$lightText(int color) {
        return DarkGui.lightText(color);
    }
}
