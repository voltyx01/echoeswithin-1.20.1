package com.voltyx.efw.gore.json;

import com.voltyx.efw.gore.json.BloodEntityLog;
import java.util.Locale;
import javax.annotation.Nullable;

public enum BloodBrightnessMode {
    WORLD(1),
    FULLBRIGHT(2);

    public static final int FULLBRIGHT_PACKED = 0xF000F0;
    private final int arg;

    private BloodBrightnessMode(int arg) {
        this.arg = arg;
    }

    public int toArg() {
        return this.arg;
    }

    public static BloodBrightnessMode fromArg(int v) {
        switch (v) {
            case 2: {
                return FULLBRIGHT;
            }
        }
        return WORLD;
    }

    public int applyToPackedLight(int worldPackedLight) {
        return this == FULLBRIGHT ? 0xF000F0 : worldPackedLight;
    }

    public static BloodBrightnessMode parseOrDefault(@Nullable String s, BloodBrightnessMode def, String whereKey) {
        if (s == null) {
            return def;
        }
        String x = s.trim();
        if (x.isEmpty()) {
            throw BloodEntityLog.exBlankValue(whereKey, "Omit the key to use the default (valid: WORLD, FULLBRIGHT).");
        }
        switch (x.toUpperCase(Locale.ROOT)) {
            case "WORLD": {
                return WORLD;
            }
            case "FULLBRIGHT": {
                return FULLBRIGHT;
            }
        }
        throw BloodEntityLog.exInvalidBrightness(whereKey, s);
    }
}

