package com.voltyx.efw.compat;

import com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing;
import com.github.exopandora.shouldersurfing.api.client.Perspective;
import com.github.exopandora.shouldersurfing.client.ShoulderSurfing;
import com.voltyx.efw.EFW;
import com.voltyx.efw.client.HandFade;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Smooth shoulder surfing transitions of mwccf (ShoulderInstance), in two steps each way:
 * - entering: shoulder surfing is switched on at once and the camera glides out from the player's eyes ({@link #CLOSE}) to the
 *   configured shoulder offset, the player model fading in with it;
 * - leaving for first person: the camera is pulled back into the head while the player fades out (alpha = (distance / start)^2),
 *   the switch to first person happens when it is in the eyes, and the hands fade in after it.
 * The camera glide itself is SSR's own offset lerp; efw only sets the target offset and delays the perspective change.
 * Only touched through the SSR mixins and a guarded tick handler, so it never loads without Shoulder Surfing.
 */
public final class SsrTransition {
    /** Offset of a camera sitting in the eyes: close enough that the switch to first person does not jump. */
    public static final Vec3 CLOSE = new Vec3(0.0, 0.0, -0.01);
    private static final double ARRIVED_DISTANCE = 0.03;
    private static final int MIN_TICKS = 2;
    private static final int MAX_TICKS = 12;
    private static final long HANDS_IN_MS = 140L;
    /** Ticks after entering during which the camera glides out at the doubled speed. */
    private static final int GLIDE_OUT_TICKS = 12;

    private static boolean exiting;
    private static boolean lookAtCrosshair;
    private static int exitTicks;
    private static double startDistance = 1.0;
    private static boolean immediate;
    private static int glideOutTicks;

    private SsrTransition() {
    }

    public static boolean isExiting() {
        return exiting;
    }

    /** The camera is gliding into the head or out to the shoulder: SSR's camera takes two smoothing steps per tick then. */
    public static boolean isGliding() {
        return exiting || glideOutTicks > 0;
    }

    /** Runs a perspective change without transitions (terminal, safe, bodycam picture). */
    public static void runImmediate(Runnable action) {
        boolean prev = immediate;
        immediate = true;
        try {
            action.run();
        } finally {
            immediate = prev;
        }
    }

    /**
     * Called before SSR changes the perspective; true cancels the change.
     * Entering shoulder surfing from first person waits for the hands to fade out; leaving it for first person is replaced by the
     * pull-back. Any other change aborts a running transition.
     */
    public static boolean interceptChange(Perspective perspective, boolean lookAtCrosshairTarget) {
        if (exiting) {
            if (perspective == Perspective.FIRST_PERSON) {
                return true; // temporary first person asks every frame while aiming
            }
            exiting = false;
            return false;
        }
        if (immediate) {
            HandFade.set(1.0f);
            return false;
        }
        IShoulderSurfing ss = IShoulderSurfing.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getCameraEntity() != mc.player || mc.level == null) {
            return false;
        }
        if (perspective == Perspective.SHOULDER_SURFING && !ss.isShoulderSurfing() && mc.options.getCameraType().isFirstPerson()) {
            // switched on at once; the camera glides out of the eyes and the model fades in with it
            HandFade.set(1.0f);
            glideOutTicks = GLIDE_OUT_TICKS;
            return false;
        }
        if (perspective != Perspective.FIRST_PERSON || !ss.isShoulderSurfing()) {
            return false;
        }
        exiting = true;
        exitTicks = 0;
        lookAtCrosshair = lookAtCrosshairTarget;
        startDistance = Math.max(0.3, ss.getCamera().getOffset().length());
        return true;
    }

    /** Target camera offset while pulling back. */
    public static Vec3 exitOffset() {
        return CLOSE;
    }

    /**
     * Player model alpha during the glides: (camera distance / shoulder distance)^2. The pull-back fades the player out, the glide out
     * fades it in (the first frames had the camera inside the head, showing the model from within).
     */
    public static float glideAlpha() {
        IShoulderSurfing ss = IShoulderSurfing.getInstance();
        double distance = ss.getCamera().getRenderOffset().length();
        double full = exiting ? startDistance : Math.max(0.3, ss.getCamera().getTargetOffset().length());
        float a = (float) Mth.clamp(distance / full, 0.0, 1.0);
        return a * a;
    }

    /**
     * The camera of shoulder surfing turns on its own (decoupled camera), first person looks where the player looks: the player is
     * turned to the camera before the switch, so the view does not jump. The model is invisible at that moment.
     */
    private static void alignPlayerToCamera(Minecraft mc, IShoulderSurfing ss) {
        float yaw = ss.getCamera().getYRot();
        float pitch = ss.getCamera().getXRot();
        var p = mc.player;
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
        p.yHeadRot = yaw;
        p.yHeadRotO = yaw;
        p.yBob = yaw;
        p.yBobO = yaw;
        p.xBob = pitch;
        p.xBobO = pitch;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (glideOutTicks > 0) {
            glideOutTicks--;
        }
        if (!exiting) {
            return;
        }
        IShoulderSurfing ss = IShoulderSurfing.getInstance();
        if (mc.player == null || mc.level == null || !ss.isShoulderSurfing()) {
            exiting = false;
            return;
        }
        exitTicks++;
        double distance = ss.getCamera().getOffset().length();
        if (exitTicks >= MAX_TICKS || (exitTicks >= MIN_TICKS && distance <= ARRIVED_DISTANCE)) {
            exiting = false;
            alignPlayerToCamera(mc, ss);
            runImmediate(() -> ShoulderSurfing.getInstance().changePerspective(Perspective.FIRST_PERSON, false));
            HandFade.set(0.0f);
            HandFade.fadeTo(1.0f, HANDS_IN_MS);
            EFW.LOGGER.debug("[SSR] pull-back done after {} ticks", exitTicks);
        }
    }
}
