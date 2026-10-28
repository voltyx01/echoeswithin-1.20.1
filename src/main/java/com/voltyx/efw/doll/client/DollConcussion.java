package com.voltyx.efw.doll.client;

import com.voltyx.efw.EFW;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * The concussion of Explosion Overhaul (the blur with the colour fringes, the camera sway, the muffled sound and the ringing) for the
 * frenzy of the doll, by reflection (the mod stays optional): it comes in smoothly before the blink and lasts the whole frenzy.
 * Its own config switches (concussion, low pass, deafness, sway) are respected by its effects.
 */
final class DollConcussion {
    private static final String PKG = "com.vinlanx.explosionoverhaul.client.";
    private static final boolean LOADED = ModList.get().isLoaded("explosionoverhaul");
    private static final float BLUR = 0.55F, SWAY = 0.45F, LOW_PASS = 0.6F, DEAFNESS = 0.8F;
    /** The full silence of the ringing, at the blink; the hearing then comes back over Explosion Overhaul's 5 seconds. */
    private static final double SILENT_SECONDS = 1.5;

    private static boolean resolved;
    private static Method blur, sway, lowPass, deafness, stop;

    private DollConcussion() {
    }

    /** Starts the blur, the sway and the muffling; they fade in over 2 seconds and hold for the given time. */
    static void start(int seconds) {
        if (!resolve()) {
            return;
        }
        int s = Math.max(1, seconds);
        call(blur, s, BLUR);
        call(sway, s, SWAY);
        call(lowPass, s, LOW_PASS);
    }

    /** The ringing: the sound cut off for a moment, at the blink. */
    static void ring() {
        if (resolve() && deafness != null) {
            try {
                deafness.invoke(null, DEAFNESS, SILENT_SECONDS, 100.0, "", Math.round(DEAFNESS * 100.0F));
            } catch (ReflectiveOperationException | RuntimeException e) {
                EFW.LOGGER.debug("[Doll] concussion ringing failed: {}", e.toString());
            }
        }
    }

    /** The frenzy got longer (a kill): the concussion holds that much longer, as strong as it is. */
    static void extend(int seconds) {
        if (seconds > 0 && resolve()) {
            call(blur, seconds, 0.0F);
            call(sway, seconds, 0.0F);
            call(lowPass, seconds, 0.0F);
        }
    }

    /** The frenzy ended early (death, respawn): all of it stops. */
    static void stop() {
        if (resolve() && stop != null) {
            try {
                stop.invoke(null);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
    }

    private static void call(Method m, int seconds, float intensity) {
        if (m == null) {
            return;
        }
        try {
            m.invoke(null, seconds, intensity);
        } catch (ReflectiveOperationException | RuntimeException e) {
            EFW.LOGGER.debug("[Doll] concussion {} failed: {}", m.getDeclaringClass().getSimpleName(), e.toString());
        }
    }

    private static synchronized boolean resolve() {
        if (!LOADED) {
            return false;
        }
        if (!resolved) {
            resolved = true;
            blur = find("Blur", "start", int.class, float.class);
            sway = find("CameraShakeConcussionEffect", "start", int.class, float.class);
            lowPass = find("LowPassConcussionEffect", "start", int.class, float.class);
            deafness = find("DeafnessConcussionEffect", "start", float.class, double.class, double.class, String.class, int.class);
            stop = find("ConcussionAudioEffect", "stopAll");
        }
        return blur != null || lowPass != null;
    }

    private static Method find(String cls, String name, Class<?>... args) {
        try {
            return Class.forName(PKG + cls).getMethod(name, args);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            EFW.LOGGER.warn("[Doll] Explosion Overhaul {}.{} not found: {}", cls, name, e.toString());
            return null;
        }
    }
}
