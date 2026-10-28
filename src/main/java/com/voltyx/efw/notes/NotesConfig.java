package com.voltyx.efw.notes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.voltyx.efw.EFW;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Texts of the notes, by id: config/efw/notes.json, notes_ru.json, quest_notes.json, quest_notes_ru.json (port of the 1.12
 * NotesConfig). Loaded on both sides: the server picks random ids, the client shows the texts.
 */
public final class NotesConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, String> notesEn = new LinkedHashMap<>();
    private static Map<String, String> notesRu = new LinkedHashMap<>();
    private static Map<String, String> questEn = new LinkedHashMap<>();
    private static Map<String, String> questRu = new LinkedHashMap<>();
    private static boolean loaded;

    private NotesConfig() {
    }

    public static synchronized void load() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("efw");
        notesEn = loadFile(dir.resolve("notes.json"), false, false);
        notesRu = loadFile(dir.resolve("notes_ru.json"), true, false);
        questEn = loadFile(dir.resolve("quest_notes.json"), false, true);
        questRu = loadFile(dir.resolve("quest_notes_ru.json"), true, true);
        loaded = true;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private static Map<String, String> loadFile(Path file, boolean russian, boolean quest) {
        Map<String, String> data = new LinkedHashMap<>();
        try {
            if (!Files.exists(file)) {
                if (quest) {
                    if (russian) {
                        data.put("1", "Квестовая записка #1: В бункере обнаружены следы выживших. Координаты ведут на восток (RU).");
                        data.put("2", "Квестовая записка #2: Пароль от гермодвери: 7492. Не сообщайте посторонним (RU).");
                    } else {
                        data.put("1", "Quest Note #1: Traces of survivors discovered in the bunker. Coordinates point east (EN).");
                        data.put("2", "Quest Note #2: Airtight door passcode: 7492. Do not disclose (EN).");
                    }
                } else if (russian) {
                    data.put("1", "Первая страница дневника (RU)");
                    data.put("2", "Запись в спешке (RU)");
                } else {
                    data.put("1", "First page of the ancient diary (EN)");
                    data.put("2", "Someone left this note in a hurry (EN)");
                }
                Files.createDirectories(file.getParent());
                try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    GSON.toJson(data, w);
                }
                return data;
            }
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Map<String, String> read = GSON.fromJson(r, new TypeToken<LinkedHashMap<String, String>>() {
                }.getType());
                return read != null ? read : data;
            }
        } catch (Exception e) {
            EFW.LOGGER.warn("[Notes] cannot read {}: {}", file.getFileName(), e.toString());
            return data;
        }
    }

    public static String getText(int id, boolean quest) {
        ensureLoaded();
        String lang = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> com.voltyx.efw.notes.client.NotesClient::currentLanguage);
        boolean ru = lang != null && lang.toLowerCase().startsWith("ru");
        Map<String, String> active = quest ? (ru ? questRu : questEn) : (ru ? notesRu : notesEn);
        Map<String, String> fallback = quest ? questEn : notesEn;
        String key = String.valueOf(id);
        if (active.containsKey(key)) {
            return active.get(key);
        }
        if (fallback.containsKey(key)) {
            return fallback.get(key);
        }
        return (quest ? "Quest Note #" : "Note #") + id + " is missing.";
    }

    public static int getEntriesCount() {
        ensureLoaded();
        return Math.max(notesEn.size(), notesRu.size());
    }

    public static List<Integer> getAvailableIds(boolean quest) {
        ensureLoaded();
        Set<Integer> ids = new TreeSet<>();
        for (String k : (quest ? questEn : notesEn).keySet()) {
            parseInto(ids, k);
        }
        for (String k : (quest ? questRu : notesRu).keySet()) {
            parseInto(ids, k);
        }
        if (ids.isEmpty()) {
            ids.add(1);
        }
        return new ArrayList<>(ids);
    }

    private static void parseInto(Set<Integer> ids, String key) {
        try {
            ids.add(Integer.parseInt(key));
        } catch (NumberFormatException ignored) {
            // non-numeric keys are not note ids
        }
    }
}
