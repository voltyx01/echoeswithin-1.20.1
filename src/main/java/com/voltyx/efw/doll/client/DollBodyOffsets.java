package com.voltyx.efw.doll.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.voltyx.efw.EFW;
import com.voltyx.efw.tech.CuriosCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Where the doll hangs on the belt for each kind of chest armour or backpack (mwccf DollBodySettings). The values live in
 * config/efw/doll_body_offsets.json (created from the defaults of the 1.12 pack on the first start); the armour is recognised by
 * the name of its item, so the armour of any mod id is found.
 */
public final class DollBodyOffsets {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Offset> OFFSETS = new LinkedHashMap<>();
    private static boolean loaded;
    private static final Offset ZERO = new Offset();

    public static final class Offset {
        public float posX, posY, posZ, rotX, rotY, rotZ;
        public float scale = 1.0F;
    }

    private DollBodyOffsets() {
    }

    private static void load() {
        loaded = true;
        try {
            Path file = FMLPaths.CONFIGDIR.get().resolve("efw").resolve("doll_body_offsets.json");
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                try (InputStream in = DollBodyOffsets.class.getResourceAsStream("/assets/efw/doll_body_offsets_default.json")) {
                    if (in != null) {
                        Files.write(file, in.readAllBytes());
                    }
                }
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Map<String, Offset> read = GSON.fromJson(reader, new TypeToken<Map<String, Offset>>() {
                }.getType());
                if (read != null) {
                    read.forEach((k, v) -> OFFSETS.put(k.toUpperCase(Locale.ROOT), v));
                }
            }
        } catch (Exception e) {
            EFW.LOGGER.warn("[Doll] cannot read doll_body_offsets.json: {}", e.toString());
        }
    }

    public static Offset forPlayer(Player player) {
        if (!loaded) {
            load();
        }
        Offset o = OFFSETS.get(category(player));
        return o != null ? o : ZERO;
    }

    /** For a dead body: only its chest armour is known, a backpack in a Curios slot is not looked at. */
    public static Offset forChest(ItemStack chest) {
        if (!loaded) {
            load();
        }
        String path = path(chest);
        Offset o = OFFSETS.get(path.contains("backpack") ? "BACKPACK" : armorCategory(path));
        return o != null ? o : ZERO;
    }

    private static String path(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
    }

    private static String category(Player player) {
        String chest = path(player.getItemBySlot(EquipmentSlot.CHEST));
        String armor = armorCategory(chest);
        boolean wornBackpack = chest.contains("backpack");
        boolean curioBackpack = !CuriosCompat.findFirst(player, s -> path(s).contains("backpack")).isEmpty();
        if (wornBackpack || (curioBackpack && (chest.isEmpty() || armor.equals("FIRE_FIGHTER") || armor.equals("NONE")))) {
            return "BACKPACK";
        }
        return armor;
    }

    private static String armorCategory(String n) {
        if (n.isEmpty()) {
            return "NONE";
        }
        if (n.contains("exo_heavy")) return "EXO_HEAVY";
        if (n.contains("exo")) return "EXO";
        if (n.contains("juggernaut")) return "JUGGERNAUT";
        if (n.contains("rockie")) return "ROCKIE";
        if (n.contains("recluit") || n.contains("recruit")) return "RECLUIT";
        if (n.contains("hazmat")) return "HAZMAT";
        if (n.contains("military")) return "MILITARY";
        if (n.contains("police")) return "POLICE";
        if (n.contains("fire_fighter") || n.contains("firefighter")) return "FIRE_FIGHTER";
        if (n.contains("ghillie") || n.contains("guillie")) return "GUILLIE";
        if (n.contains("plate_carrier")) return "PLATE_CARRIER";
        if (n.contains("swat")) return "SWAT";
        if (n.contains("riot")) return "RIOT";
        if (n.contains("steel_chestplate")) return "STEEL";
        if (n.contains("titanium_chestplate")) return "TITANIUM";
        return "NONE";
    }
}
