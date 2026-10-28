package com.voltyx.efw.heartbeat;

import com.voltyx.efw.config.ConfigRegistry;
import net.minecraftforge.common.ForgeConfigSpec;

/** Client settings of the heartbeat system. */
public final class HeartbeatConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue BODY_VOLUME;
    public static final ForgeConfigSpec.IntValue BODY_THRESHOLD;
    public static final ForgeConfigSpec.BooleanValue TRAIL;
    public static final ForgeConfigSpec.BooleanValue TREMOR;
    public static final ForgeConfigSpec.BooleanValue AIM_DRIFT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ConfigRegistry.section("Heartbeat");
        b.push("heartbeat");
        ENABLED = ConfigRegistry.bool(b, "enabled", true, "The pulse of the player reacts to danger and plays the heartbeat sounds.");
        BODY_VOLUME = ConfigRegistry.decimal(b, "bodyVolume", 1.0, 0.0, 1.0, "Volume of the heartbeat in the body at a high pulse.");
        BODY_THRESHOLD = ConfigRegistry.integer(b, "bodyThreshold", 105, 60, 200, "Pulse (BPM) from which the heartbeat in the body is heard.");
        TRAIL = ConfigRegistry.bool(b, "trail", true, "The picture leaves a ghost trail at a pulse over 150.");
        TREMOR = ConfigRegistry.bool(b, "tremor", true, "The hands shake at a pulse over 150.");
        AIM_DRIFT = ConfigRegistry.bool(b, "aimDrift", true, "The aim drifts at a pulse over 160.");
        b.pop();
        SPEC = b.build();
        ConfigRegistry.bind(b, SPEC);
    }

    private HeartbeatConfig() {
    }
}
