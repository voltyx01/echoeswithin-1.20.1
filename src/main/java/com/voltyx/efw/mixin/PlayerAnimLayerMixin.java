package com.voltyx.efw.mixin;

import com.voltyx.efw.client.anim.AnimBlend;
import com.voltyx.efw.client.anim.HeldPose;
import com.voltyx.efw.client.anim.Locomotion;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Transitions of a modifier layer of the player animation library. The library had three ways of making a transition jump:
 * <ul>
 * <li>a new fade started from the target of the fade still running, not from the pose on screen (switching walk, run and stand
 * quickly jerked);</li>
 * <li>there was no fade at all when the layer was empty, or when a mod emptied it right before (Serious Player Animations does
 * that), so animations popped in;</li>
 * <li>an animation that simply ended vanished in one tick, and the body jumped to idle.</li>
 * </ul>
 * Now a fade always starts from what is on screen (the running fades go on inside it), a layer that was emptied a moment ago fades
 * from the animation it had, and an ended animation fades out from its last frame. The walk, run, stand and fall of Serious Player
 * Animations get their own care on top ({@link Locomotion}): a switch shows only once it lasts, it fades long enough and the step
 * goes on where the legs are.
 */
@Pseudo
@Mixin(targets = "dev.kosmx.playerAnim.api.layered.ModifierLayer", remap = false)
public abstract class PlayerAnimLayerMixin {
    @Unique
    private static final long EFW_DROP_WINDOW_NS = 200_000_000L;

    @Shadow(remap = false)
    @Final
    private List<AbstractModifier> modifiers;

    @Shadow(remap = false)
    IAnimation animation;

    @Shadow(remap = false)
    public abstract void addModifierLast(AbstractModifier modifier);

    @Shadow(remap = false)
    public abstract void setAnimation(IAnimation animation);

    @Unique
    private IAnimation efw$dropped;
    @Unique
    private long efw$droppedAt;
    @Unique
    private boolean efw$internal;
    @Unique
    private boolean efw$wasActive;
    /** A walk/run/stand switch waiting to last {@link Locomotion#SETTLE_TICKS} ticks. */
    @Unique
    private IAnimation efw$pendingNext;
    @Unique
    private AbstractFadeModifier efw$pendingFade;
    @Unique
    private int efw$pendingTicks;

    /** A mod clears the layer and then fades into the next animation: the cleared one is what the fade starts from. */
    @Inject(method = "setAnimation", at = @At("HEAD"), remap = false)
    private void efw$rememberDropped(IAnimation next, CallbackInfo ci) {
        if (!efw$internal && next == null && animation != null && animation.isActive()) {
            efw$dropped = animation;
            efw$droppedAt = System.nanoTime();
        }
    }

    @Inject(method = "replaceAnimationWithFade(Ldev/kosmx/playerAnim/api/layered/modifier/AbstractFadeModifier;Ldev/kosmx/playerAnim/api/layered/IAnimation;Z)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void efw$replace(AbstractFadeModifier fade, IAnimation next, boolean fadeFromNothing, CallbackInfo ci) {
        ci.cancel();
        // mods call this every frame (TACZ stops the empty layer and replays the same rotation pose each frame): nothing changes then
        if (next == null && animation == null) {
            return;
        }
        if (efw$sameStart(next)) {
            efw$pendingNext = null; // a walk/run blip that went back before it lasted
            return;
        }
        String nowId = animation instanceof KeyframeAnimationPlayer current && current.isActive() ? Locomotion.id(current.getData()) : null;
        String nextId = next instanceof KeyframeAnimationPlayer wanted ? Locomotion.id(wanted.getData()) : null;
        if (Locomotion.isGait(nowId) && Locomotion.isGait(nextId)) {
            // walking, running and standing switch on the speed of a single tick: shown only once the switch lasts
            if (efw$pendingNext == null || ((KeyframeAnimationPlayer) efw$pendingNext).getData() != ((KeyframeAnimationPlayer) next).getData()) {
                efw$pendingTicks = Locomotion.SETTLE_TICKS;
            }
            efw$pendingFade = fade;
            efw$pendingNext = next;
            return;
        }
        efw$pendingNext = null;
        efw$apply(fade, next, nowId, nextId);
    }

    @Unique
    private void efw$apply(AbstractFadeModifier fade, IAnimation next, String nowId, String nextId) {
        if (nowId != null && nextId != null) {
            if (AnimBlend.fadeLength(fade) < Locomotion.MIN_FADE_TICKS) {
                fade = AbstractFadeModifier.standardFadeIn(Locomotion.MIN_FADE_TICKS, Ease.INOUTSINE);
            }
            if (Locomotion.isCycle(nowId) && Locomotion.isCycle(nextId) && animation instanceof KeyframeAnimationPlayer current) {
                next = Locomotion.continueStep(current, ((KeyframeAnimationPlayer) next).getData());
            }
        }
        IAnimation begin = null;
        // the fades at the end of the list are the transition running now: they become the start of the new one and go on inside it
        int first = modifiers.size();
        while (first > 0 && modifiers.get(first - 1) instanceof AbstractFadeModifier) {
            first--;
        }
        if (first < modifiers.size()) {
            AbstractModifier running = modifiers.get(first);
            if (running.isActive()) {
                begin = running;
                if (AnimBlend.nestedFades(running) >= AnimBlend.MAX_NESTED_FADES) {
                    begin = animation != null && animation.isActive() ? animation : null;
                }
            }
            modifiers.subList(first, modifiers.size()).clear();
        } else if (animation != null && animation.isActive()) {
            begin = animation;
        }
        if (begin == null && efw$dropped != null && efw$dropped.isActive() && System.nanoTime() - efw$droppedAt < EFW_DROP_WINDOW_NS) {
            begin = efw$dropped;
        }
        efw$dropped = null;
        if (begin != null || next != null) {
            // with nothing to start from, the fade starts from the layers under this one
            fade.setBeginAnimation(begin);
            addModifierLast(fade);
        }
        efw$internal = true;
        setAnimation(next);
        efw$internal = false;
    }

    /**
     * The same animation asked for again while it is looping or has only just started: keep it (a restart every frame froze it on
     * its first tick, and every restart was a new transition).
     */
    @Unique
    private boolean efw$sameStart(IAnimation next) {
        if (!(next instanceof KeyframeAnimationPlayer wanted) || !(animation instanceof KeyframeAnimationPlayer current) || !current.isActive()) {
            return false;
        }
        return current.getData() == wanted.getData() && (current.isInfinite() || current.getTick() <= 1);
    }

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void efw$beforeTick(CallbackInfo ci) {
        if (efw$pendingNext != null && --efw$pendingTicks <= 0) {
            IAnimation next = efw$pendingNext;
            efw$pendingNext = null;
            String nowId = animation instanceof KeyframeAnimationPlayer current && current.isActive() ? Locomotion.id(current.getData()) : null;
            efw$apply(efw$pendingFade, next, nowId, Locomotion.id(((KeyframeAnimationPlayer) next).getData()));
        }
        efw$wasActive = animation != null && animation.isActive();
    }

    /** The animation ended by itself this tick: its last frame fades out instead of vanishing. */
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void efw$afterTick(CallbackInfo ci) {
        if (!efw$wasActive || animation == null || animation.isActive()) {
            return;
        }
        if (!modifiers.isEmpty() && modifiers.get(modifiers.size() - 1) instanceof AbstractFadeModifier) {
            return; // a transition is running, it handles the end
        }
        AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn(AnimBlend.END_FADE_TICKS, Ease.INOUTSINE);
        fade.setBeginAnimation(new HeldPose(animation));
        addModifierLast(fade);
        efw$internal = true;
        setAnimation(null);
        efw$internal = false;
    }
}
