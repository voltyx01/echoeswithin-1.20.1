package com.voltyx.efw.client.hearts;

import com.voltyx.efw.config.ConfigRegistry;
import net.minecraftforge.common.ForgeConfigSpec;

/** Client options of the efw heart bar (efw-hearts.toml). */
public final class HeartsConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue LOW_HEALTH_SHAKE;
    public static final ForgeConfigSpec.BooleanValue REGEN_WAVE;
    public static final ForgeConfigSpec.IntValue BLINK_TICKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ConfigRegistry.section("Hearts");
        b.push("hearts");
        ENABLED = ConfigRegistry.bool(b, "enabled", true,
                "Draw the hearts ourselves: one row, health over 20 in coloured layers, LSO broken and shield hearts included. "
                        + "Off: the vanilla and LSO bars come back.");
        LOW_HEALTH_SHAKE = ConfigRegistry.bool(b, "lowHealthShake", true, "The hearts shake at 2 hearts or less, as in vanilla.");
        REGEN_WAVE = ConfigRegistry.bool(b, "regenWave", true, "A wave runs over the hearts while regenerating, as in vanilla.");
        BLINK_TICKS = ConfigRegistry.integer(b, "blinkTicks", 20, 0, 60,
                "How long the hearts blink after a hit (the lost part stays white meanwhile).");
        b.pop();
        SPEC = b.build();
        ConfigRegistry.bind(b, SPEC);
    }

    private HeartsConfig() {
    }
}
