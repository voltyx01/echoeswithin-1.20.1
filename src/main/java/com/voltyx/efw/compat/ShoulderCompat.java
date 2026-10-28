package com.voltyx.efw.compat;

import net.minecraftforge.fml.ModList;

/** Shoulder Surfing Reloaded is optional: its API is only touched after a loaded check. */
public final class ShoulderCompat {
    private static final boolean LOADED = ModList.get().isLoaded("shouldersurfing");

    private ShoulderCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static boolean isShoulderSurfing() {
        return LOADED && Impl.isShoulderSurfing();
    }

    public static boolean isAiming() {
        return LOADED && Impl.isAiming();
    }

    /** Camera rotation as {yaw, pitch}, or null when shoulder surfing is not active. */
    public static float[] cameraRotation() {
        return LOADED ? Impl.cameraRotation() : null;
    }

    /** Current camera offset (x to the side, y up) for the lock-on parallax correction. */
    public static net.minecraft.world.phys.Vec3 cameraOffset() {
        return LOADED ? Impl.cameraOffset() : net.minecraft.world.phys.Vec3.ZERO;
    }

    /** Runs a camera type change without the smooth shoulder transitions (terminal, safe, bodycam picture). */
    public static void runImmediate(Runnable action) {
        if (LOADED) {
            SsrTransition.runImmediate(action);
        } else {
            action.run();
        }
    }

    /**
     * Shoulder Surfing's own perspective switch: FIRST_PERSON, THIRD_PERSON_BACK, THIRD_PERSON_FRONT or SHOULDER_SURFING. Not
     * immediate, it glides like the key does (SsrTransition).
     */
    public static void changePerspective(String perspective) {
        if (LOADED) {
            Impl.changePerspective(perspective);
        }
    }

    /** The camera is gliding into the head or out to the shoulder (SsrTransition). */
    public static boolean isGliding() {
        return LOADED && SsrTransition.isGliding();
    }

    /** Back to shoulder surfing at once (a perspective switched away from it is being restored). */
    public static void enterShoulderSurfing() {
        if (LOADED) {
            SsrTransition.runImmediate(Impl::enterShoulderSurfing);
        }
    }

    public static void setCameraRotation(float yaw, float pitch) {
        if (LOADED) {
            Impl.setCameraRotation(yaw, pitch);
        }
    }

    private static final class Impl {
        static boolean isShoulderSurfing() {
            return com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing.getInstance().isShoulderSurfing();
        }

        static boolean isAiming() {
            return com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing.getInstance().isAiming();
        }

        static float[] cameraRotation() {
            com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing s = com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing.getInstance();
            if (!s.isShoulderSurfing()) {
                return null;
            }
            return new float[] {s.getCamera().getYRot(), s.getCamera().getXRot()};
        }

        static net.minecraft.world.phys.Vec3 cameraOffset() {
            return com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing.getInstance().getCamera().getOffset();
        }

        static void changePerspective(String perspective) {
            com.github.exopandora.shouldersurfing.client.ShoulderSurfing.getInstance()
                    .changePerspective(com.github.exopandora.shouldersurfing.api.client.Perspective.valueOf(perspective), false);
        }

        static void enterShoulderSurfing() {
            com.github.exopandora.shouldersurfing.client.ShoulderSurfing.getInstance()
                    .changePerspective(com.github.exopandora.shouldersurfing.api.client.Perspective.SHOULDER_SURFING, false);
        }

        static void setCameraRotation(float yaw, float pitch) {
            com.github.exopandora.shouldersurfing.api.client.IShoulderSurfingCamera c = com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing.getInstance().getCamera();
            c.setYRot(yaw);
            c.setXRot(pitch);
        }
    }
}
