package com.voltyx.efw.client;

import com.voltyx.efw.config.ConfigRegistry;
import net.minecraftforge.common.ForgeConfigSpec;

import static com.voltyx.efw.config.ConfigRegistry.decimal;
import static com.voltyx.efw.config.ConfigRegistry.integer;

/** Port of the 1.12 lockon.cfg (client only). */
public final class LockOnConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue MAX_RANGE;
    public static final ForgeConfigSpec.DoubleValue AUTO_SWITCH_RANGE;
    public static final ForgeConfigSpec.DoubleValue ROTATION_SPEED;
    public static final ForgeConfigSpec.DoubleValue POST_ROLL_ROTATION_SPEED;
    public static final ForgeConfigSpec.IntValue WALL_TIMEOUT_TICKS;
    public static final ForgeConfigSpec.DoubleValue AUTO_SWITCH_ANGLE;
    public static final ForgeConfigSpec.DoubleValue TARGET_HEIGHT_MULTIPLIER;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ConfigRegistry.section("Lock-on");
        b.push("lockon");
        MAX_RANGE = decimal(b, "maxRange", 24.0, 1.0, 128.0, "Maximum distance to lock on a target.");
        AUTO_SWITCH_RANGE = decimal(b, "autoSwitchRange", 12.0, 1.0, 64.0, "Distance to find the next target after a kill.");
        ROTATION_SPEED = decimal(b, "rotationSpeed", 2.5, 0.1, 10.0, "How fast the camera follows the target (degrees per frame at most).");
        POST_ROLL_ROTATION_SPEED = decimal(b, "postRollRotationSpeed", 16.0, 1.0, 50.0, "Turn speed towards the target right after a roll.");
        WALL_TIMEOUT_TICKS = integer(b, "wallTimeout", 20, 0, 4000, "Ticks before the lock is lost when the target is behind a wall.");
        AUTO_SWITCH_ANGLE = decimal(b, "autoSwitchAngle", 90.0, 0.0, 180.0, "Max angle for auto-switching to the next target.");
        TARGET_HEIGHT_MULTIPLIER = decimal(b, "targetHeightMultiplier", 0.66, 0.0, 1.0, "Height of the focus point on the target (0 = feet, 1 = head).");
        b.pop();
        SPEC = b.build();
        ConfigRegistry.bind(b, SPEC);
    }

    private LockOnConfig() {
    }
}
