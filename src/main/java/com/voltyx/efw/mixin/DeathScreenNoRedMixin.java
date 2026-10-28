package com.voltyx.efw.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The death screen has no red wash: it comes out of the black of the closed eyes (EyesOpen). */
@Mixin(DeathScreen.class)
public abstract class DeathScreenNoRedMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fillGradient(IIIIII)V"), require = 0)
    private void efw$noRed(GuiGraphics g, int x0, int y0, int x1, int y1, int from, int to) {
    }
}
