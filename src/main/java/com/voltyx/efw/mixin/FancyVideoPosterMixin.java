package com.voltyx.efw.mixin;

import com.voltyx.efw.client.VideoPoster;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Field;

/**
 * FancyMenu's video background fills the screen black and draws the video over it once WaterMedia has the first frame, so every
 * opening of the menu starts with half a second of black. The poster of the video (see {@link VideoPoster}) goes over that fill,
 * fitted like the video (covering the screen at its own aspect ratio when the background keeps it, stretched otherwise); the
 * video covers it as soon as it plays. FancyMenu is built with SRG names, so its render is m_88315_ in the game.
 */
@Pseudo
@Mixin(targets = "de.keksuccino.fancymenu.customization.background.backgrounds.video.nativevideo.NativeVideoMenuBackground", remap = false)
public abstract class FancyVideoPosterMixin {
    @Unique
    private static Field efw$keepAspect;

    @Shadow(remap = false)
    protected volatile String currentResolvedVideoSource;

    @Redirect(method = {"render", "m_88315_"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", remap = true), remap = false, require = 0)
    private void efw$poster(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y1, color);
        VideoPoster.Poster poster = VideoPoster.forSource(currentResolvedVideoSource);
        if (poster == null) {
            return;
        }
        int w = x1 - x0;
        int h = y1 - y0;
        int dw = w;
        int dh = h;
        if (efw$keepsAspect()) {
            float scale = Math.max(w / (float) poster.width(), h / (float) poster.height());
            dw = Math.round(poster.width() * scale);
            dh = Math.round(poster.height() * scale);
        }
        g.enableScissor(x0, y0, x1, y1);
        g.setColor(1.0f, 1.0f, 1.0f, (color >>> 24) / 255.0f);
        g.blit(poster.texture(), x0 + (w - dw) / 2, y0 + (h - dh) / 2, 0.0f, 0.0f, dw, dh, dw, dh);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        g.disableScissor();
    }

    @Unique
    private boolean efw$keepsAspect() {
        try {
            if (efw$keepAspect == null) {
                efw$keepAspect = Class.forName("de.keksuccino.fancymenu.customization.background.MenuBackground")
                        .getField("keepBackgroundAspectRatio");
            }
            return efw$keepAspect.getBoolean(this);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
