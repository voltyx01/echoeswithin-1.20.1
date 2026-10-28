package com.voltyx.efw.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers every config value together with its UI metadata, so the in-game config screen is built from the same
 * definitions as the toml files.
 */
public final class ConfigRegistry {
    public record Entry(String section, String name, String comment, ForgeConfigSpec.ConfigValue<?> value,
                        ForgeConfigSpec spec, double min, double max) {
        public boolean isBoolean() {
            return value instanceof ForgeConfigSpec.BooleanValue;
        }

        public boolean isInteger() {
            return value instanceof ForgeConfigSpec.IntValue;
        }
    }

    public static final List<Entry> ENTRIES = new ArrayList<>();

    private static ForgeConfigSpec.Builder current;
    private static String section = "";

    private ConfigRegistry() {
    }

    /** The specs are only known after build(); entries are bound to them lazily through this holder. */
    private static final List<Object[]> PENDING = new ArrayList<>();

    public static void section(String name) {
        section = name;
    }

    public static ForgeConfigSpec.IntValue integer(ForgeConfigSpec.Builder b, String name, int def, int min, int max, String comment) {
        ForgeConfigSpec.IntValue v = (comment.isEmpty() ? b : b.comment(comment)).defineInRange(name, def, min, max);
        PENDING.add(new Object[] {section, name, comment, v, (double) min, (double) max, b});
        return v;
    }

    public static ForgeConfigSpec.DoubleValue decimal(ForgeConfigSpec.Builder b, String name, double def, double min, double max, String comment) {
        ForgeConfigSpec.DoubleValue v = (comment.isEmpty() ? b : b.comment(comment)).defineInRange(name, def, min, max);
        PENDING.add(new Object[] {section, name, comment, v, min, max, b});
        return v;
    }

    public static ForgeConfigSpec.BooleanValue bool(ForgeConfigSpec.Builder b, String name, boolean def, String comment) {
        ForgeConfigSpec.BooleanValue v = (comment.isEmpty() ? b : b.comment(comment)).define(name, def);
        PENDING.add(new Object[] {section, name, comment, v, 0.0, 1.0, b});
        return v;
    }

    /** Called right after builder.build(): ties the pending entries of that builder to the finished spec. */
    public static void bind(ForgeConfigSpec.Builder b, ForgeConfigSpec spec) {
        for (Object[] p : PENDING) {
            if (p[6] == b) {
                ENTRIES.add(new Entry((String) p[0], (String) p[1], (String) p[2], (ForgeConfigSpec.ConfigValue<?>) p[3], spec,
                        (Double) p[4], (Double) p[5]));
            }
        }
        PENDING.removeIf(p -> p[6] == b);
    }
}
