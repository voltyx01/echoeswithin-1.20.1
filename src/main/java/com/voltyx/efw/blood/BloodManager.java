package com.voltyx.efw.blood;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side blood contamination (0.0 .. 1.0) of every player (port of the 1.12 BloodManager). The level is synced to the
 * player and to everyone who tracks them; the client turns it into stains on the skin and the armour textures.
 */
public final class BloodManager {
    public static final float MELEE_ATTACKER_GAIN = 0.025f; // the player hit someone in melee (~40 hits to the maximum)
    public static final float MELEE_VICTIM_GAIN = 0.02f;    // the player took damage
    public static final float BYSTANDER_GAIN = 0.01f;       // splash from a melee hit on someone nearby

    /** Dry decay: clean in about 6 minutes. */
    private static final float DECAY_PER_TICK = 1.0f / (360 * 20);
    /** Water and rain wash the blood off 180 times faster (about 2 seconds). */
    private static final float WATER_DECAY_MUL = 180.0f;
    /** Blood does not start to fade for 40 seconds after the last gain. */
    private static final long DECAY_DELAY_MS = 40_000L;

    private static final Map<UUID, Float> LEVELS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_GAIN = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_MELEE = new ConcurrentHashMap<>();

    private BloodManager() {
    }

    public static float getBloodLevel(UUID id) {
        Float f = LEVELS.get(id);
        return f == null ? 0.0f : f;
    }

    public static void addBlood(UUID id, float amount) {
        LAST_GAIN.put(id, System.currentTimeMillis());
        LEVELS.merge(id, amount, (old, delta) -> Math.min(1.0f, old + delta));
    }

    public static void setBloodLevel(UUID id, float level) {
        LAST_GAIN.put(id, System.currentTimeMillis());
        LEVELS.put(id, Math.max(0.0f, Math.min(1.0f, level)));
    }

    public static void gain(Player player, float amount) {
        if (player != null && amount > 0.0001f) {
            addBlood(player.getUUID(), amount * (float) com.voltyx.efw.sins.stat.SinStats.mult(player, com.voltyx.efw.sins.stat.Stat.BLOOD_GAIN));
        }
    }

    /** Melee gain of the attacker with a 150 ms debounce: the attack event and the hurt event both report the same hit. */
    public static void onMeleeAttack(Player player, float amount) {
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        Long last = LAST_MELEE.get(id);
        if (last != null && now - last < 150) {
            return;
        }
        LAST_MELEE.put(id, now);
        gain(player, amount);
    }

    /** One server tick of fading for a player; the entry disappears once clean. */
    public static void tickDecay(Player player) {
        UUID id = player.getUUID();
        Float cur = LEVELS.get(id);
        if (cur == null || cur <= 0.0f) {
            return;
        }
        boolean wash = player.isInWaterOrRain();
        if (!wash) {
            Long lastGain = LAST_GAIN.get(id);
            if (lastGain != null && System.currentTimeMillis() - lastGain < DECAY_DELAY_MS) {
                return;
            }
        }
        float next = cur - DECAY_PER_TICK * (wash ? WATER_DECAY_MUL : 1.0f);
        if (next <= 0.0f) {
            LEVELS.remove(id);
            LAST_GAIN.remove(id);
        } else {
            LEVELS.put(id, next);
        }
    }

    public static void clear(UUID id) {
        LEVELS.remove(id);
        LAST_GAIN.remove(id);
        LAST_MELEE.remove(id);
    }
}
