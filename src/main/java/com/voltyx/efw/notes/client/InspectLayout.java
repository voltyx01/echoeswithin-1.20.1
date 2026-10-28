package com.voltyx.efw.notes.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.voltyx.efw.EFW;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Layout of the inspect screen, edited in game with the inspect maker (M in the inspect screen) and kept in
 * config/efw/inspect_layout.json (port of ItemInspectConfig). An item takes its own transform if it has one, else the one of the first
 * custom group it is in, else the one of its automatic group; the text layout is shared.
 */
public final class InspectLayout {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** How the item sits in the inspect screen. */
    public static class Transform {
        public float scale = 1.8f;
        /** Screen offset of the item, GUI pixels. */
        public float posX;
        public float posY;
        /** The point the item turns around, in item units (1 = the size of the item model). */
        public float pivotX;
        public float pivotY;
        public float pivotZ;
        public float yaw = 25.0f;
        public float pitch = 15.0f;
        public float roll;
        /** Thickness (Z scale): paper is thin. */
        public float depth = 1.0f;
        /** Idle turn, degrees per second. */
        public float spin = 6.0f;
        /**
         * Fit to size: the model is measured, its largest side made one unit and its middle the centre, so items of very different
         * sizes (a pistol, a sniper rifle) fill the screen the same at one scale.
         */
        public boolean fit;
        /** With fit: a model whose longest side points at the viewer is turned to lie across the screen. */
        public boolean alignLongest = true;

        public Transform copy() {
            return GSON.fromJson(GSON.toJson(this), Transform.class);
        }
    }

    /** Title, divider and description. */
    public static class Text {
        public float titleScale = 1.25f;
        public int titleX;
        public int titleY;
        public float descScale = 1.0f;
        public int descX;
        public int descY;
        /** Top of the text block, from the bottom of the screen. */
        public int blockY = -145;
        public int dividerWidth = 260;
        public int dividerGap = 16;
        public int descGap = 8;
        public int maxWidth = 380;
        public int maxLines = 7;
        /** Lines shown at once for notes, the diary and the page. */
        public int paperLines = 4;
        /** Height of the item centre from the middle of the screen. */
        public int itemY = -25;

        public Text copy() {
            return GSON.fromJson(GSON.toJson(this), Text.class);
        }
    }

    /** A group made in the maker: chosen items sharing one transform. */
    public static class Group {
        public String name = "Группа";
        public List<String> items = new ArrayList<>();
        public Transform transform = new Transform();
    }

    private static class Data {
        Text text = new Text();
        Map<String, Transform> auto = new LinkedHashMap<>();
        List<Group> groups = new ArrayList<>();
        Map<String, Transform> items = new LinkedHashMap<>();
    }

    private static Data data;

    private InspectLayout() {
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("efw").resolve("inspect_layout.json");
    }

    private static Data data() {
        if (data == null) {
            Data loaded = null;
            try {
                if (Files.exists(file())) {
                    try (Reader r = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
                        loaded = GSON.fromJson(r, Data.class);
                    }
                }
            } catch (Exception e) {
                EFW.LOGGER.warn("[Inspect] cannot read inspect_layout.json: {}", e.toString());
            }
            data = loaded != null ? loaded : new Data();
            if (data.text == null) {
                data.text = new Text();
            }
            if (data.auto == null) {
                data.auto = new LinkedHashMap<>();
            }
            if (data.groups == null) {
                data.groups = new ArrayList<>();
            }
            if (data.items == null) {
                data.items = new LinkedHashMap<>();
            }
            // blocks used to share the items group: they start from what it was tuned to
            Transform items = data.auto.get(autoKey(InspectScreen.Group.ITEMS_2D));
            if (items != null && !data.auto.containsKey(autoKey(InspectScreen.Group.BLOCKS))) {
                data.auto.put(autoKey(InspectScreen.Group.BLOCKS), items.copy());
            }
            for (InspectScreen.Group g : InspectScreen.Group.values()) {
                data.auto.computeIfAbsent(autoKey(g), k -> defaultAuto(g));
            }
        }
        return data;
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                GSON.toJson(data(), w);
            }
        } catch (Exception e) {
            EFW.LOGGER.warn("[Inspect] cannot write inspect_layout.json: {}", e.toString());
        }
    }

    static String autoKey(InspectScreen.Group g) {
        return g.name().toLowerCase();
    }

    static Transform defaultAuto(InspectScreen.Group g) {
        Transform t = new Transform();
        t.scale = g.scale;
        if (g == InspectScreen.Group.NOTE || g == InspectScreen.Group.DPOR) {
            t.depth = 0.2f;
        }
        if (g == InspectScreen.Group.TACZ) {
            t.fit = true;
            t.yaw = 20.0f;
            t.pitch = 10.0f;
        }
        return t;
    }

    /**
     * The key an item is configured by: its id, plus the gun / attachment / ammo id for TACZ items (all TACZ guns are one item).
     */
    public static String keyOf(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String key = id == null ? "minecraft:air" : id.toString();
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            for (String sub : new String[]{"GunId", "AttachmentId", "AmmoId"}) {
                if (tag.contains(sub, 8)) {
                    return key + "#" + tag.getString(sub);
                }
            }
        }
        return key;
    }

    /** The bare item id, which in a group takes every variant of the item. */
    public static String idOf(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "minecraft:air" : id.toString();
    }

    private static boolean matches(String entry, String key) {
        // a plain item id in a group takes every variant of that item
        return entry.equals(key) || (entry.indexOf('#') < 0 && key.startsWith(entry + "#"));
    }

    public static boolean groupMember(Group group, String key) {
        for (String entry : group.items) {
            if (matches(entry, key)) {
                return true;
            }
        }
        return false;
    }

    public static Text text() {
        return data().text;
    }

    public static void resetText() {
        data().text = new Text();
    }

    public static Transform auto(InspectScreen.Group g) {
        return data().auto.computeIfAbsent(autoKey(g), k -> defaultAuto(g));
    }

    public static void resetAuto(InspectScreen.Group g) {
        data().auto.put(autoKey(g), defaultAuto(g));
    }

    public static Transform item(String key) {
        return data().items.get(key);
    }

    public static Transform createItem(String key, Transform from) {
        Transform t = from.copy();
        data().items.put(key, t);
        return t;
    }

    public static void removeItem(String key) {
        data().items.remove(key);
    }

    public static List<Group> groups() {
        return data().groups;
    }

    public static Group groupOf(String key) {
        for (Group g : data().groups) {
            for (String entry : g.items) {
                if (matches(entry, key)) {
                    return g;
                }
            }
        }
        return null;
    }

    /** The transform the item is shown with. */
    public static Transform resolve(ItemStack stack, InspectScreen.Group auto) {
        String key = keyOf(stack);
        Transform own = item(key);
        if (own != null) {
            return own;
        }
        Group group = groupOf(key);
        return group != null && group.transform != null ? group.transform : auto(auto);
    }
}
