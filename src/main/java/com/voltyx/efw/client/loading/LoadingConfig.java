package com.voltyx.efw.client.loading;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.voltyx.efw.EFW;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads config/efw/loading_screen_items_en.json and _ru.json (written with defaults when missing). */
public final class LoadingConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static List<LoadingEntry> en;
    private static List<LoadingEntry> ru;

    private LoadingConfig() {
    }

    /** Entries for the selected game language (Russian when it starts with "ru", otherwise English). */
    public static List<LoadingEntry> entries() {
        if (en == null) {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("efw");
            en = load(dir.resolve("loading_screen_items_en.json"), true);
            ru = load(dir.resolve("loading_screen_items_ru.json"), false);
        }
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        if (lang != null && lang.toLowerCase().startsWith("ru") && !ru.isEmpty()) {
            return ru;
        }
        return en.isEmpty() ? ru : en;
    }

    /** Forces a re-read of both files on the next use. */
    public static void reload() {
        en = null;
        ru = null;
    }

    private static List<LoadingEntry> load(Path file, boolean english) {
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    GSON.toJson(defaults(english), w);
                }
            }
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                List<LoadingEntry> list = GSON.fromJson(r, new TypeToken<List<LoadingEntry>>() {
                }.getType());
                if (list != null) {
                    EFW.LOGGER.info("[LoadingScreen] {} items from {}", list.size(), file.getFileName());
                    return list;
                }
            }
        } catch (IOException | RuntimeException e) {
            EFW.LOGGER.warn("[LoadingScreen] cannot read {}: {}", file.getFileName(), e.toString());
        }
        return new ArrayList<>();
    }

    private static List<LoadingEntry> defaults(boolean english) {
        List<LoadingEntry> list = new ArrayList<>();
        list.add(entry("minecraft:iron_sword",
                english ? "A steel blade found among the ruins." : "Стальной клинок, найденный среди руин.",
                english ? "Its master did not survive. You are different." : "Его хозяин не выжил. Ты — другое дело."));
        list.add(entry("minecraft:golden_apple",
                english ? "A rarity even before the catastrophe." : "Редкость даже до катастрофы.",
                english ? "They say this was worth a fortune in the old world." : "Говорят, в старом мире это стоило целое состояние."));
        return list;
    }

    private static LoadingEntry entry(String item, String description, String lore) {
        LoadingEntry e = new LoadingEntry();
        e.item = item;
        e.description = description;
        e.lore = lore;
        return e;
    }
}
