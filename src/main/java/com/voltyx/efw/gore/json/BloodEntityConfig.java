package com.voltyx.efw.gore.json;

import com.voltyx.efw.gore.json.BloodAmalgamationPolicy;
import com.voltyx.efw.gore.json.BloodBrightnessMode;
import com.voltyx.efw.gore.json.BloodEntityDefaultJson;
import com.voltyx.efw.gore.json.BloodEntityLog;
import com.voltyx.efw.gore.json.BloodStyle;
import com.voltyx.efw.gore.particle.common.Util;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;
public final class BloodEntityConfig {
    private static final int CURRENT_VERSION = 1;
    private static final int FALLBACK_RGB = 0xFF0000;
    private static final float FALLBACK_SCALE_MIN = 0.4f;
    private static final float FALLBACK_SCALE_MAX = 1.6f;
    private static final float SCALE_ABS_MIN = 0.001f;
    private static final float SCALE_ABS_MAX = 6.0f;
    private static final int FALLBACK_LIFE_MIN = 150;
    private static final int FALLBACK_LIFE_MAX = 300;
    private static final int LIFE_ABS_MIN = 1;
    private static final int LIFE_ABS_MAX = 6000;
    private static final float FALLBACK_DRIP_CHANCE = 0.4f;
    private static final float DRIP_CHANCE_ABS_MIN = 0.0f;
    private static final float DRIP_CHANCE_ABS_MAX = 1.0f;
    private static final int FALLBACK_COUNT = 45;
    private static final int COUNT_ABS_MIN = 0;
    private static final int COUNT_ABS_MAX = 4096;
    private static final float FALLBACK_VISCOSITY = 0.35f;
    private static final float VISCOSITY_ABS_MIN = 0.0f;
    private static final float VISCOSITY_ABS_MAX = 1.0f;
    private static File FILE;
    private static boolean loaded;
    private static int defaultRGB;
    private static BloodStyle defaultStyle;
    private static BloodAmalgamationPolicy defaultAmalgamation;
    private static BloodBrightnessMode defaultBrightness;
    private static float defaultDripChance;
    private static float defaultViscosity;
    private static float defaultScaleMin;
    private static float defaultScaleMax;
    private static int defaultLifeMin;
    private static int defaultLifeMax;
    private static int defaultCount;
    private static Values defaultResolvedValues;
    private static final Map<ResourceLocation, Values> entityToValues;
    private static final Set<String> ROOT_KEYS;
    private static final Set<String> DEFAULTS_KEYS;
    private static final Set<String> GROUP_KEYS;
    private static final Set<String> RANGE_KEYS;

    private static Set<String> keySet(String ... keys) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(keys)));
    }

    /** config/efw/blood_entity_config.json: blood colour, style and amounts per entity and group. */
    public static void init() {
        File cfgDir = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().toFile();
        File modDir = new File(cfgDir, "efw");
        if (!modDir.exists()) {
            modDir.mkdirs();
        }
        FILE = new File(modDir, "blood_entity_config.json");
        BloodEntityConfig.load();
    }

    public static void ensureLoaded() {
        if (!loaded) {
            BloodEntityConfig.load();
        }
    }

    public static synchronized void load() {
        loaded = true;
        BloodEntityConfig.resetToBuiltins();
        if (FILE == null) {
            return;
        }
        try {
            JsonObject root;
            if (!FILE.exists()) {
                try {
                    BloodEntityDefaultJson.write(FILE);
                }
                catch (Throwable t) {
                    BloodEntityLog.error(FILE, "Could not create default config file. Using built-in defaults.", t);
                    return;
                }
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(Files.newInputStream(FILE.toPath(), new OpenOption[0]), StandardCharsets.UTF_8));){
                JsonElement rootEl = new JsonParser().parse((Reader)r);
                if (rootEl == null || rootEl.isJsonNull() || !rootEl.isJsonObject()) {
                    throw BloodEntityLog.exRootMustBeObject();
                }
                root = rootEl.getAsJsonObject();
            }
            catch (Throwable t) {
                BloodEntityLog.error(FILE, "Failed to read/parse config. Using built-in defaults.", t);
                return;
            }
            BloodEntityConfig.loadV1(root);
            BloodEntityConfig.refreshResolvedDefaults();
        }
        catch (Throwable t) {
            BloodEntityConfig.resetToBuiltins();
            BloodEntityLog.error(FILE, "Unexpected error while loading config. Using built-in defaults.", t);
        }
    }

    public static Values getValues(@Nonnull Entity e) {
        return BloodEntityConfig.getEntry(e);
    }

    public static int getDefaultRGB() {
        BloodEntityConfig.ensureLoaded();
        return defaultRGB;
    }

    public static BloodStyle getDefaultStyle() {
        BloodEntityConfig.ensureLoaded();
        return defaultStyle;
    }

    public static BloodAmalgamationPolicy getDefaultAmalgamation() {
        BloodEntityConfig.ensureLoaded();
        return defaultAmalgamation;
    }

    public static BloodBrightnessMode getDefaultBrightness() {
        BloodEntityConfig.ensureLoaded();
        return defaultBrightness;
    }

    public static float getDefaultScaleMin() {
        BloodEntityConfig.ensureLoaded();
        return defaultScaleMin;
    }

    public static float getDefaultScaleMax() {
        BloodEntityConfig.ensureLoaded();
        return defaultScaleMax;
    }

    public static int getDefaultLifeMin() {
        BloodEntityConfig.ensureLoaded();
        return defaultLifeMin;
    }

    public static int getDefaultLifeMax() {
        BloodEntityConfig.ensureLoaded();
        return defaultLifeMax;
    }

    public static float getDefaultDripChance() {
        BloodEntityConfig.ensureLoaded();
        return defaultDripChance;
    }

    public static int getDefaultParticleCount() {
        BloodEntityConfig.ensureLoaded();
        return defaultCount;
    }

    public static float getDefaultViscosity() {
        BloodEntityConfig.ensureLoaded();
        return defaultViscosity;
    }

    public static float sanitizeViscosity(float viscosity01, float def) {
        float x = viscosity01;
        if (!Float.isFinite(x)) {
            x = def;
        }
        if (x < 0.0f) {
            x = 0.0f;
        }
        if (x > 1.0f) {
            x = 1.0f;
        }
        return x;
    }

    public static int sanitizeParticleCount(int count, int def) {
        int x = count;
        if (x < 0 || x > 4096) {
            x = def;
        }
        if (x < 0) {
            x = 0;
        }
        if (x > 4096) {
            x = 4096;
        }
        return x;
    }

    public static RangeI sanitizeLifeRange(int min, int max) {
        int a = min;
        int b = max;
        if (a < 1) {
            a = 1;
        }
        if (b < 1) {
            b = 1;
        }
        if (a > 6000) {
            a = 6000;
        }
        if (b > 6000) {
            b = 6000;
        }
        if (b < a) {
            int t = a;
            a = b;
            b = t;
        }
        if (a < 1) {
            a = 1;
        }
        if (b < a) {
            b = a;
        }
        return RangeI.of(a, b);
    }

    public static Util.RangeF sanitizeScaleRange(float min, float max) {
        float a = min;
        float b = max;
        if (!Float.isFinite(a)) {
            a = 0.4f;
        }
        if (!Float.isFinite(b)) {
            b = 1.6f;
        }
        if (a < 0.001f) {
            a = 0.001f;
        }
        if (b < 0.001f) {
            b = 0.001f;
        }
        if (a > 6.0f) {
            a = 6.0f;
        }
        if (b > 6.0f) {
            b = 6.0f;
        }
        if (b < a) {
            float t = a;
            a = b;
            b = t;
        }
        if (a < 0.001f) {
            a = 0.001f;
        }
        if (b < a) {
            b = a;
        }
        return Util.RangeF.of(a, b).clampMin(0.001f);
    }

    public static float sanitizeDripChance(float chance01, float def) {
        float x = chance01;
        if (!Float.isFinite(x)) {
            x = def;
        }
        if (x < 0.0f) {
            x = 0.0f;
        }
        if (x > 1.0f) {
            x = 1.0f;
        }
        return x;
    }

    private static void resetToBuiltins() {
        entityToValues.clear();
        defaultRGB = 0xFF0000;
        defaultStyle = BloodEntityDefaultJson.DEFAULT_STYLE;
        defaultAmalgamation = BloodEntityDefaultJson.DEFAULT_AMALGAMATION;
        defaultBrightness = BloodEntityDefaultJson.DEFAULT_BRIGHTNESS;
        defaultScaleMin = 0.4f;
        defaultScaleMax = 1.6f;
        defaultLifeMin = 150;
        defaultLifeMax = 300;
        defaultCount = 45;
        defaultDripChance = 0.4f;
        defaultViscosity = 0.35f;
        BloodEntityConfig.refreshResolvedDefaults();
    }

    private static void refreshResolvedDefaults() {
        defaultResolvedValues = BloodEntityConfig.buildValues(defaultRGB, defaultStyle, defaultAmalgamation, defaultBrightness, defaultScaleMin, defaultScaleMax, defaultLifeMin, defaultLifeMax, defaultCount, defaultDripChance, defaultViscosity);
    }

    private static BloodAmalgamationPolicy resolveEffectiveAmalgamation(BloodStyle style, BloodAmalgamationPolicy configured) {
        return style.isLightLike() ? configured : BloodAmalgamationPolicy.NONE;
    }

    private static float resolveEffectiveDripChance(BloodStyle style, float configured) {
        return style.isLightLike() ? configured : 0.0f;
    }

    private static float resolveEffectiveViscosity(BloodStyle style, float configured) {
        return style.isLightLike() ? configured : 0.35f;
    }

    private static Values buildValues(int rgb, BloodStyle style, BloodAmalgamationPolicy configuredAmalgamation, BloodBrightnessMode brightness, float scaleMin, float scaleMax, int lifeMin, int lifeMax, int count, float configuredDripChance, float configuredViscosity) {
        return new Values(rgb, style, BloodEntityConfig.resolveEffectiveAmalgamation(style, configuredAmalgamation), brightness, scaleMin, scaleMax, lifeMin, lifeMax, count, BloodEntityConfig.resolveEffectiveDripChance(style, configuredDripChance), BloodEntityConfig.resolveEffectiveViscosity(style, configuredViscosity));
    }

    private static Values getEntry(@Nonnull Entity e) {
        Values v;
        BloodEntityConfig.ensureLoaded();
        ResourceLocation key = null;
        try {
            key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        if (key == null) {
            try {
                String s = e.getEncodeId();
                key = BloodEntityConfig.safeRL(s);
            }
            catch (Throwable s) {
                // empty catch block
            }
        }
        if (key != null && (v = entityToValues.get(key)) != null) {
            return v;
        }
        return defaultResolvedValues;
    }

    private static void loadV1(JsonObject root) {
        try {
            BloodEntityConfig.requireAllowedKeys(root, ROOT_KEYS, "root");
        }
        catch (Throwable t) {
            BloodEntityLog.error(FILE, "Invalid root keys (unknown variables). Using built-in defaults.", t);
            return;
        }
        try {
            if (root.has("version") && root.get("version").isJsonNull()) {
                throw BloodEntityLog.exPresentButNull("root.version");
            }
            Integer v = BloodEntityConfig.getOptionalInt(root, "version", "root");
            if (v != null && v != 1) {
                throw new JsonParseException("Expected version 1 but got " + v);
            }
        }
        catch (Throwable t) {
            BloodEntityLog.error(FILE, "Unsupported config version. Using built-in defaults.", t);
            return;
        }
        try {
            JsonObject defs = BloodEntityConfig.getOptionalObject(root, "defaults", "root");
            if (root.has("defaults") && defs == null) {
                throw BloodEntityLog.exPresentButNull("root.defaults");
            }
            if (defs != null) {
                BloodEntityConfig.requireAllowedKeys(defs, DEFAULTS_KEYS, "root.defaults");
                BloodEntityConfig.parseDefaults(defs);
            }
        }
        catch (Throwable t) {
            BloodEntityLog.error(FILE, "Invalid 'defaults' section. Keeping built-in defaults for that section.", t);
        }
        HashMap<ResourceLocation, Values> tmp = new HashMap<ResourceLocation, Values>();
        try {
            JsonObject groupsObj = BloodEntityConfig.getOptionalObject(root, "groups", "root");
            if (root.has("groups") && groupsObj == null) {
                throw BloodEntityLog.exPresentButNull("root.groups");
            }
            if (groupsObj == null) {
                entityToValues.clear();
                return;
            }
            BloodEntityConfig.parseGroups(groupsObj, tmp);
            entityToValues.clear();
            entityToValues.putAll(tmp);
        }
        catch (Throwable t) {
            entityToValues.clear();
            BloodEntityLog.error(FILE, "Invalid 'groups' section. Ignoring ALL groups and using defaults.", t);
        }
    }

    private static void parseDefaults(JsonObject defs) {
        int rgb = BloodEntityConfig.parseColorOrDefault(BloodEntityConfig.getOptionalString(defs, "color", "root.defaults"), defaultRGB, "root.defaults.color");
        BloodStyle style = BloodStyle.parseOrDefault(BloodEntityConfig.getOptionalString(defs, "style", "root.defaults"), defaultStyle, "root.defaults.style");
        BloodAmalgamationPolicy amalgamation = BloodAmalgamationPolicy.parseOrDefault(BloodEntityConfig.getOptionalString(defs, "amalgamation", "root.defaults"), defaultAmalgamation, "root.defaults.amalgamation");
        BloodBrightnessMode brightness = BloodBrightnessMode.parseOrDefault(BloodEntityConfig.getOptionalString(defs, "brightness", "root.defaults"), defaultBrightness, "root.defaults.brightness");
        int count = BloodEntityConfig.parseCountOrDefault(BloodEntityConfig.getOptionalInt(defs, "count", "root.defaults"), defaultCount, "root.defaults.count");
        float dripChance = BloodEntityConfig.parseDripChanceOrDefault(BloodEntityConfig.getOptionalFloat(defs, "dripChance", "root.defaults"), defaultDripChance, "root.defaults.dripChance");
        float viscosity = BloodEntityConfig.parseViscosityOrDefault(BloodEntityConfig.getOptionalFloat(defs, "viscosity", "root.defaults"), defaultViscosity, "root.defaults.viscosity");
        float scaleMin = defaultScaleMin;
        float scaleMax = defaultScaleMax;
        JsonObject scale = BloodEntityConfig.getOptionalObject(defs, "scale", "root.defaults");
        if (scale != null) {
            BloodEntityConfig.requireAllowedKeys(scale, RANGE_KEYS, "root.defaults.scale");
            scaleMin = BloodEntityConfig.parseScaleOrDefault(BloodEntityConfig.getOptionalFloat(scale, "min", "root.defaults.scale"), defaultScaleMin, "root.defaults.scale.min");
            scaleMax = BloodEntityConfig.parseScaleOrDefault(BloodEntityConfig.getOptionalFloat(scale, "max", "root.defaults.scale"), defaultScaleMax, "root.defaults.scale.max");
            BloodEntityConfig.validateScaleRange(scaleMin, scaleMax, "root.defaults.scale.min", "root.defaults.scale.max");
        }
        int lifeMin = defaultLifeMin;
        int lifeMax = defaultLifeMax;
        JsonObject life = BloodEntityConfig.getOptionalObject(defs, "life", "root.defaults");
        if (life != null) {
            BloodEntityConfig.requireAllowedKeys(life, RANGE_KEYS, "root.defaults.life");
            lifeMin = BloodEntityConfig.parseLifeOrDefault(BloodEntityConfig.getOptionalInt(life, "min", "root.defaults.life"), defaultLifeMin, "root.defaults.life.min");
            lifeMax = BloodEntityConfig.parseLifeOrDefault(BloodEntityConfig.getOptionalInt(life, "max", "root.defaults.life"), defaultLifeMax, "root.defaults.life.max");
            BloodEntityConfig.validateLifeRange(lifeMin, lifeMax, "root.defaults.life.min", "root.defaults.life.max");
        }
        defaultRGB = rgb;
        defaultStyle = style;
        defaultAmalgamation = amalgamation;
        defaultBrightness = brightness;
        defaultCount = count;
        defaultDripChance = dripChance;
        defaultViscosity = viscosity;
        defaultScaleMin = scaleMin;
        defaultScaleMax = scaleMax;
        defaultLifeMin = lifeMin;
        defaultLifeMax = lifeMax;
    }

    private static void parseGroups(JsonObject groupsObj, Map<ResourceLocation, Values> out) {
        HashMap<ResourceLocation, String> entityDefinedAt = new HashMap<ResourceLocation, String>();
        for (Map.Entry ge : groupsObj.entrySet()) {
            String groupId;
            String string = groupId = ge.getKey() != null ? ((String)ge.getKey()).trim() : "";
            if (groupId.isEmpty()) {
                throw BloodEntityLog.exRequiredNonBlank("root.groups.<groupId>");
            }
            String groupWhere = "root.groups." + groupId;
            JsonElement val = (JsonElement)ge.getValue();
            if (val == null || val.isJsonNull() || !val.isJsonObject()) {
                throw BloodEntityLog.exMustBeObject(groupWhere);
            }
            JsonObject g = val.getAsJsonObject();
            BloodEntityConfig.requireAllowedKeys(g, GROUP_KEYS, groupWhere);
            JsonArray ents = BloodEntityConfig.getRequiredArray(g, "entities", groupWhere);
            if (ents.size() == 0) continue;
            int rgb = BloodEntityConfig.parseColorOrDefault(BloodEntityConfig.getOptionalString(g, "color", groupWhere), defaultRGB, groupWhere + ".color");
            BloodStyle style = BloodStyle.parseOrDefault(BloodEntityConfig.getOptionalString(g, "style", groupWhere), defaultStyle, groupWhere + ".style");
            BloodBrightnessMode brightness = BloodBrightnessMode.parseOrDefault(BloodEntityConfig.getOptionalString(g, "brightness", groupWhere), defaultBrightness, groupWhere + ".brightness");
            int count = BloodEntityConfig.parseCountOrDefault(BloodEntityConfig.getOptionalInt(g, "count", groupWhere), defaultCount, groupWhere + ".count");
            BloodAmalgamationPolicy amalgamation = defaultAmalgamation;
            float dripChance = defaultDripChance;
            float viscosity = defaultViscosity;
            if (style.isLightLike()) {
                amalgamation = BloodAmalgamationPolicy.parseOrDefault(BloodEntityConfig.getOptionalString(g, "amalgamation", groupWhere), defaultAmalgamation, groupWhere + ".amalgamation");
                dripChance = BloodEntityConfig.parseDripChanceOrDefault(BloodEntityConfig.getOptionalFloat(g, "dripChance", groupWhere), defaultDripChance, groupWhere + ".dripChance");
                viscosity = BloodEntityConfig.parseViscosityOrDefault(BloodEntityConfig.getOptionalFloat(g, "viscosity", groupWhere), defaultViscosity, groupWhere + ".viscosity");
            } else {
                String ignored = BloodEntityConfig.getPresentIgnoredStyleKeys(g);
                if (!ignored.isEmpty()) {
                    BloodEntityLog.warn(FILE, "Ignoring " + ignored + " in " + groupWhere + " because style " + (Object)((Object)style) + " does not use amalgamation, dripChance, or viscosity.");
                }
            }
            float scaleMin = defaultScaleMin;
            float scaleMax = defaultScaleMax;
            JsonObject scale = BloodEntityConfig.getOptionalObject(g, "scale", groupWhere);
            if (scale != null) {
                BloodEntityConfig.requireAllowedKeys(scale, RANGE_KEYS, groupWhere + ".scale");
                scaleMin = BloodEntityConfig.parseScaleOrDefault(BloodEntityConfig.getOptionalFloat(scale, "min", groupWhere + ".scale"), defaultScaleMin, groupWhere + ".scale.min");
                scaleMax = BloodEntityConfig.parseScaleOrDefault(BloodEntityConfig.getOptionalFloat(scale, "max", groupWhere + ".scale"), defaultScaleMax, groupWhere + ".scale.max");
                BloodEntityConfig.validateScaleRange(scaleMin, scaleMax, groupWhere + ".scale.min", groupWhere + ".scale.max");
            }
            int lifeMin = defaultLifeMin;
            int lifeMax = defaultLifeMax;
            JsonObject life = BloodEntityConfig.getOptionalObject(g, "life", groupWhere);
            if (life != null) {
                BloodEntityConfig.requireAllowedKeys(life, RANGE_KEYS, groupWhere + ".life");
                lifeMin = BloodEntityConfig.parseLifeOrDefault(BloodEntityConfig.getOptionalInt(life, "min", groupWhere + ".life"), defaultLifeMin, groupWhere + ".life.min");
                lifeMax = BloodEntityConfig.parseLifeOrDefault(BloodEntityConfig.getOptionalInt(life, "max", groupWhere + ".life"), defaultLifeMax, groupWhere + ".life.max");
                BloodEntityConfig.validateLifeRange(lifeMin, lifeMax, groupWhere + ".life.min", groupWhere + ".life.max");
            }
            Values values = BloodEntityConfig.buildValues(rgb, style, amalgamation, brightness, scaleMin, scaleMax, lifeMin, lifeMax, count, dripChance, viscosity);
            for (int i = 0; i < ents.size(); ++i) {
                String whereKey = groupWhere + ".entities[" + i + "]";
                JsonElement ee = ents.get(i);
                if (ee == null || ee.isJsonNull() || !ee.isJsonPrimitive() || !ee.getAsJsonPrimitive().isString()) {
                    throw BloodEntityLog.exEntityMustBeString(whereKey);
                }
                String entStr = ee.getAsString();
                ResourceLocation rl = BloodEntityConfig.safeRL(entStr);
                if (rl == null) {
                    BloodEntityLog.warn(FILE, "Skipping invalid entity id '" + entStr + "' at " + whereKey);
                    continue;
                }
                String prev = entityDefinedAt.putIfAbsent(rl, whereKey);
                if (prev != null) {
                    throw BloodEntityLog.exDuplicateEntity(rl, whereKey, prev);
                }
                out.put(rl, values);
            }
        }
    }

    private static String getPresentIgnoredStyleKeys(JsonObject obj) {
        StringBuilder sb = new StringBuilder();
        if (obj.has("amalgamation")) {
            sb.append("amalgamation");
        }
        if (obj.has("dripChance")) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("dripChance");
        }
        if (obj.has("viscosity")) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("viscosity");
        }
        return sb.toString();
    }

    private static int parseCountOrDefault(@Nullable Integer v, int def, String whereKey) {
        if (v == null) {
            return def;
        }
        int x = v;
        if (x < 0 || x > 4096) {
            throw BloodEntityLog.exInvalidParticleCount(whereKey, String.valueOf(v));
        }
        return x;
    }

    private static float parseDripChanceOrDefault(@Nullable Float v, float def, String whereKey) {
        if (v == null) {
            return def;
        }
        float x = v.floatValue();
        if (!Float.isFinite(x) || x < 0.0f || x > 1.0f) {
            throw BloodEntityLog.exInvalidDripChance(whereKey, String.valueOf(v));
        }
        return x;
    }

    private static float parseViscosityOrDefault(@Nullable Float v, float def, String whereKey) {
        if (v == null) {
            return def;
        }
        float x = v.floatValue();
        if (!Float.isFinite(x) || x < 0.0f || x > 1.0f) {
            throw BloodEntityLog.exInvalidViscosity(whereKey, String.valueOf(v));
        }
        return x;
    }

    private static float parseScaleOrDefault(@Nullable Float v, float def, String whereKey) {
        if (v == null) {
            return def;
        }
        float x = v.floatValue();
        if (!Float.isFinite(x)) {
            throw BloodEntityLog.exInvalidScale(whereKey, String.valueOf(v));
        }
        if (x < 0.001f || x > 6.0f) {
            throw BloodEntityLog.exInvalidScale(whereKey, String.valueOf(v));
        }
        return x;
    }

    private static void validateScaleRange(float min, float max, String whereMinKey, String whereMaxKey) {
        if (max < min) {
            throw BloodEntityLog.exInvalidScaleRange(whereMinKey, whereMaxKey, min, max);
        }
    }

    private static int parseLifeOrDefault(@Nullable Integer v, int def, String whereKey) {
        if (v == null) {
            return def;
        }
        int x = v;
        if (x < 1 || x > 6000) {
            throw BloodEntityLog.exInvalidLife(whereKey, String.valueOf(v));
        }
        return x;
    }

    private static void validateLifeRange(int min, int max, String whereMinKey, String whereMaxKey) {
        if (max < min) {
            throw BloodEntityLog.exInvalidLifeRange(whereMinKey, whereMaxKey, min, max);
        }
    }

    private static int parseColorOrDefault(@Nullable String s, int def, String whereKey) {
        if (s == null) {
            return def;
        }
        String x = s.trim();
        if (x.isEmpty()) {
            throw BloodEntityLog.exBlankValue(whereKey, "Omit the key to use the default (must be RRGGBB or AARRGGBB).");
        }
        if (x.startsWith("#")) {
            x = x.substring(1);
        }
        if (x.startsWith("0x") || x.startsWith("0X")) {
            x = x.substring(2);
        }
        if (x.length() != 6 && x.length() != 8) {
            throw BloodEntityLog.exInvalidColorFormat(whereKey, s);
        }
        try {
            long v = Long.parseLong(x, 16);
            if (x.length() == 8) {
                v &= 0xFFFFFFL;
            }
            return (int)(v & 0xFFFFFFL);
        }
        catch (Throwable t) {
            throw BloodEntityLog.exInvalidHex(whereKey, s);
        }
    }

    @Nullable
    private static ResourceLocation safeRL(@Nullable String s) {
        if (s == null) {
            return null;
        }
        String id = s.trim();
        if (id.isEmpty()) {
            return null;
        }
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        id = id.toLowerCase(Locale.ROOT);
        try {
            return new ResourceLocation(id);
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static void requireAllowedKeys(JsonObject obj, Set<String> allowed, String where) {
        for (Map.Entry e : obj.entrySet()) {
            String k = (String)e.getKey();
            if (allowed.contains(k)) continue;
            throw BloodEntityLog.exUnknownKey(k, where);
        }
    }

    @Nullable
    private static JsonObject getOptionalObject(JsonObject obj, String key, String where) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = obj.get(key);
        if (!el.isJsonObject()) {
            throw BloodEntityLog.exMustBeObject(where + "." + key);
        }
        return el.getAsJsonObject();
    }

    @Nullable
    private static String getOptionalString(JsonObject obj, String key, String where) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = obj.get(key);
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            throw BloodEntityLog.exMustBeString(where + "." + key);
        }
        return el.getAsString();
    }

    @Nullable
    private static JsonArray getOptionalArray(JsonObject obj, String key, String where) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = obj.get(key);
        if (!el.isJsonArray()) {
            throw BloodEntityLog.exMustBeArray(where + "." + key);
        }
        return el.getAsJsonArray();
    }

    private static JsonArray getRequiredArray(JsonObject obj, String key, String where) {
        JsonArray a = BloodEntityConfig.getOptionalArray(obj, key, where);
        if (a == null) {
            throw BloodEntityLog.exRequiredArray(where + "." + key);
        }
        return a;
    }

    @Nullable
    private static Float getOptionalFloat(JsonObject obj, String key, String where) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = obj.get(key);
        if (!el.isJsonPrimitive()) {
            throw BloodEntityLog.exMustBeNumber(where + "." + key);
        }
        JsonPrimitive p = el.getAsJsonPrimitive();
        if (p.isNumber()) {
            return Float.valueOf(p.getAsFloat());
        }
        if (p.isString()) {
            String t;
            String raw = p.getAsString();
            String string = t = raw != null ? raw.trim() : "";
            if (t.isEmpty()) {
                throw BloodEntityLog.exBlankValue(where + "." + key, "Omit the key to use the default (must be a number).");
            }
            try {
                return Float.valueOf(Float.parseFloat(t));
            }
            catch (Throwable ex) {
                throw BloodEntityLog.exMustBeNumber(where + "." + key);
            }
        }
        throw BloodEntityLog.exMustBeNumber(where + "." + key);
    }

    @Nullable
    private static Integer getOptionalInt(JsonObject obj, String key, String where) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = obj.get(key);
        if (!el.isJsonPrimitive()) {
            throw BloodEntityLog.exMustBeNumber(where + "." + key);
        }
        JsonPrimitive p = el.getAsJsonPrimitive();
        if (p.isNumber()) {
            double d = p.getAsDouble();
            if (!Double.isFinite(d)) {
                throw BloodEntityLog.exMustBeNumber(where + "." + key);
            }
            double r = Math.rint(d);
            if (Math.abs(d - r) > 1.0E-6) {
                throw BloodEntityLog.exMustBeNumber(where + "." + key);
            }
            if (r < -2.147483648E9 || r > 2.147483647E9) {
                throw BloodEntityLog.exMustBeNumber(where + "." + key);
            }
            return (int)r;
        }
        if (p.isString()) {
            String t;
            String raw = p.getAsString();
            String string = t = raw != null ? raw.trim() : "";
            if (t.isEmpty()) {
                throw BloodEntityLog.exBlankValue(where + "." + key, "Omit the key to use the default (must be an integer).");
            }
            try {
                return Integer.parseInt(t);
            }
            catch (Throwable ex) {
                throw BloodEntityLog.exMustBeNumber(where + "." + key);
            }
        }
        throw BloodEntityLog.exMustBeNumber(where + "." + key);
    }

    static {
        entityToValues = new HashMap<ResourceLocation, Values>();
        ROOT_KEYS = BloodEntityConfig.keySet("version", "defaults", "groups");
        DEFAULTS_KEYS = BloodEntityConfig.keySet("color", "style", "amalgamation", "brightness", "count", "dripChance", "viscosity", "scale", "life");
        GROUP_KEYS = BloodEntityConfig.keySet("color", "style", "amalgamation", "brightness", "count", "dripChance", "viscosity", "scale", "life", "entities");
        RANGE_KEYS = BloodEntityConfig.keySet("min", "max");
    }

    public static final class RangeI {
        public final int min;
        public final int max;

        private RangeI(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public static RangeI of(int a, int b) {
            return a <= b ? new RangeI(a, b) : new RangeI(b, a);
        }
    }

    public static final class Values {
        public final int rgb;
        public final BloodStyle style;
        public final BloodAmalgamationPolicy amalgamation;
        public final BloodBrightnessMode brightness;
        public final float scaleMin;
        public final float scaleMax;
        public final int lifeMin;
        public final int lifeMax;
        public final int count;
        public final float dripChance;
        public final float viscosity;

        private Values(int rgb, BloodStyle style, BloodAmalgamationPolicy amalgamation, BloodBrightnessMode brightness, float scaleMin, float scaleMax, int lifeMin, int lifeMax, int count, float dripChance, float viscosity) {
            this.rgb = rgb;
            this.style = style;
            this.amalgamation = amalgamation;
            this.brightness = brightness;
            this.scaleMin = scaleMin;
            this.scaleMax = scaleMax;
            this.lifeMin = lifeMin;
            this.lifeMax = lifeMax;
            this.count = count;
            this.dripChance = dripChance;
            this.viscosity = viscosity;
        }
    }
}

