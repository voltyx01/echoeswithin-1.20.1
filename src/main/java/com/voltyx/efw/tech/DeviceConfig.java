package com.voltyx.efw.tech;

import com.voltyx.efw.config.ConfigRegistry;
import net.minecraftforge.common.ForgeConfigSpec;

/** Battery powered devices: how fast they drain (the portable map, night vision helmets) and how fast the generator charges. */
public final class DeviceConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MAP_DRAIN_PER_SECOND;
    public static final ForgeConfigSpec.IntValue NVG_DRAIN_PER_SECOND;
    public static final ForgeConfigSpec.IntValue GENERATOR_CHARGE_PER_TICK;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ConfigRegistry.section("Devices and batteries");
        b.push("devices");
        MAP_DRAIN_PER_SECOND = ConfigRegistry.integer(b, "mapDrainPerSecond", 20, 0, 10000, "Charge the worn portable map uses every second (a battery holds 48 000).");
        NVG_DRAIN_PER_SECOND = ConfigRegistry.integer(b, "nvgDrainPerSecond", 20, 0, 10000, "Charge a switched on night vision helmet uses every second.");
        GENERATOR_CHARGE_PER_TICK = ConfigRegistry.integer(b, "generatorChargePerTick", 40, 0, 100000, "Charge a running electricity generator puts into each device in its charging slots every tick.");
        b.pop();
        SPEC = b.build();
        ConfigRegistry.bind(b, SPEC);
    }

    private DeviceConfig() {
    }
}
