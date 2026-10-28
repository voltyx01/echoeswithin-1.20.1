package com.voltyx.efw.gore.json;

import com.google.gson.JsonParseException;
import java.io.File;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class BloodEntityLog {
    private static final Logger LOG = LogManager.getLogger((String)"extragore");
    private static final String PREFIX = "[extragore] ";
    public static final String CREATE_DEFAULT_FAILED = "Could not create default config file. Using built-in defaults.";
    public static final String READ_PARSE_FAILED = "Failed to read/parse config. Using built-in defaults.";
    public static final String INVALID_ROOT_KEYS = "Invalid root keys (unknown variables). Using built-in defaults.";
    public static final String UNSUPPORTED_VERSION = "Unsupported config version. Using built-in defaults.";
    public static final String INVALID_DEFAULTS = "Invalid 'defaults' section. Keeping built-in defaults for that section.";
    public static final String INVALID_GROUPS = "Invalid 'groups' section. Ignoring ALL groups and using defaults.";
    public static final String UNEXPECTED_ERROR = "Unexpected error while loading config. Using built-in defaults.";

    private static String fileSuffix(@Nullable File file) {
        return " (file: " + (file != null ? file.getAbsolutePath() : "<null>") + ")";
    }

    public static void warn(@Nullable File file, String msg) {
        LOG.warn(PREFIX + msg + BloodEntityLog.fileSuffix(file));
    }

    public static void error(@Nullable File file, String msg, Throwable t) {
        LOG.error(PREFIX + msg + BloodEntityLog.fileSuffix(file), t);
    }

    public static JsonParseException exRootMustBeObject() {
        return new JsonParseException("Config root must be a JSON object");
    }

    public static JsonParseException exUnknownKey(String key, String where) {
        return new JsonParseException("Unknown key '" + key + "' in " + where);
    }

    public static JsonParseException exMustBeObject(String where) {
        return new JsonParseException(where + " must be an object");
    }

    public static JsonParseException exMustBeString(String whereKey) {
        return new JsonParseException(whereKey + " must be a string");
    }

    public static JsonParseException exPresentButNull(String whereKey) {
        return new JsonParseException(whereKey + " must not be null");
    }

    public static JsonParseException exRequiredNonBlank(String whereKey) {
        return new JsonParseException(whereKey + " is required and must be non-blank");
    }

    public static JsonParseException exMustBeArray(String whereKey) {
        return new JsonParseException(whereKey + " must be an array");
    }

    public static JsonParseException exRequiredArray(String whereKey) {
        return new JsonParseException(whereKey + " is required and must be an array");
    }

    public static JsonParseException exEntitiesEmpty(String whereKey) {
        return new JsonParseException(whereKey + " must not be empty");
    }

    public static JsonParseException exEntityMustBeString(String whereKey) {
        return new JsonParseException(whereKey + " must be a string");
    }

    public static JsonParseException exDuplicateEntity(ResourceLocation rl, String where, String prevWhere) {
        return new JsonParseException("Duplicate entity entry '" + rl + "' at " + where + " (already defined at " + prevWhere + ")");
    }

    public static JsonParseException exInvalidColorFormat(String whereKey, String raw) {
        return new JsonParseException(whereKey + " must be RRGGBB or AARRGGBB (optionally prefixed with '#' or '0x') (got '" + raw + "')");
    }

    public static JsonParseException exInvalidBrightness(String whereKey, String raw) {
        return new JsonParseException(whereKey + " must be WORLD or FULLBRIGHT (got '" + raw + "')");
    }

    public static JsonParseException exInvalidHex(String whereKey, String raw) {
        return new JsonParseException(whereKey + " is not valid hex (got '" + raw + "')");
    }

    public static JsonParseException exBlankValue(String whereKey, String help) {
        return new JsonParseException(whereKey + " is blank. " + help);
    }

    public static JsonParseException exInvalidStyle(String whereKey, String raw) {
        return new JsonParseException(whereKey + " must be LIGHT, HEAVY, MAGIC, or SLIMY (got '" + raw + "')");
    }

    public static JsonParseException exInvalidAmalgamation(String whereKey, String raw) {
        return new JsonParseException(whereKey + " must be NONE, GROUND, LIQUID, or BOTH (got '" + raw + "')");
    }

    public static JsonParseException exMustBeNumber(String whereKey) {
        return new JsonParseException(whereKey + " must be a number");
    }

    public static JsonParseException exInvalidScale(String whereKey, String raw) {
        return new JsonParseException(whereKey + " must be a finite number within allowed range (got '" + raw + "')");
    }

    public static JsonParseException exInvalidScaleRange(String whereMinKey, String whereMaxKey, float min, float max) {
        return new JsonParseException("Invalid scale range: " + whereMaxKey + " (" + max + ") must be >= " + whereMinKey + " (" + min + ")");
    }

    public static JsonParseException exInvalidLife(String whereKey, String raw) {
        return new JsonParseException(whereKey + " must be an integer within allowed range (got '" + raw + "')");
    }

    public static JsonParseException exInvalidLifeRange(String whereMinKey, String whereMaxKey, int min, int max) {
        return new JsonParseException("Invalid life range: " + whereMaxKey + " (" + max + ") must be >= " + whereMinKey + " (" + min + ")");
    }

    public static JsonParseException exInvalidDripChance(String whereKey, String raw) {
        return new JsonParseException(whereKey + " must be a finite number within [0.0, 1.0] (got '" + raw + "')");
    }

    public static JsonParseException exInvalidParticleCount(String whereKey, String raw) {
        return new JsonParseException(whereKey + " must be an integer within allowed range (got '" + raw + "')");
    }

    public static JsonParseException exInvalidViscosity(String whereKey, String raw) {
        return new JsonParseException(whereKey + " must be a finite number within [0.0, 1.0] (got '" + raw + "')");
    }
}

