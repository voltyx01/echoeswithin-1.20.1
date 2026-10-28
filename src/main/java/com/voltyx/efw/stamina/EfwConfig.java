package com.voltyx.efw.stamina;

import com.voltyx.efw.config.ConfigRegistry;
import net.minecraftforge.common.ForgeConfigSpec;

import static com.voltyx.efw.config.ConfigRegistry.bool;
import static com.voltyx.efw.config.ConfigRegistry.decimal;
import static com.voltyx.efw.config.ConfigRegistry.integer;

/**
 * Port of the 1.12 MwccfConfig "dash_and_stamina" section: stamina, low stamina effects, roll (dash) and the HUD offsets.
 * COMMON (efw-stamina.toml) is read by the server, CLIENT (efw-stamina-client.toml) only holds HUD options.
 */
public final class EfwConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    // stamina
    public static final ForgeConfigSpec.IntValue MAX_STAMINA;
    public static final ForgeConfigSpec.IntValue SPRINT_DRAIN_INTERVAL;
    public static final ForgeConfigSpec.IntValue JUMP_COST;
    public static final ForgeConfigSpec.IntValue JUMP_REGEN_DELAY;
    public static final ForgeConfigSpec.IntValue SPRINT_REGEN_DELAY;
    public static final ForgeConfigSpec.IntValue REGEN_INTERVAL_NORMAL;
    public static final ForgeConfigSpec.IntValue REGEN_INTERVAL_BOOST;
    public static final ForgeConfigSpec.IntValue STAMINA_DRAIN_AMOUNT;
    public static final ForgeConfigSpec.IntValue STAMINA_REGEN_AMOUNT;
    public static final ForgeConfigSpec.BooleanValue LIMIT_BY_FOOD;

    // low stamina effects
    public static final ForgeConfigSpec.IntValue SLOWNESS_DURATION;
    public static final ForgeConfigSpec.IntValue SLOWNESS_AMPLIFIER;
    public static final ForgeConfigSpec.IntValue FATIGUE_DURATION;
    public static final ForgeConfigSpec.IntValue FATIGUE_AMPLIFIER;
    public static final ForgeConfigSpec.IntValue LOW_WEAKNESS_DURATION;
    public static final ForgeConfigSpec.IntValue LOW_WEAKNESS_AMPLIFIER;

    // dash / roll
    public static final ForgeConfigSpec.DoubleValue DASH_STAMINA_COST;
    public static final ForgeConfigSpec.IntValue DASH_REGEN_DELAY;
    public static final ForgeConfigSpec.DoubleValue DASH_SPEED;
    public static final ForgeConfigSpec.IntValue DASH_POST_TIMER_TICKS;
    public static final ForgeConfigSpec.IntValue DASH_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue PARTICLE_COUNT;
    public static final ForgeConfigSpec.DoubleValue PARTICLE_SPREAD;
    public static final ForgeConfigSpec.DoubleValue PARTICLE_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue PARTICLE_SPEED;
    public static final ForgeConfigSpec.DoubleValue PARTICLE_Y_OFFSET;
    public static final ForgeConfigSpec.DoubleValue SOUND_VOLUME;
    public static final ForgeConfigSpec.DoubleValue SOUND_PITCH;
    public static final ForgeConfigSpec.IntValue RESISTANCE_LEVEL;
    public static final ForgeConfigSpec.IntValue RESISTANCE_DURATION;
    public static final ForgeConfigSpec.IntValue WEAKNESS_LEVEL;
    public static final ForgeConfigSpec.IntValue WEAKNESS_DURATION;
    public static final ForgeConfigSpec.IntValue ATTACK_EXTRA_STAMINA;
    public static final ForgeConfigSpec.BooleanValue ATTACK_EXTRA_COOLDOWN;
    public static final ForgeConfigSpec.BooleanValue ALLOW_DASH_IN_LIQUIDS;

    // HUD (client)
    public static final ForgeConfigSpec.BooleanValue USE_ALTERNATIVE_HUD;
    public static final ForgeConfigSpec.IntValue OVERLAY_BASE_X;
    public static final ForgeConfigSpec.IntValue OVERLAY_BASE_Y;
    public static final ForgeConfigSpec.IntValue ALT_OVERLAY_BASE_X;
    public static final ForgeConfigSpec.IntValue ALT_OVERLAY_BASE_Y;
    public static final ForgeConfigSpec.IntValue DASH_BAR_BASE_X;
    public static final ForgeConfigSpec.IntValue DASH_BAR_BASE_Y;
    public static final ForgeConfigSpec.IntValue ALT_DASH_BAR_OFFSET_Y;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ConfigRegistry.section("Stamina");
        b.push("stamina");
        MAX_STAMINA = integer(b, "maxStamina", 20, 1, 1000, "Maximum stamina (the default HUD shows 2 points per icon).");
        SPRINT_DRAIN_INTERVAL = integer(b, "sprintDrainInterval", 25, 1, 1200, "Ticks of sprinting per stamina drain step.");
        JUMP_COST = integer(b, "jumpCost", 0, 0, 1000, "Stamina taken by a jump.");
        JUMP_REGEN_DELAY = integer(b, "jumpRegenDelay", 0, 0, 1200, "Ticks without regeneration after a jump.");
        SPRINT_REGEN_DELAY = integer(b, "sprintRegenDelay", 20, 0, 1200, "Ticks without regeneration after sprinting.");
        REGEN_INTERVAL_NORMAL = integer(b, "regenIntervalNormal", 6, 1, 1200, "Ticks per regeneration step.");
        REGEN_INTERVAL_BOOST = integer(b, "regenIntervalBoost", 4, 1, 1200, "Ticks per regeneration step with Energy Boost.");
        STAMINA_DRAIN_AMOUNT = integer(b, "staminaDrainAmount", 1, 0, 1000, "Stamina per sprint drain step.");
        STAMINA_REGEN_AMOUNT = integer(b, "staminaRegenAmount", 1, 0, 1000, "Stamina per regeneration step.");
        LIMIT_BY_FOOD = bool(b, "limitByFood", true, "Cap stamina by the food level (6 or less food: almost nothing; Energy Boost ignores it).");
        b.pop();
        ConfigRegistry.section("Low stamina effects");
        b.push("stamina_effects");
        SLOWNESS_DURATION = integer(b, "slownessDuration", 10, 0, 1200, "Ticks of slowness while exhausted.");
        SLOWNESS_AMPLIFIER = integer(b, "slownessAmplifier", 2, 0, 255, "Slowness level while exhausted (0 = level I).");
        FATIGUE_DURATION = integer(b, "fatigueDuration", 10, 0, 1200, "Ticks of mining fatigue while exhausted.");
        FATIGUE_AMPLIFIER = integer(b, "fatigueAmplifier", 0, 0, 255, "Mining fatigue level while exhausted.");
        LOW_WEAKNESS_DURATION = integer(b, "weaknessDuration", 10, 0, 1200, "Ticks of weakness while exhausted.");
        LOW_WEAKNESS_AMPLIFIER = integer(b, "weaknessAmplifier", 0, 0, 255, "Weakness level while exhausted.");
        b.pop();
        ConfigRegistry.section("Roll (dash)");
        b.push("dash");
        DASH_STAMINA_COST = decimal(b, "staminaCost", 4.0, 0.0, 1000.0, "Stamina taken by a roll.");
        DASH_REGEN_DELAY = integer(b, "staminaRegenDelay", 20, 0, 1200, "Ticks without regeneration after a roll.");
        DASH_SPEED = decimal(b, "speed", 1.0, 0.0, 10.0, "Roll distance multiplier.");
        DASH_POST_TIMER_TICKS = integer(b, "postTimerTicks", 10, 0, 1200, "Ticks after the roll in which the next attack costs extra stamina.");
        DASH_COOLDOWN_TICKS = integer(b, "cooldownTicks", 20, 0, 1200, "Ticks before the next roll.");
        PARTICLE_COUNT = integer(b, "particleCount", 15, 0, 1000, "Dust particles of a roll.");
        PARTICLE_SPREAD = decimal(b, "particleSpread", 0.3, 0.0, 10.0, "Horizontal spread of the dust.");
        PARTICLE_HEIGHT = decimal(b, "particleHeight", 0.1, 0.0, 10.0, "Height of the dust.");
        PARTICLE_SPEED = decimal(b, "particleSpeed", 0.05, 0.0, 10.0, "Speed of the dust.");
        PARTICLE_Y_OFFSET = decimal(b, "particleYOffset", 0.1, -10.0, 10.0, "Vertical offset of the dust.");
        SOUND_VOLUME = decimal(b, "soundVolume", 1.0, 0.0, 10.0, "Roll sound volume.");
        SOUND_PITCH = decimal(b, "soundPitch", 1.0, 0.1, 10.0, "Roll sound pitch.");
        RESISTANCE_LEVEL = integer(b, "resistanceLevel", 2, 0, 255, "Resistance level during the roll (0 = level I).");
        RESISTANCE_DURATION = integer(b, "resistanceDuration", 10, 0, 1200, "Resistance duration in ticks.");
        WEAKNESS_LEVEL = integer(b, "weaknessLevel", 1, 0, 255, "Weakness level during the roll (0 = level I).");
        WEAKNESS_DURATION = integer(b, "weaknessDuration", 10, 0, 1200, "Weakness duration in ticks.");
        ATTACK_EXTRA_STAMINA = integer(b, "attackExtraStamina", 2, 0, 1000, "Stamina taken by an attack made right after a roll.");
        ATTACK_EXTRA_COOLDOWN = bool(b, "attackExtraCooldown", false, "Attacking right after a roll restarts the roll cooldown.");
        ALLOW_DASH_IN_LIQUIDS = bool(b, "allowDashInLiquids", false, "Allow rolling while in water or lava.");
        b.pop();
        COMMON_SPEC = b.build();
        ConfigRegistry.bind(b, COMMON_SPEC);

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        ConfigRegistry.section("HUD");
        c.push("overlay");
        USE_ALTERNATIVE_HUD = bool(c, "useAlternativeStaminaHUD", false, "Alternative stamina bar in place of the XP bar.");
        OVERLAY_BASE_X = integer(c, "overlayBaseX", 0, -2000, 2000, "Stamina bar horizontal offset.");
        OVERLAY_BASE_Y = integer(c, "overlayBaseY", 0, -2000, 2000, "Stamina bar vertical offset.");
        ALT_OVERLAY_BASE_X = integer(c, "altOverlayBaseX", 0, -2000, 2000, "Alternative stamina bar horizontal offset.");
        ALT_OVERLAY_BASE_Y = integer(c, "altOverlayBaseY", 0, -2000, 2000, "Alternative stamina bar vertical offset.");
        DASH_BAR_BASE_X = integer(c, "dashBarBaseX", 0, -2000, 2000, "Roll cooldown bar horizontal offset.");
        DASH_BAR_BASE_Y = integer(c, "dashBarBaseY", 0, -2000, 2000, "Roll cooldown bar vertical offset.");
        ALT_DASH_BAR_OFFSET_Y = integer(c, "altDashBarOffsetY", 0, -2000, 2000, "Alternative roll cooldown bar vertical offset.");
        c.pop();
        CLIENT_SPEC = c.build();
        ConfigRegistry.bind(c, CLIENT_SPEC);
    }

    private EfwConfig() {
    }
}
