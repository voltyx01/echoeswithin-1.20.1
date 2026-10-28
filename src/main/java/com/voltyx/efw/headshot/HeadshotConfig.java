package com.voltyx.efw.headshot;

import com.voltyx.efw.config.ConfigRegistry;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * efw-headshots.toml: the 1.12 mwccf "headshots" settings with the same names, units and line format. The defaults are the
 * values tuned in mwccf (run/config/mwccf_config.cfg), so lines can be copied between the two files as they are.
 */
public final class HeadshotConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue BASE_RADIUS;
    public static final ForgeConfigSpec.DoubleValue HUMANOID_Y_OFFSET;
    public static final ForgeConfigSpec.DoubleValue QUADRUPED_Y_OFFSET_MULT;
    public static final ForgeConfigSpec.DoubleValue QUADRUPED_Y_OFFSET_ADD;
    public static final ForgeConfigSpec.DoubleValue QUADRUPED_FORWARD_OFFSET_MULT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CUSTOM_MOBS;
    public static final ForgeConfigSpec.BooleanValue TACZ_USE_GUN_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue LSO_SUPPRESS_OWN_HEADSHOT;
    public static final ForgeConfigSpec.BooleanValue DEBUG_HITBOXES;
    public static final ForgeConfigSpec.DoubleValue RING_HEAD_SCALE;

    /** Tuned in mwccf; a later line for the same mob wins, like in 1.12. */
    private static final List<String> TUNED_MOBS = List.of(
            "srparasites:ada_summoner, 1.6, 1.75, 0.4",
            "srparasites:sim_bigspider, 0.45, 1.6, 0.2",
            "srparasites:sim_bear, 0.0, 1.0, 0.3",
            "srparasites:sim_cow, 0.75, 1.3, 0.25",
            "srparasites:sim_sheep, 0.70, 1.2, 0.20",
            "srparasites:sim_wolf, 0.55, 0.7, 0.2",
            "srparasites:sim_pig, 0.65, 0.9, 0.3",
            "srparasites:sim_horse, 0.7, 1.7, 0.35",
            "srparasites:sim_adventurer, 0.0, 2.0, 0.25",
            "srparasites:fer_bear, 0.0, 1.35, 0.3",
            "srparasites:fer_human, 0.0, 1.7, 0.25",
            "srparasites:fer_cow, 0.45, 1.3, 0.20",
            "srparasites:fer_horse, 0.7, 1.7, 0.30",
            "srparasites:fer_pig, 0.65, 1.1, 0.25",
            "srparasites:fer_sheep, 0.70, 1.2, 0.20",
            "srparasites:fer_villager, 0.0, 2.0, 0.4",
            "srparasites:fer_wolf, 0.55, 0.7, 0.2",
            "srparasites:mar_cow, 0.7, 1.4, 0.4",
            "srparasites:mar_villager, 0.0, 2.5, 0.37",
            "srparasites:mar_human, 0.65, 2.0, 0.3",
            "srparasites:mar_sheep, 0.5, 2.8, 0.3",
            "srparasites:mar_bear, 0.0, 1.0, 0.3",
            "srparasites:heed, 1.55, 0.9, 0.3",
            "srparasites:crux, 0.9, 2.7, 0.3",
            "srparasites:dredge, 0.05, 3.4, 0.3",
            "srparasites:ada_longarms, 0.54, 2.93, 0.27",
            "srparasites:ada_summoner, 1.62, 1.75, 0.21",
            "srparasites:ada_reeker, 1.60, 2.47, 0.31",
            "srparasites:ada_manducater, 1.10, 1.59, 0.25",
            "srparasites:ada_yelloweye, 0.34, 2.07, 0.29",
            "srparasites:pri_tozoon, 1.04, 0.46, 0.25",
            "srparasites:ada_vermin, -0.38, 0.50, 0.31",
            "srparasites:ada_bolster, 0.80, 1.96, 0.35",
            "srparasites:ada_viscera, 0.50, 2.77, 0.33",
            "srparasites:ada_arachnida, 1.26, 1.42, 0.35",
            "srparasites:beckon_siii, 0.00, 5.41, 0.37",
            "srparasites:beckon_siv, 0.04, 8.09, 0.77",
            "srparasites:kyphosis, 0.00, 4.17, 0.05",
            "srparasites:seizer, -0.02, -0.95, 0.05",
            "srparasites:dispatcher_si, -0.00, 2.63, 0.31",
            "srparasites:dispatcher_sii, -0.86, 1.02, 0.05",
            "srparasites:dispatcher_siii, 0.10, 2.40, 0.05",
            "srparasites:dispatcher_siv, 0.86, 8.91, 0.77",
            "srparasites:rooterball, 0.02, 0.66, 0.05",
            "srparasites:rooter_sii, 0.00, 4.46, 1.13",
            "srparasites:rooter_si, 0.00, 2.16, 0.89",
            "srparasites:rooter_siii, 0.00, 6.82, 1.59",
            "srparasites:overseer, 3.52, 1.26, 0.43",
            "srparasites:warden, 0.28, 3.58, 0.33",
            "srparasites:marauder, 0.44, 4.04, 0.39",
            "srparasites:monarch, 1.08, 3.69, 0.25",
            "srparasites:grunt, 0.14, 1.80, 0.23",
            "srparasites:vigilante, 0.00, 1.27, 0.25",
            "srparasites:bomber_heavy, 0.00, 0.20, 0.69",
            "srparasites:carrier_colony, 0.98, 3.10, 0.35",
            "srparasites:haunter, 0.02, 6.24, 0.05",
            "srparasites:bogle, 2.60, 0.50, 0.55",
            "srparasites:wraith, 2.36, 0.80, 0.45",
            "srparasites:draconite, 5.16, 3.42, 1.05",
            "srparasites:kirin, 2.32, 8.45, 0.39",
            "srparasites:anc_dreadnaut, 2.94, 2.02, 0.43",
            "srparasites:anc_overlord, 0.00, 5.81, 0.05",
            "srparasites:ada_longarms, 0.54, 2.93, 0.27",
            "srparasites:ada_summoner, 1.62, 1.75, 0.21",
            "srparasites:ada_reeker, 1.60, 2.47, 0.31",
            "srparasites:ada_manducater, 1.10, 1.59, 0.25",
            "srparasites:ada_yelloweye, 0.34, 2.07, 0.29",
            "srparasites:pri_tozoon, 1.04, 0.46, 0.25",
            "srparasites:ada_vermin, -0.38, 0.50, 0.31",
            "srparasites:ada_bolster, 0.80, 1.96, 0.35",
            "srparasites:ada_viscera, 0.50, 2.77, 0.33",
            "srparasites:ada_arachnida, 1.26, 1.42, 0.35",
            "srparasites:beckon_siii, 0.00, 5.41, 0.37",
            "srparasites:beckon_siv, 0.04, 8.09, 0.77",
            "srparasites:kyphosis, 0.00, 4.17, 0.05",
            "srparasites:seizer, -0.02, -0.95, 0.05",
            "srparasites:dispatcher_si, -0.00, 2.63, 0.31",
            "srparasites:dispatcher_sii, -0.86, 1.02, 0.05",
            "srparasites:dispatcher_siii, 0.10, 2.40, 0.05",
            "srparasites:dispatcher_siv, 0.86, 8.91, 0.77",
            "srparasites:rooter_si, 0.00, 1.70, 0.09");

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ConfigRegistry.section("Headshots");
        b.comment("Настройки системы хэдшотов (порт mwccf)").push("headshots");
        b.comment("Настройки для мобов, которые вычисляются автоматически").push("general");
        DAMAGE_MULTIPLIER = ConfigRegistry.decimal(b, "damageMultiplier", 2.5, 1.0, 100.0, "Урон будет умножаться на это число");
        BASE_RADIUS = ConfigRegistry.decimal(b, "baseRadius", 0.25, 0.01, 10.0, "Базовый множитель размера хитбокса головы");
        HUMANOID_Y_OFFSET = ConfigRegistry.decimal(b, "humanoidYOffset", 0.1, -10.0, 10.0, "[Гуманоиды] Смещение по Y (прибавляется к высоте глаз)");
        QUADRUPED_Y_OFFSET_MULT = ConfigRegistry.decimal(b, "quadrupedYOffsetMult", 0.6, -10.0, 10.0, "[Четвероногие] Смещение по Y (в процентах от роста, 0.5 = половина)");
        QUADRUPED_Y_OFFSET_ADD = ConfigRegistry.decimal(b, "quadrupedYOffsetAdd", 0.1, -10.0, 10.0, "[Четвероногие] Доп. смещение по высоте (в блоках)");
        QUADRUPED_FORWARD_OFFSET_MULT = ConfigRegistry.decimal(b, "quadrupedForwardOffsetMult", 0.5, -10.0, 10.0, "[Четвероногие] Смещение вперед (в процентах от ширины, 0.5 = передняя грань)");
        b.pop();
        CUSTOM_MOBS = b.comment("Добавляйте мобов вручную, если автоматика ошибается.", "Формат: modid:name, forwardOffset, yOffset, radius",
                        "Пример: minecraft:cow, 0.6, 1.1, 0.25")
                .defineListAllowEmpty(List.of("customMobs"), () -> TUNED_MOBS, o -> o instanceof String);
        b.push("compat");
        TACZ_USE_GUN_MULTIPLIER = ConfigRegistry.bool(b, "taczUseGunMultiplier", false,
                "TACZ: keep the gun's own headshot multiplier (gun data and tacz-tweaks) instead of damageMultiplier. The head is always detected by these hitboxes.");
        LSO_SUPPRESS_OWN_HEADSHOT = ConfigRegistry.bool(b, "lsoSuppressOwnHeadshot", true,
                "Legendary Survival Overhaul: skip its own 'Headshot Multiplier' on hits these hitboxes already judged, so a player headshot is not multiplied twice.");
        b.pop();
        b.push("ring");
        RING_HEAD_SCALE = ConfigRegistry.decimal(b, "ringHeadScale", 1.15, 1.0, 3.0,
                "Потёртое кольцо: во сколько раз голова тварей больше для выстрелов того, кто его носит");
        b.pop();
        b.push("debug");
        DEBUG_HITBOXES = ConfigRegistry.bool(b, "debugHitboxes", false,
                "Draw the head boxes and enable the tuner (sneak + right click a mob with a stick, arrows / +- / Enter / Esc).");
        b.pop();
        b.pop();
        SPEC = b.build();
        ConfigRegistry.bind(b, SPEC);
    }

    private HeadshotConfig() {
    }
}
