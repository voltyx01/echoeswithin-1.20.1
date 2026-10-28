package com.voltyx.efw.gear;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client settings of the kawaii ears. */
public final class KawaiiEarsConfig {
    public enum Mode {
        /** Shown on whoever wears the item (the head curio slot). */
        EQUIPPED,
        /** Shown on every player. */
        ALWAYS,
        /** Never shown. */
        OFF
    }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<Mode> MODE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        MODE = b.comment("EQUIPPED: the ears show on whoever wears the item (the head curio slot); ALWAYS: on every player;"
                + " OFF: never.").defineEnum("mode", Mode.EQUIPPED);
        SPEC = b.build();
    }

    private KawaiiEarsConfig() {
    }
}
