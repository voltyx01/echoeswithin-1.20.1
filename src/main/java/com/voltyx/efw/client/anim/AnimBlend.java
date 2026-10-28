package com.voltyx.efw.client.anim;

import dev.kosmx.playerAnim.api.layered.AnimationContainer;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;

/**
 * Helpers for the fixes of the player animation library (the PlayerAnim* mixins).
 *
 * <p>The library keeps every rotation inside -PI..PI and blends the three Euler angles one by one in a straight line. When one
 * pose has an arm at 170 degrees and the other at -170, the straight line goes the long way through zero: the arm swings 340
 * degrees round the back instead of 20 degrees over the top. Rotations are blended along the shorter way here.
 */
public final class AnimBlend {
    /** Ticks of the fade-out an animation gets when it simply ends (the library dropped it at once). */
    public static final int END_FADE_TICKS = 5;

    private AnimBlend() {
    }

    /** Implemented by the fade modifier: how much "some animation" is in its output (0 = only the layers under it). */
    public interface Weighted {
        float efw$contentWeight(float tickDelta);

        /** What the fade starts from. */
        IAnimation efw$begin();

        /** Ticks the fade takes. */
        int efw$length();
    }

    /** Ticks a fade takes (0 when it cannot be told). */
    public static int fadeLength(AbstractFadeModifier fade) {
        return fade instanceof Weighted w ? w.efw$length() : 0;
    }

    /** Transitions nested in each other at most this deep; deeper ones start from the plain animation. */
    public static final int MAX_NESTED_FADES = 3;

    /** How many fades are nested through the start points of {@code a}. */
    public static int nestedFades(IAnimation a) {
        int depth = 0;
        for (int guard = 0; a instanceof Weighted fade && guard < 32; guard++) {
            depth++;
            a = fade.efw$begin();
        }
        return depth;
    }

    public static float wrap(float angle) {
        float twoPi = (float) (Math.PI * 2.0);
        angle %= twoPi;
        if (angle > Math.PI) {
            angle -= twoPi;
        } else if (angle < -Math.PI) {
            angle += twoPi;
        }
        return angle;
    }

    /** Blend of two angles along the shorter way round. */
    public static float angleLerp(float from, float to, float alpha) {
        return from + wrap(to - from) * alpha;
    }

    public static float clamp01(float v) {
        return Float.isNaN(v) ? 1.0f : Math.max(0.0f, Math.min(1.0f, v));
    }

    /** The product of the fades below a modifier: an adjustment on top of a layer should vanish with the layer, not after it. */
    public static float chainWeight(IAnimation start, float tickDelta) {
        float w = 1.0f;
        IAnimation a = start;
        for (int guard = 0; a instanceof AnimationContainer<?> c && guard < 16; guard++) {
            if (a instanceof AbstractFadeModifier && a instanceof Weighted fade) {
                w *= fade.efw$contentWeight(tickDelta);
            }
            a = c.getAnim();
        }
        return w;
    }
}
