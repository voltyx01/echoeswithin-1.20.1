package com.voltyx.efw.client.anim;

import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * The movement animations of Serious Player Animations (walk, run, stand, fall). It picks one every tick from the raw speed of that
 * tick: walking and running are split at a fixed speed, so a sprint near that speed (slowed by an injury or the armour, a step, a
 * turn) switched between them every few ticks, the run came in with a 2 tick fade, and every switch started the step cycle from
 * its first frame. PlayerAnimLayerMixin uses this to hold a switch until it lasts, to fade at least {@link #MIN_FADE_TICKS} and to
 * carry the step on from where the legs are.
 */
public final class Locomotion {
    private static final String MOD = "seriousplayeranimations";
    /** Walking, running and standing: a switch between them must last this long before it is shown. */
    private static final Set<String> GAIT = Set.of("walking", "walking_backwards", "running", "idle_standing", "turn_left", "turn_right");
    /** Step cycles: one goes on into the other at the same point of the step. */
    private static final Set<String> CYCLES = Set.of("walking", "walking_backwards", "running");
    private static final Set<String> MOVEMENT = Set.of("walking", "walking_backwards", "running", "idle_standing", "turn_left", "turn_right",
            "falling", "walking_sneak", "walking_sneak_backwards", "idle_sneak");

    public static final int SETTLE_TICKS = 3;
    public static final int MIN_FADE_TICKS = 6;

    private Locomotion() {
    }

    /** The Serious Player Animations movement id of this animation, or null for anything else. */
    public static String id(KeyframeAnimation data) {
        if (data == null) {
            return null;
        }
        for (String id : MOVEMENT) {
            if (PlayerAnimationRegistry.getAnimation(new ResourceLocation(MOD, id)) == data) {
                return id;
            }
        }
        return null;
    }

    public static boolean isGait(String id) {
        return id != null && GAIT.contains(id);
    }

    public static boolean isCycle(String id) {
        return id != null && CYCLES.contains(id);
    }

    /** The next step cycle, started at the point of the step the current one has reached. */
    public static KeyframeAnimationPlayer continueStep(KeyframeAnimationPlayer current, KeyframeAnimation next) {
        KeyframeAnimation now = current.getData();
        float phase = phase(now, current.getTick());
        int loop = next.endTick - next.returnToTick;
        if (phase < 0.0F || loop <= 0) {
            return new KeyframeAnimationPlayer(next);
        }
        return new KeyframeAnimationPlayer(next, next.returnToTick + Math.round(phase * loop));
    }

    /** 0..1 inside the loop of the step, -1 when it cannot be told. */
    private static float phase(KeyframeAnimation data, int tick) {
        int start = Math.max(0, data.returnToTick);
        int loop = data.endTick - start;
        if (loop <= 0 || tick < 0) {
            return -1.0F;
        }
        int t = Math.floorMod(tick - start, loop);
        return t / (float) loop;
    }
}
