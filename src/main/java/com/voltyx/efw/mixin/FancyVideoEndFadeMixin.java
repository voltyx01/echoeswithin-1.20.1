package com.voltyx.efw.mixin;

import com.voltyx.efw.client.loading.IntroBlackout;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * A FancyMenu video element that does not loop stays on its last frame for good (the intro covers the menu with black). Once it
 * has played to the end it dissolves and is no longer drawn; so does one that never starts or gets stuck (the element fills its area
 * black until WaterMedia has a frame, the buttons stayed under that). FancyMenu is built with SRG names, so its render is m_88315_.
 */
@Pseudo
@Mixin(targets = "de.keksuccino.fancymenu.customization.element.elements.video.nativevideo.NativeVideoElement", remap = false)
public abstract class FancyVideoEndFadeMixin {
    @Unique
    private static final long EFW_FADE_MS = 400L;
    @Unique
    private static Field efw$opacity;

    @Shadow(remap = false)
    protected Boolean lastLoop;

    @Unique
    private float efw$maxPlayed;
    @Unique
    private long efw$endedAt = -1L;
    /** Not started this long after the first frame of the element, or stuck this long: given up, it dissolves. */
    @Unique
    private static final long EFW_START_MS = 6000L, EFW_STALL_MS = 2500L;
    @Unique
    private long efw$firstRender = -1L;
    @Unique
    private long efw$progressAt = -1L;

    @Shadow(remap = false)
    public abstract float getPlayTime();

    @Shadow(remap = false)
    public abstract float getDuration();

    @Inject(method = {"render", "m_88315_"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void efw$fadeAtEnd(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (Boolean.TRUE.equals(lastLoop)) {
            efw$endedAt = -1L;
            return;
        }
        if (efw$endedAt < 0L) {
            long now = Util.getMillis();
            if (efw$firstRender < 0L) {
                efw$firstRender = now;
                efw$progressAt = now;
            }
            float duration = getDuration();
            float played = getPlayTime();
            if (played > efw$maxPlayed + 0.01f) {
                efw$maxPlayed = played;
                efw$progressAt = now;
            }
            if (efw$maxPlayed > 0.05f) {
                IntroBlackout.introPlaying();
            }
            boolean atEnd = duration > 0.0f && efw$maxPlayed >= duration - 0.15f;
            // the element fills its area black until there is a frame: an intro that never starts or gets stuck would stay black
            boolean neverStarted = efw$maxPlayed <= 0.05f && now - efw$firstRender > EFW_START_MS;
            boolean stuck = efw$maxPlayed > 0.05f && now - efw$progressAt > EFW_STALL_MS;
            if (!atEnd && !neverStarted && !stuck) {
                return;
            }
            efw$endedAt = now;
            IntroBlackout.introEnded(); // the menu animations start now
        }
        float alpha = 1.0f - (Util.getMillis() - efw$endedAt) / (float) EFW_FADE_MS;
        if (alpha <= 0.0f) {
            ci.cancel();
            return;
        }
        try {
            if (efw$opacity == null) {
                efw$opacity = Class.forName("de.keksuccino.fancymenu.customization.element.AbstractElement").getField("opacity");
            }
            efw$opacity.setFloat(this, Math.min(efw$opacity.getFloat(this), alpha));
        } catch (ReflectiveOperationException e) {
            ci.cancel(); // no fade then, but the element still goes away
        }
    }
}
