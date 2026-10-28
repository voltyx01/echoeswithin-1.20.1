package com.voltyx.efw.gore.json;

import com.voltyx.efw.gore.json.BloodEntityLog;
import java.util.Locale;
import javax.annotation.Nullable;

public enum BloodStyle {
    LIGHT(0),
    HEAVY(1),
    MAGIC(2),
    SLIMY(3);

    private final int arg;

    private BloodStyle(int arg) {
        this.arg = arg;
    }

    public int toArg() {
        return this.arg;
    }

    public static BloodStyle fromArg(int v) {
        switch (v) {
            case 1: {
                return HEAVY;
            }
            case 2: {
                return MAGIC;
            }
            case 3: {
                return SLIMY;
            }
        }
        return LIGHT;
    }

    public boolean isLightLike() {
        return this == LIGHT || this == SLIMY;
    }

    public static BloodStyle parseOrDefault(@Nullable String s, BloodStyle def, String whereKey) {
        if (s == null) {
            return def;
        }
        String x = s.trim();
        if (x.isEmpty()) {
            throw BloodEntityLog.exBlankValue(whereKey, "Omit the key to use the default (valid: LIGHT, HEAVY, MAGIC, SLIMY).");
        }
        switch (x.toUpperCase(Locale.ROOT)) {
            case "LIGHT": {
                return LIGHT;
            }
            case "HEAVY": {
                return HEAVY;
            }
            case "MAGIC": {
                return MAGIC;
            }
            case "SLIMY": {
                return SLIMY;
            }
        }
        throw BloodEntityLog.exInvalidStyle(whereKey, s);
    }
}

