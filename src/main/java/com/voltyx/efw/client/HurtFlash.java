package com.voltyx.efw.client;

import net.minecraft.Util;

/**
 * When the local player was last really hit. A real hit reaches the client as a damage event (and a hurt animation) together with
 * the lower health; a health drop with neither is no hit (Legendary Survival Overhaul cutting the health down to the max after
 * breaking a heart, up to a second after the blow), so it must not shake the camera as a second one (LocalPlayerHealthCutMixin).
 */
public final class HurtFlash {
    /** The health packet comes in the same server tick as the damage event; this leaves room for a slow connection. */
    private static final long WINDOW_MS = 250L;
    private static long lastHitMs = -1L;

    private HurtFlash() {
    }

    public static void hit() {
        lastHitMs = Util.getMillis();
    }

    public static boolean recentlyHit() {
        return lastHitMs >= 0L && Util.getMillis() - lastHitMs <= WINDOW_MS;
    }
}
