package com.voltyx.efw.sins.stat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.voltyx.efw.EFW;
import com.voltyx.efw.sins.SinType;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The content of SEVEN: cards, the skill tree of every sin, the manual ranks and the numbers of the progression. Lives as json in
 * {@code config/efw/sins/} (the defaults from the jar are copied there once); the server sends its copy to every client on login,
 * so the server's files are what counts. {@code /seven reload} reads them again.
 */
public final class SinDefs {
    public static final String[] FILES = {"common.json", "manuals.json", "wrath.json", "pride.json", "lust.json", "envy.json",
            "gluttony.json", "greed.json", "sloth.json"};
    public static final int BRANCHES = 3;
    public static final int DEPTH = 4;
    public static final int NODES = 1 + BRANCHES * DEPTH;
    public static final int MANUALS = 5;
    public static final int MANUAL_RANKS = 5;

    /** {@code veil}: the text shown instead of {@code text} while the player does not know about the doll (empty: the same). */
    public record CardDef(String id, String name, String text, String veil, @Nullable SinType sin, boolean grace, List<SinEffect> buffs,
                          List<SinEffect> costs) {
        public String text(boolean knowsDoll) {
            return knowsDoll || veil.isEmpty() ? text : veil;
        }

        public boolean isAboutDoll() {
            return buffs.stream().anyMatch(SinEffect::isAboutDoll);
        }
    }

    public record SkillDef(int node, String name, String text, String veil, List<SinEffect> effects) {
        public String text(boolean knowsDoll) {
            return knowsDoll || veil.isEmpty() ? text : veil;
        }
    }

    public record RankDef(String text, List<SinEffect> effects) {
    }

    public record SinDef(SinType sin, String focus, String price, SkillDef[] nodes, List<CardDef> cards) {
    }

    // ---- progression numbers (common.json "settings")
    public int maxPages = 20;
    public List<Integer> pointPages = List.of(1, 3, 5, 7, 9, 11, 13, 15, 17, 18, 19, 20);
    public List<Integer> gracePages = List.of(5, 10, 15, 20);
    public int maxRank = 3;
    public double[] rankBuff = {1.0, 1.6, 2.2};
    public double[] rankCost = {1.0, 1.4, 1.8};
    public int sinCardsOffered = 2;

    public final Map<String, CardDef> cards = new LinkedHashMap<>();
    public final List<CardDef> commonCards = new ArrayList<>();
    public final List<CardDef> graceCards = new ArrayList<>();
    public final Map<SinType, SinDef> sins = new EnumMap<>(SinType.class);
    /** [category][rank-1] */
    public final RankDef[][] manuals = new RankDef[MANUALS][MANUAL_RANKS];

    private static SinDefs current = new SinDefs();
    /** The files as read, sent to clients as they are. */
    private static String bundle = "{}";
    private static int generation;

    public static SinDefs get() {
        return current;
    }

    /** Changes when the content is read again (the effect caches rebuild). */
    public static int generation() {
        return generation;
    }

    public static String bundle() {
        return bundle;
    }

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve("efw").resolve("sins");
    }

    /**
     * Server start and /seven reload: bring the files up to the defaults of this jar, read the folder. A file nobody edited follows
     * the jar (its last shipped default is kept in {@code .defaults/}); an edited one is left alone. @return a problem, or null
     */
    @Nullable
    public static String loadFromConfig() {
        JsonObject files = new JsonObject();
        try {
            Files.createDirectories(dir().resolve(".defaults"));
            for (String name : FILES) {
                Path file = dir().resolve(name);
                updateDefault(name, file);
                if (Files.exists(file)) {
                    files.addProperty(name, Files.readString(file, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            EFW.LOGGER.error("SEVEN: cannot read {}", dir(), e);
            return e.getMessage();
        }
        return apply(files.toString());
    }

    private static void updateDefault(String name, Path file) throws IOException {
        String shipped;
        try (InputStream in = SinDefs.class.getResourceAsStream("/efw_sins/" + name)) {
            if (in == null) {
                return;
            }
            shipped = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Path last = dir().resolve(".defaults").resolve(name);
        if (!Files.exists(file)) {
            Files.writeString(file, shipped, StandardCharsets.UTF_8);
        } else {
            String current = Files.readString(file, StandardCharsets.UTF_8);
            if (!current.equals(shipped)) {
                if (!Files.exists(last)) {
                    // from before this tracking: take the new default, keep the old file next to it
                    Files.writeString(dir().resolve(name + ".bak"), current, StandardCharsets.UTF_8);
                    Files.writeString(file, shipped, StandardCharsets.UTF_8);
                } else if (current.equals(Files.readString(last, StandardCharsets.UTF_8))) {
                    Files.writeString(file, shipped, StandardCharsets.UTF_8);
                } else {
                    EFW.LOGGER.warn("SEVEN: {} was edited, the new default of the mod is not applied (see .defaults/{})", name, name);
                }
            }
        }
        Files.writeString(last, shipped, StandardCharsets.UTF_8);
    }

    /** The client got the server's files. */
    @Nullable
    public static String apply(String json) {
        try {
            JsonObject files = JsonParser.parseString(json).getAsJsonObject();
            SinDefs defs = new SinDefs();
            for (String name : FILES) {
                if (files.has(name)) {
                    defs.read(name, JsonParser.parseString(files.get(name).getAsString()).getAsJsonObject());
                }
            }
            current = defs;
            bundle = json;
            generation++;
            return null;
        } catch (Exception e) {
            EFW.LOGGER.error("SEVEN: broken content", e);
            return e.getMessage();
        }
    }

    private void read(String file, JsonObject o) {
        if (file.equals("common.json")) {
            if (o.has("settings")) {
                readSettings(o.getAsJsonObject("settings"));
            }
            for (JsonElement e : arr(o, "cards")) {
                CardDef c = card(e.getAsJsonObject(), null, false);
                commonCards.add(c);
                cards.put(c.id, c);
            }
            for (JsonElement e : arr(o, "grace")) {
                CardDef c = card(e.getAsJsonObject(), null, true);
                graceCards.add(c);
                cards.put(c.id, c);
            }
        } else if (file.equals("manuals.json")) {
            String[] keys = {"melee", "firearms", "meds", "lockpick", "tools"};
            for (int i = 0; i < keys.length; i++) {
                JsonArray ranks = arr(o, keys[i]);
                for (int r = 0; r < Math.min(MANUAL_RANKS, ranks.size()); r++) {
                    JsonObject ro = ranks.get(r).getAsJsonObject();
                    manuals[i][r] = new RankDef(str(ro, "text"), effects("manual:" + keys[i] + ":" + (r + 1), arr(ro, "effects")));
                }
            }
        } else {
            SinType sin = SinType.byId(file.replace(".json", ""));
            if (sin == null) {
                return;
            }
            SkillDef[] nodes = new SkillDef[NODES];
            nodes[0] = skill(sin, 0, o.getAsJsonObject("root"));
            JsonArray branches = arr(o, "branches");
            for (int b = 0; b < Math.min(BRANCHES, branches.size()); b++) {
                JsonArray branch = branches.get(b).getAsJsonArray();
                for (int d = 0; d < Math.min(DEPTH, branch.size()); d++) {
                    int node = 1 + b * DEPTH + d;
                    nodes[node] = skill(sin, node, branch.get(d).getAsJsonObject());
                }
            }
            List<CardDef> sinCards = new ArrayList<>();
            for (JsonElement e : arr(o, "cards")) {
                CardDef c = card(e.getAsJsonObject(), sin, false);
                sinCards.add(c);
                cards.put(c.id, c);
            }
            sins.put(sin, new SinDef(sin, str(o, "focus"), str(o, "price"), nodes, sinCards));
        }
    }

    private void readSettings(JsonObject s) {
        if (s.has("maxPages")) {
            maxPages = s.get("maxPages").getAsInt();
        }
        if (s.has("pointPages")) {
            pointPages = ints(s.getAsJsonArray("pointPages"));
        }
        if (s.has("gracePages")) {
            gracePages = ints(s.getAsJsonArray("gracePages"));
        }
        if (s.has("maxRank")) {
            maxRank = Math.max(1, s.get("maxRank").getAsInt());
        }
        if (s.has("rankBuff")) {
            rankBuff = doubles(s.getAsJsonArray("rankBuff"));
        }
        if (s.has("rankCost")) {
            rankCost = doubles(s.getAsJsonArray("rankCost"));
        }
        if (s.has("sinCardsOffered")) {
            sinCardsOffered = s.get("sinCardsOffered").getAsInt();
        }
    }

    private static CardDef card(JsonObject o, @Nullable SinType sin, boolean grace) {
        String id = str(o, "id");
        return new CardDef(id, str(o, "name"), str(o, "text"), str(o, "veil"), sin, grace, effects("card:" + id + ":b", arr(o, "buff")),
                effects("card:" + id + ":c", arr(o, "cost")));
    }

    private static SkillDef skill(SinType sin, int node, @Nullable JsonObject o) {
        if (o == null) {
            return new SkillDef(node, "—", "", "", List.of());
        }
        return new SkillDef(node, str(o, "name"), str(o, "text"), str(o, "veil"), effects("skill:" + sin.getId() + ":" + node, arr(o, "effects")));
    }

    private static List<SinEffect> effects(String uidPrefix, JsonArray arr) {
        List<SinEffect> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            SinEffect e = SinEffect.parse(uidPrefix + i, arr.get(i).getAsJsonObject());
            if (e != null) {
                out.add(e);
            }
        }
        return List.copyOf(out);
    }

    private static JsonArray arr(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }

    private static List<Integer> ints(JsonArray a) {
        List<Integer> out = new ArrayList<>();
        a.forEach(e -> out.add(e.getAsInt()));
        return List.copyOf(out);
    }

    private static double[] doubles(JsonArray a) {
        return new Gson().fromJson(a, double[].class);
    }

    // ---- lookups

    @Nullable
    public SkillDef skill(@Nullable SinType sin, int node) {
        SinDef def = sin == null ? null : sins.get(sin);
        return def == null || node < 0 || node >= NODES ? null : def.nodes[node];
    }

    @Nullable
    public CardDef card(String id) {
        return cards.get(id);
    }

    public double buffScale(int rank) {
        return rankBuff[Math.max(0, Math.min(rankBuff.length - 1, rank - 1))];
    }

    public double costScale(int rank) {
        return rankCost[Math.max(0, Math.min(rankCost.length - 1, rank - 1))];
    }

    /** Skill points earned with this many pages read. */
    public int pointsForPages(int pages) {
        int n = 0;
        for (int p : pointPages) {
            if (p <= pages) {
                n++;
            }
        }
        return n;
    }

    public static int nodeBranch(int node) {
        return node == 0 ? -1 : (node - 1) / DEPTH;
    }

    public static int nodeDepth(int node) {
        return node == 0 ? 0 : (node - 1) % DEPTH + 1;
    }
}
