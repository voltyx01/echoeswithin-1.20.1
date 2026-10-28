package com.voltyx.efw.sins;

import com.voltyx.efw.config.ConfigRegistry;
import net.minecraftforge.common.ForgeConfigSpec;

/** Client options of the SEVEN screen (the 1.12 version kept the theme in the gender mod's player data). */
public final class SevenConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue WARM_THEME;
    public static final ForgeConfigSpec.IntValue BUTTON_X;
    public static final ForgeConfigSpec.IntValue BUTTON_Y;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ConfigRegistry.section("SEVEN");
        b.push("seven");
        WARM_THEME = ConfigRegistry.bool(b, "warmTheme", false, "Warm umber/blood/gold palette instead of the cold dark slate one.");
        BUTTON_X = ConfigRegistry.integer(b, "characterButtonX", 27, -400, 400,
                "Character button in the inventory (opens SEVEN): X offset from the left edge of the inventory window.");
        BUTTON_Y = ConfigRegistry.integer(b, "characterButtonY", 9, -400, 400,
                "Character button in the inventory (opens SEVEN): Y offset from the top edge of the inventory window.");
        b.pop();
        SPEC = b.build();
        ConfigRegistry.bind(b, SPEC);
    }

    private SevenConfig() {
    }
}
