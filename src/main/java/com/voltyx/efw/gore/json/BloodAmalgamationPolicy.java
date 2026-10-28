package com.voltyx.efw.gore.json;

import com.voltyx.efw.gore.json.BloodEntityLog;
import java.util.Locale;
import javax.annotation.Nullable;

public enum BloodAmalgamationPolicy {
    NONE(0),
    GROUND(1),
    LIQUID(2),
    BOTH(3);

    private final int arg;

    private BloodAmalgamationPolicy(int arg) {
        this.arg = arg;
    }

    public int toArg() {
        return this.arg;
    }

    public static BloodAmalgamationPolicy fromArg(int v) {
        switch (v) {
            case 1: {
                return GROUND;
            }
            case 2: {
                return LIQUID;
            }
            case 3: {
                return BOTH;
            }
        }
        return NONE;
    }

    public boolean allowGround() {
        return this == GROUND || this == BOTH;
    }

    public boolean allowLiquid() {
        return this == LIQUID || this == BOTH;
    }

    public static BloodAmalgamationPolicy parseOrDefault(@Nullable String s, BloodAmalgamationPolicy def, String whereKey) {
        if (s == null) {
            return def;
        }
        String x = s.trim();
        if (x.isEmpty()) {
            throw BloodEntityLog.exBlankValue(whereKey, "Omit the key to use the default (valid: NONE, GROUND, LIQUID, BOTH).");
        }
        switch (x.toUpperCase(Locale.ROOT)) {
            case "NONE": {
                return NONE;
            }
            case "GROUND": {
                return GROUND;
            }
            case "LIQUID": {
                return LIQUID;
            }
            case "BOTH": {
                return BOTH;
            }
        }
        throw BloodEntityLog.exInvalidAmalgamation(whereKey, s);
    }
}

