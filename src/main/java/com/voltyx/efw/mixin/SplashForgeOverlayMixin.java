package com.voltyx.efw.mixin;

import com.voltyx.efw.client.loading.EfwSplash;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.loading.ForgeLoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The first resource load: Forge draws its early window into the game window here. The efw splash goes over it with the same fade
 * Forge uses (it starts a second after loading ended and lasts a second, the title screen showing through).
 * The method is Minecraft's render, so it has the official name in a dev run and the SRG name in the game.
 */
@Mixin(value = ForgeLoadingOverlay.class, remap = false)
public abstract class SplashForgeOverlayMixin {
    @Shadow(remap = false)
    private long fadeOutStart;

    @Inject(method = {"render", "m_88315_"}, at = @At("TAIL"), remap = false, require = 0)
    private void efw$splash(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (EfwSplash.providerActive()) {
            return; // the EFW early window draws the splash into this overlay already
        }
        float timer = fadeOutStart > -1L ? (Util.getMillis() - fadeOutStart) / 1000.0f : -1.0f;
        float fade = 1.0f - Math.max(0.0f, Math.min(1.0f, timer - 1.0f));
        EfwSplash.renderOverlay(g, fade);
    }
}
