package com.voltyx.efw.notes.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.voltyx.efw.EFW;
import com.voltyx.efw.client.loading.LoadingConfig;
import com.voltyx.efw.client.loading.LoadingEntry;
import net.minecraft.client.Minecraft;
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
import java.util.List;

/**
 * Item descriptions of the inspect screen: config/efw/inspect_items_en.json and _ru.json (same format as the loading screen
 * items), then the loading screen items themselves (port of ItemInspectDescConfig).
 */
public final class InspectDescConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static List<LoadingEntry> en;
    private static List<LoadingEntry> ru;

    private InspectDescConfig() {
    }

    private static List<LoadingEntry> load(Path file) {
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    GSON.toJson(new ArrayList<LoadingEntry>(), w);
                }
            }
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                List<LoadingEntry> list = GSON.fromJson(r, new TypeToken<List<LoadingEntry>>() {
                }.getType());
                return list != null ? list : new ArrayList<>();
            }
        } catch (Exception e) {
            EFW.LOGGER.warn("[Inspect] cannot read {}: {}", file.getFileName(), e.toString());
            return new ArrayList<>();
        }
    }

    private static List<LoadingEntry> entries() {
        if (en == null) {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("efw");
            en = load(dir.resolve("inspect_items_en.json"));
            ru = load(dir.resolve("inspect_items_ru.json"));
        }
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        boolean russian = lang != null && lang.toLowerCase().startsWith("ru");
        return russian && !ru.isEmpty() ? ru : en;
    }

    public static LoadingEntry entryFor(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            return null;
        }
        LoadingEntry found = find(entries(), id);
        return found != null ? found : find(LoadingConfig.entries(), id);
    }

    private static LoadingEntry find(List<LoadingEntry> list, ResourceLocation id) {
        String full = id.toString().toLowerCase();
        String path = id.getPath().toLowerCase();
        for (LoadingEntry entry : list) {
            if (entry == null || entry.item == null) {
                continue;
            }
            String item = entry.item.toLowerCase();
            if (item.equals(full) || item.equals(path) || full.endsWith(":" + item)) {
                return entry;
            }
        }
        return null;
    }
}
