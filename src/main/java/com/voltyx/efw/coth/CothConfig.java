package com.voltyx.efw.coth;

import com.voltyx.efw.config.ConfigRegistry;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Settings of the sickness of the Call of the Hive (Scape and Run: Parasites' COTH effect on the player). Its stage is the level of the
 * effect: SRP raises it by itself, I -> II -> III. Read on the server (the fits of vomiting) and on the client (complaints, the head
 * pulled down, the silhouettes).
 */
public final class CothConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;

    public static final ForgeConfigSpec.BooleanValue REPLICAS;
    public static final ForgeConfigSpec.IntValue REPLICA_MIN;
    public static final ForgeConfigSpec.IntValue REPLICA_MAX;
    public static final ForgeConfigSpec.DoubleValue WARN_REPLICA;

    public static final ForgeConfigSpec.BooleanValue VOMIT;
    public static final ForgeConfigSpec.IntValue VOMIT_STAGE;
    public static final ForgeConfigSpec.IntValue VOMIT_MIN;
    public static final ForgeConfigSpec.IntValue VOMIT_MAX;
    public static final ForgeConfigSpec.IntValue WARN_TICKS;
    public static final ForgeConfigSpec.IntValue VOMIT_TICKS;
    public static final ForgeConfigSpec.BooleanValue FORCE_CROUCH;
    public static final ForgeConfigSpec.DoubleValue HEAD_PITCH;
    public static final ForgeConfigSpec.DoubleValue EXHAUSTION;
    public static final ForgeConfigSpec.DoubleValue DAMAGE;
    public static final ForgeConfigSpec.DoubleValue BLOOD;

    public static final ForgeConfigSpec.BooleanValue PHANTOMS;
    public static final ForgeConfigSpec.IntValue PHANTOM_STAGE;
    public static final ForgeConfigSpec.IntValue PHANTOM_MAX;
    public static final ForgeConfigSpec.IntValue PHANTOM_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue PHANTOM_VANISH;
    public static final ForgeConfigSpec.BooleanValue PHANTOM_FLICKER;
    public static final ForgeConfigSpec.BooleanValue PHANTOM_SOUNDS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ConfigRegistry.section("Call of the Hive");
        b.push("general");
        ENABLED = ConfigRegistry.bool(b, "enabled", true,
                "The Call of the Hive (SRP's COTH effect) makes the character sick: complaints, fits of vomiting blood, silhouettes."
                        + " Amoxicillin cures it and keeps it away for a while.");
        REPLICAS = ConfigRegistry.bool(b, "replicas", true, "The character complains about the sickness (personal replicas).");
        REPLICA_MIN = ConfigRegistry.integer(b, "replicaMinSeconds", 240, 10, 3600,
                "Shortest pause between two complaints (a new stage of the sickness is told anyway, once).");
        REPLICA_MAX = ConfigRegistry.integer(b, "replicaMaxSeconds", 480, 10, 7200, "Longest pause between two complaints.");
        WARN_REPLICA = ConfigRegistry.decimal(b, "warnReplicaChance", 0.35, 0.0, 1.0,
                "Chance that a fit coming is also said aloud (never within a minute after another complaint).");
        b.pop();

        ConfigRegistry.section("Call of the Hive: vomiting");
        b.push("vomiting");
        VOMIT = ConfigRegistry.bool(b, "enabled", true, "Fits of vomiting blood (seen by everyone around).");
        VOMIT_STAGE = ConfigRegistry.integer(b, "fromStage", 2, 1, 3, "Stage of the sickness (level of the effect) from which the fits come.");
        VOMIT_MIN = ConfigRegistry.integer(b, "minSeconds", 150, 20, 3600, "Shortest pause between two fits.");
        VOMIT_MAX = ConfigRegistry.integer(b, "maxSeconds", 330, 20, 7200, "Longest pause between two fits (a third shorter at stage III).");
        WARN_TICKS = ConfigRegistry.integer(b, "warningTicks", 50, 0, 200,
                "How long the character feels it coming (a replica, the view sways) before the fit.");
        VOMIT_TICKS = ConfigRegistry.integer(b, "fitTicks", 80, 20, 300, "How long a fit lasts.");
        FORCE_CROUCH = ConfigRegistry.bool(b, "forceCrouch", true, "The character doubles over (crouches) during a fit.");
        HEAD_PITCH = ConfigRegistry.decimal(b, "headPitch", 38.0, 0.0, 90.0,
                "How far down the head is pulled during a fit, in degrees (0 = the view is left alone).");
        EXHAUSTION = ConfigRegistry.decimal(b, "hungerExhaustion", 6.0, 0.0, 40.0, "Hunger a fit costs (exhaustion, 4 = one food point).");
        DAMAGE = ConfigRegistry.decimal(b, "damage", 1.0, 0.0, 10.0, "Damage of a fit (never kills: not dealt at low health).");
        BLOOD = ConfigRegistry.decimal(b, "bloodOnPlayer", 0.12, 0.0, 1.0, "Blood the fit leaves on the character (the blood system).");
        b.pop();

        ConfigRegistry.section("Call of the Hive: silhouettes");
        b.push("silhouettes");
        PHANTOMS = ConfigRegistry.bool(b, "enabled", true,
                "The sick one glimpses twitching silhouettes of black and white squares (only they see them): behind the back or far off, gone the moment they are seen.");
        PHANTOM_STAGE = ConfigRegistry.integer(b, "fromStage", 3, 1, 3, "Stage of the sickness from which the silhouettes come.");
        PHANTOM_MAX = ConfigRegistry.integer(b, "maxAtOnce", 2, 1, 6, "How many silhouettes can stand around at once.");
        PHANTOM_INTERVAL = ConfigRegistry.integer(b, "averageSeconds", 50, 5, 1800, "Average time until the next silhouette shows up.");
        PHANTOM_VANISH = ConfigRegistry.decimal(b, "vanishDistance", 6.0, 1.0, 20.0,
                "A silhouette melts away when the player comes this close.");
        PHANTOM_FLICKER = ConfigRegistry.bool(b, "fastFlicker", true,
                "The squares change fast (off: slowly, for those sensitive to flashing).");
        PHANTOM_SOUNDS = ConfigRegistry.bool(b, "sounds", true, "A faint sound is heard where a silhouette melted away (sometimes).");
        b.pop();

        SPEC = b.build();
        ConfigRegistry.bind(b, SPEC);
    }

    private CothConfig() {
    }
}
