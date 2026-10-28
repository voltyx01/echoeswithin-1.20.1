package com.voltyx.efw.mixin;

import com.voltyx.efw.speech.client.SpeechClientEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stylish Effects: the effect icons left of the inventory slide under it while the chat panel of the inventory opens (cut at the
 * edge of the inventory), so the panel can be wider; their tooltips are off meanwhile.
 */
@Pseudo
@Mixin(targets = "fuzs.stylisheffects.client.handler.EffectScreenHandlerImpl", remap = false)
public abstract class StylishEffectsChatSlideMixin {
    @Unique
    private boolean efw$sliding;

    @Inject(method = "onDrawBackground", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void efw$slideStart(AbstractContainerScreen<?> screen, GuiGraphics g, int mouseX, int mouseY, CallbackInfo ci) {
        float slide = SpeechClientEvents.inventoryChatSlide();
        efw$sliding = false;
        if (slide <= 0.0F || !(screen instanceof InventoryScreen inventory)) {
            return;
        }
        if (slide >= 1.0F) {
            ci.cancel();
            return;
        }
        int left = inventory.getGuiLeft();
        g.enableScissor(0, 0, left, screen.height);
        g.pose().pushPose();
        g.pose().translate(left * slide, 0.0F, 0.0F);
        efw$sliding = true;
    }

    @Inject(method = "onDrawBackground", at = @At("RETURN"), remap = false, require = 0)
    private void efw$slideEnd(AbstractContainerScreen<?> screen, GuiGraphics g, int mouseX, int mouseY, CallbackInfo ci) {
        if (efw$sliding) {
            efw$sliding = false;
            g.pose().popPose();
            g.disableScissor();
        }
    }

    @Inject(method = "onDrawForeground", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void efw$noTooltip(AbstractContainerScreen<?> screen, GuiGraphics g, int mouseX, int mouseY, CallbackInfo ci) {
        if (screen instanceof InventoryScreen && SpeechClientEvents.inventoryChatSlide() > 0.0F) {
            ci.cancel();
        }
    }
}
