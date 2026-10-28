package com.voltyx.efw.gore.json;

import com.voltyx.efw.gore.json.BloodAmalgamationPolicy;
import com.voltyx.efw.gore.json.BloodBrightnessMode;
import com.voltyx.efw.gore.json.BloodStyle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.Locale;
public final class BloodEntityDefaultJson {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int VERSION = 1;
    public static final int DEFAULT_RGB = 0xFF0000;
    public static final BloodStyle DEFAULT_STYLE = BloodStyle.LIGHT;
    public static final BloodAmalgamationPolicy DEFAULT_AMALGAMATION = BloodAmalgamationPolicy.BOTH;
    public static final BloodBrightnessMode DEFAULT_BRIGHTNESS = BloodBrightnessMode.WORLD;
    public static final int DEFAULT_COUNT = 45;
    public static final float DEFAULT_DRIP_CHANCE = 0.4f;
    public static final float DEFAULT_VISCOSITY = 0.35f;
    public static final float DEFAULT_SCALE_MIN = 0.4f;
    public static final float DEFAULT_SCALE_MAX = 1.6f;
    public static final int DEFAULT_LIFE_MIN = 150;
    public static final int DEFAULT_LIFE_MAX = 300;

    private BloodEntityDefaultJson() {
    }

    public static void write(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (BufferedWriter w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8, new OpenOption[0]);){
            GSON.toJson((JsonElement)BloodEntityDefaultJson.createRoot(), (Appendable)w);
        }
    }

    public static JsonObject createRoot() {
        JsonObject root = new JsonObject();
        root.addProperty("version", (Number)1);
        root.add("defaults", (JsonElement)BloodEntityDefaultJson.createDefaults());
        root.add("groups", (JsonElement)BloodEntityDefaultJson.createGroups());
        return root;
    }

    private static JsonObject createDefaults() {
        JsonObject defaults = new JsonObject();
        defaults.addProperty("color", BloodEntityDefaultJson.hex6(0xFF0000));
        defaults.addProperty("style", DEFAULT_STYLE.name());
        defaults.addProperty("amalgamation", DEFAULT_AMALGAMATION.name());
        defaults.addProperty("brightness", DEFAULT_BRIGHTNESS.name());
        defaults.addProperty("count", (Number)45);
        defaults.addProperty("dripChance", (Number)Float.valueOf(0.4f));
        defaults.addProperty("viscosity", (Number)Float.valueOf(0.35f));
        defaults.add("scale", (JsonElement)BloodEntityDefaultJson.rangeF(0.4f, 1.6f));
        defaults.add("life", (JsonElement)BloodEntityDefaultJson.rangeI(150, 300));
        return defaults;
    }

    private static JsonObject createGroups() {
        JsonObject groups = new JsonObject();
        JsonObject undeadHeavy = new JsonObject();
        undeadHeavy.addProperty("color", "C9C9C9");
        undeadHeavy.addProperty("style", BloodStyle.HEAVY.name());
        undeadHeavy.addProperty("brightness", BloodBrightnessMode.WORLD.name());
        undeadHeavy.add("scale", (JsonElement)BloodEntityDefaultJson.rangeF(0.6f, 1.1f));
        undeadHeavy.add("life", (JsonElement)BloodEntityDefaultJson.rangeI(200, 300));
        undeadHeavy.add("entities", (JsonElement)BloodEntityDefaultJson.entities("minecraft:skeleton", "minecraft:stray", "minecraft:wither_skeleton", "minecraft:skeleton_horse", "minecraft:wither"));
        groups.add("undead_heavy", (JsonElement)undeadHeavy);
        JsonObject slime = new JsonObject();
        slime.addProperty("color", "B0FF91");
        slime.addProperty("style", BloodStyle.SLIMY.name());
        slime.addProperty("amalgamation", BloodAmalgamationPolicy.GROUND.name());
        slime.addProperty("brightness", BloodBrightnessMode.WORLD.name());
        slime.addProperty("count", (Number)40);
        slime.add("scale", (JsonElement)BloodEntityDefaultJson.rangeF(0.6f, 1.4f));
        slime.add("life", (JsonElement)BloodEntityDefaultJson.rangeI(150, 200));
        slime.add("entities", (JsonElement)BloodEntityDefaultJson.entities("minecraft:slime"));
        groups.add("slime", (JsonElement)slime);
        JsonObject magmaCube = new JsonObject();
        magmaCube.addProperty("color", "A82C07");
        magmaCube.addProperty("style", BloodStyle.SLIMY.name());
        magmaCube.addProperty("amalgamation", BloodAmalgamationPolicy.GROUND.name());
        magmaCube.addProperty("brightness", BloodBrightnessMode.FULLBRIGHT.name());
        magmaCube.addProperty("count", (Number)40);
        magmaCube.add("scale", (JsonElement)BloodEntityDefaultJson.rangeF(0.6f, 1.4f));
        magmaCube.add("life", (JsonElement)BloodEntityDefaultJson.rangeI(150, 200));
        magmaCube.add("entities", (JsonElement)BloodEntityDefaultJson.entities("minecraft:magma_cube"));
        groups.add("magma_cube", (JsonElement)magmaCube);
        JsonObject blaze = new JsonObject();
        blaze.addProperty("color", "EDB90C");
        blaze.addProperty("style", BloodStyle.HEAVY.name());
        blaze.addProperty("brightness", BloodBrightnessMode.FULLBRIGHT.name());
        blaze.add("scale", (JsonElement)BloodEntityDefaultJson.rangeF(0.2f, 1.4f));
        blaze.add("life", (JsonElement)BloodEntityDefaultJson.rangeI(200, 300));
        blaze.add("entities", (JsonElement)BloodEntityDefaultJson.entities("minecraft:blaze"));
        groups.add("blaze", (JsonElement)blaze);
        JsonObject poison = new JsonObject();
        poison.addProperty("color", "46CC23");
        poison.addProperty("style", BloodStyle.LIGHT.name());
        poison.addProperty("amalgamation", BloodAmalgamationPolicy.LIQUID.name());
        poison.addProperty("brightness", BloodBrightnessMode.FULLBRIGHT.name());
        poison.addProperty("dripChance", (Number)0);
        poison.addProperty("viscosity", (Number)0);
        poison.add("entities", (JsonElement)BloodEntityDefaultJson.entities("minecraft:spider", "minecraft:cave_spider", "minecraft:witch"));
        groups.add("poison", (JsonElement)poison);
        JsonObject iron = new JsonObject();
        iron.addProperty("color", "BABABA");
        iron.addProperty("style", BloodStyle.HEAVY.name());
        iron.addProperty("brightness", BloodBrightnessMode.WORLD.name());
        iron.add("scale", (JsonElement)BloodEntityDefaultJson.rangeF(0.2f, 1.4f));
        iron.add("life", (JsonElement)BloodEntityDefaultJson.rangeI(200, 300));
        iron.add("entities", (JsonElement)BloodEntityDefaultJson.entities("minecraft:iron_golem"));
        groups.add("iron", (JsonElement)iron);
        JsonObject magic = new JsonObject();
        magic.addProperty("color", "903FD1");
        magic.addProperty("style", BloodStyle.MAGIC.name());
        magic.addProperty("brightness", BloodBrightnessMode.FULLBRIGHT.name());
        magic.addProperty("count", (Number)60);
        magic.add("scale", (JsonElement)BloodEntityDefaultJson.rangeF(0.4f, 1.0f));
        magic.add("life", (JsonElement)BloodEntityDefaultJson.rangeI(200, 300));
        magic.add("entities", (JsonElement)BloodEntityDefaultJson.entities("minecraft:enderman", "minecraft:endermite", "minecraft:silverfish", "minecraft:ender_dragon"));
        groups.add("magic", (JsonElement)magic);
        JsonObject waterCreatures = new JsonObject();
        waterCreatures.addProperty("color", "085A96");
        waterCreatures.addProperty("style", BloodStyle.LIGHT.name());
        waterCreatures.addProperty("brightness", BloodBrightnessMode.WORLD.name());
        waterCreatures.addProperty("count", (Number)25);
        waterCreatures.add("life", (JsonElement)BloodEntityDefaultJson.rangeI(100, 200));
        waterCreatures.add("entities", (JsonElement)BloodEntityDefaultJson.entities("minecraft:squid", "minecraft:glow_squid", "minecraft:guardian", "minecraft:elder_guardian"));
        groups.add("water_creatures", (JsonElement)waterCreatures);
        return groups;
    }

    private static JsonObject rangeF(float min, float max) {
        JsonObject obj = new JsonObject();
        obj.addProperty("min", (Number)Float.valueOf(min));
        obj.addProperty("max", (Number)Float.valueOf(max));
        return obj;
    }

    private static JsonObject rangeI(int min, int max) {
        JsonObject obj = new JsonObject();
        obj.addProperty("min", (Number)min);
        obj.addProperty("max", (Number)max);
        return obj;
    }

    private static JsonArray entities(String ... ids) {
        JsonArray arr = new JsonArray();
        for (String id : ids) {
            arr.add(new com.google.gson.JsonPrimitive(id));
        }
        return arr;
    }

    private static String hex6(int rgb) {
        return String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
    }
}

