package com.voltyx.efw.mixin;

import com.voltyx.efw.client.loading.IntroBlackout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * FancyMenu's element animations (the main menu buttons flying in) run on the wall clock from the first frame the menu is drawn,
 * which can be under the loading screen or the black before the intro: by the time the menu is seen they are over. Their clock
 * stands still while the menu waits for its intro to play out ({@link IntroBlackout#waitingForIntro()}) and then skips the still
 * start of the animations, so the buttons move right as the intro ends. And when the screen changes FancyMenu
 * drops the running animations without putting their elements back: a button caught mid-flight stayed there for the whole session
 * (its animation runs once per session). They are put back first now.
 */
@Pseudo
@Mixin(targets = "de.keksuccino.fancymenu.customization.element.elements.animationcontroller.runtime.AnimationControllerRuntime", remap = false)
public abstract class FancyAnimationClockMixin {
    @Shadow(remap = false)
    private static Map<String, ?> RUNNING_ANIMATIONS;

    /** Time the clock stood still, taken off the wall clock. */
    @Unique
    private static long efw$paused;
    @Unique
    private static long efw$lastReal = -1L;
    @Unique
    private static Method efw$restore;
    @Unique
    private static java.lang.reflect.Field efw$startTime;
    @Unique
    private static Method efw$keyframes;

    @Unique
    private static long efw$clock(long real) {
        if (efw$lastReal >= 0L && real > efw$lastReal && IntroBlackout.waitingForIntro()) {
            efw$paused += real - efw$lastReal;
        }
        efw$lastReal = Math.max(efw$lastReal, real);
        if (IntroBlackout.consumeRelease()) {
            efw$skipHold(real - efw$paused);
        }
        return real - efw$paused;
    }

    /**
     * The intro is over: the clock jumps over the still start of the animations (the buttons held out of sight while the intro
     * plays, 5.5 s in the menu layout), so they move at once, as the intro dissolves. The jump is the shortest still part left of
     * the running animations, no animation is cut into.
     */
    @Unique
    private static void efw$skipHold(long now) {
        if (RUNNING_ANIMATIONS == null || RUNNING_ANIMATIONS.isEmpty()) {
            return;
        }
        long skip = Long.MAX_VALUE;
        try {
            for (Object animation : RUNNING_ANIMATIONS.values()) {
                if (efw$startTime == null) {
                    efw$startTime = animation.getClass().getDeclaredField("startTime");
                    efw$startTime.setAccessible(true);
                    efw$keyframes = animation.getClass().getDeclaredMethod("getKeyframes");
                    efw$keyframes.setAccessible(true);
                }
                long start = efw$startTime.getLong(animation);
                long holdEnd = efw$holdEnd((java.util.List<?>) efw$keyframes.invoke(animation));
                skip = Math.min(skip, start + holdEnd - now);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return;
        }
        if (skip > 0L && skip != Long.MAX_VALUE) {
            efw$paused -= skip;
        }
    }

    /** Until when the keyframes stay as the first one (same offset and size): the still start. */
    @Unique
    private static long efw$holdEnd(java.util.List<?> keyframes) throws ReflectiveOperationException {
        if (keyframes.isEmpty()) {
            return 0L;
        }
        Class<?> type = keyframes.get(0).getClass();
        java.lang.reflect.Field t = type.getField("timestamp"), x = type.getField("posOffsetX"), y = type.getField("posOffsetY"),
                w = type.getField("baseWidth"), h = type.getField("baseHeight");
        Object first = keyframes.get(0);
        long end = t.getLong(first);
        for (Object k : keyframes) {
            if (x.getInt(k) != x.getInt(first) || y.getInt(k) != y.getInt(first) || w.getInt(k) != w.getInt(first)
                    || h.getInt(k) != h.getInt(first)) {
                break;
            }
            end = t.getLong(k);
        }
        return end;
    }

    @ModifyVariable(method = "tick(J)V", at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private static long efw$tickClock(long currentTime) {
        return efw$clock(currentTime);
    }

    @ModifyVariable(method = "applyAnimation(Lde/keksuccino/fancymenu/customization/element/elements/animationcontroller/AnimationControllerElement;"
            + "Lde/keksuccino/fancymenu/customization/element/elements/animationcontroller/AnimationControllerElement$TargetElement;"
            + "Lde/keksuccino/fancymenu/customization/element/AbstractElement;J)Z", at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private static long efw$startClock(long currentTime) {
        return efw$clock(currentTime);
    }

    @Inject(method = "stopAllAnimations", at = @At("HEAD"), remap = false, require = 0)
    private static void efw$restoreBeforeStop(CallbackInfo ci) {
        if (RUNNING_ANIMATIONS == null || RUNNING_ANIMATIONS.isEmpty()) {
            return;
        }
        for (Object animation : RUNNING_ANIMATIONS.values()) {
            try {
                if (efw$restore == null) {
                    efw$restore = animation.getClass().getDeclaredMethod("restoreOriginalState");
                    efw$restore.setAccessible(true);
                }
                efw$restore.invoke(animation);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return;
            }
        }
    }
}
