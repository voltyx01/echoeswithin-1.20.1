package com.voltyx.efw.sins.stat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.voltyx.efw.EFW;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One effect of a card, skill or manual rank:
 * <pre>{"stat": "move.speed", "value": 0.1, "when": ["hp<0.5"], "after": "roll", "for": 3, "cooldown": 8}</pre>
 * {@code when}: all conditions must hold. {@code after}/{@code for}: the effect works only for that many seconds after the trigger
 * (an INSTANT stat happens once, at the trigger). {@code cooldown}: after the effect was used (a hit it made stronger, a death it
 * stopped, an instant one that fired) it rests that many seconds.
 */
public record SinEffect(String uid, Stat stat, double value, List<Cond> when, @Nullable Trigger after, double forSeconds,
                        double cooldown) {

    @Nullable
    public static SinEffect parse(String uid, JsonObject o) {
        Stat stat = Stat.byId(str(o, "stat"));
        if (stat == null) {
            EFW.LOGGER.warn("SEVEN: unknown stat '{}' in {}", str(o, "stat"), uid);
            return null;
        }
        List<Cond> when = new ArrayList<>();
        if (o.has("when")) {
            JsonElement w = o.get("when");
            JsonArray arr = w.isJsonArray() ? w.getAsJsonArray() : new JsonArray();
            if (!w.isJsonArray()) {
                arr.add(w);
            }
            for (JsonElement e : arr) {
                Cond c = Cond.parse(e.getAsString());
                if (c == null) {
                    EFW.LOGGER.warn("SEVEN: unknown condition '{}' in {}", e.getAsString(), uid);
                    return null;
                }
                when.add(c);
            }
        }
        Trigger after = null;
        if (o.has("after")) {
            after = Trigger.byId(str(o, "after"));
            if (after == null) {
                EFW.LOGGER.warn("SEVEN: unknown trigger '{}' in {}", str(o, "after"), uid);
                return null;
            }
        }
        double value = o.has("value") ? o.get("value").getAsDouble() : 0.0;
        double forSeconds = o.has("for") ? o.get("for").getAsDouble() : 0.0;
        double cooldown = o.has("cooldown") ? o.get("cooldown").getAsDouble() : 0.0;
        if (stat.kind == Stat.Kind.INSTANT && after == null) {
            EFW.LOGGER.warn("SEVEN: instant stat '{}' without 'after' in {}", stat.id, uid);
            return null;
        }
        return new SinEffect(uid, stat, value, List.copyOf(when), after, forSeconds, cooldown);
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }

    public SinEffect scaled(double factor) {
        return factor == 1.0 ? this : new SinEffect(uid, stat, value * factor, when, after, forSeconds, cooldown);
    }

    public boolean needsTarget() {
        for (Cond c : when) {
            if (c.needsTarget()) {
                return true;
            }
        }
        return false;
    }

    public boolean conditionsHold(Player p, SinRuntime rt, @Nullable LivingEntity target) {
        for (Cond c : when) {
            if (!c.test(p, rt, target)) {
                return false;
            }
        }
        return true;
    }

    /** The effect works through Saya's doll (veiled until the player has held it). */
    public boolean isAboutDoll() {
        if (after == Trigger.DOLL || stat.id.startsWith("doll.")) {
            return true;
        }
        for (Cond c : when) {
            if (c.isAboutDoll()) {
                return true;
            }
        }
        return false;
    }

    /** The line shown instead of an effect the player may not know about yet. */
    public static final String VEILED = "??? Что-то, чего вы пока не помните.";

    /**
     * A plain sentence: «Урон в ближнем бою выше на 20%, пока в руках холодное оружие.»,
     * «После убийства в ближнем бою: лечит на 4% от максимума здоровья (не чаще раза в 3 с).»
     */
    public String describe() {
        String what = phrase();
        StringBuilder b = new StringBuilder();
        if (stat.kind == Stat.Kind.INSTANT) {
            b.append(cap(after == null ? "" : after.textRu));
            if (!when.isEmpty()) {
                b.append(", если ").append(conditions());
            }
            b.append(": ").append(what);
        } else {
            b.append(cap(what));
            if (after != null) {
                b.append(forSeconds > 0 ? " на " + duration(forSeconds) + " " + after.textRu : " " + after.textRu);
            }
            if (!when.isEmpty()) {
                b.append(", пока ").append(conditions());
            }
        }
        if (cooldown > 0) {
            b.append(" (не чаще раза в ").append(duration(cooldown)).append(")");
        }
        return b.append('.').toString();
    }

    private String conditions() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < when.size(); i++) {
            if (i > 0) {
                b.append(i == when.size() - 1 ? " и " : ", ");
            }
            String c = when.get(i).describe();
            // «вы целитесь и не двигаетесь», not «вы целитесь и вы не двигаетесь»
            if (i > 0 && c.startsWith("вы ") && when.get(i - 1).describe().startsWith("вы ")) {
                c = c.substring(3);
            }
            b.append(c);
        }
        return b.toString();
    }

    /** What the effect does, without when. */
    private String phrase() {
        boolean up = value >= 0;
        String p = pctText(Math.abs(value));
        String n = num(Math.abs(value));
        return switch (stat) {
            case STAMINA_MAX -> "запас выносливости " + (up ? "больше" : "меньше") + " на " + p;
            case STAMINA_REGEN -> "выносливость восстанавливается на " + p + (up ? " быстрее" : " медленнее");
            case STAMINA_SPRINT_COST -> "бег тратит на " + p + (up ? " больше" : " меньше") + " выносливости";
            case STAMINA_JUMP_COST -> "прыжок тратит на " + p + (up ? " больше" : " меньше") + " выносливости";
            case STAMINA_ATTACK_COST -> "удар сразу после кувырка тратит на " + p + (up ? " больше" : " меньше") + " выносливости";
            case ROLL_COST -> "кувырок тратит на " + p + (up ? " больше" : " меньше") + " выносливости";
            case ROLL_COOLDOWN -> "кувырок перезаряжается на " + p + (up ? " дольше" : " быстрее");
            case ROLL_DISTANCE -> "кувырок на " + p + (up ? " дальше" : " короче");
            case ROLL_GUARD -> "защита после кувырка держится на " + duration(Math.abs(value) / 20.0) + (up ? " дольше" : " меньше");
            case PULSE_REST -> "пульс в покое " + (up ? "выше" : "ниже") + " на " + n + " уд/мин";
            case PULSE_FEAR -> "пульс от вида тварей растёт на " + p + (up ? " быстрее" : " медленнее");
            case PULSE_DECAY -> "после испуга пульс успокаивается на " + p + (up ? " быстрее" : " медленнее");
            case PULSE_HURT -> "от боли пульс подскакивает на " + p + (up ? " сильнее" : " слабее");
            case COTH_RESIST -> up ? p + " шанс, что Зов Улья не усилится" : "Зов Улья усиливается на " + p + " легче";
            case COTH_VOMIT -> "приступы рвоты от Зова Улья на " + p + (up ? " реже" : " чаще");
            case DOLL_READY -> "кукле нужно на " + n + "%" + (up ? " больше" : " меньше") + " крови на вас";
            case DOLL_BUFF_TIME -> "«Чувство вины» длится на " + p + (up ? " дольше" : " короче");
            case DOLL_MELEE -> "в «Чувстве вины» ближний урон " + (up ? "выше" : "ниже") + " ещё на " + p;
            case DOLL_KILL_EXTEND -> "убийства продлевают «Чувство вины» на " + p + (up ? " дольше" : " меньше");
            case HOOK_TWO_HANDED -> up ? "цепь с крюком можно держать в левой руке при двуручном оружии" : "цепь с крюком не держится при двуручном оружии";
            case HOOK_COMBO -> up ? "цепь после удара подбрасывает и тащит сквозь цель: второй удар и кувырок" : "цепь отпускает сразу после удара";
            case BLOOD_GAIN -> "чужая кровь пачкает вас на " + p + (up ? " сильнее" : " слабее");
            case LOOT_SPEED -> "обыск на " + p + (up ? " быстрее" : " медленнее");
            case LOOT_BONUS -> up ? p + " шанс найти в обысканном лишний предмет" : "на " + p + " меньше шанс найти лишний предмет";
            case LOCKPICK_WEAR -> "отмычки изнашиваются на " + p + (up ? " быстрее" : " медленнее");
            case MELEE_DAMAGE -> "урон в ближнем бою " + (up ? "выше" : "ниже") + " на " + p;
            case GUN_DAMAGE -> "урон из огнестрела " + (up ? "выше" : "ниже") + " на " + p;
            case HEADSHOT_DAMAGE -> "урон при попадании в голову " + (up ? "выше" : "ниже") + " на " + p;
            case GUN_RELOAD -> "перезарядка на " + p + (up ? " быстрее" : " медленнее");
            case GUN_SPREAD -> "разброс пуль на " + p + (up ? " больше" : " меньше");
            case GUN_RECOIL -> "отдача на " + p + (up ? " сильнее" : " слабее");
            case DAMAGE_TAKEN -> "вы получаете на " + p + (up ? " больше" : " меньше") + " урона";
            case DAMAGE_FALL -> "урон от падения на " + p + (up ? " больше" : " меньше");
            case CHEAT_DEATH -> "смертельный удар оставляет вас с полусердцем";
            case HEAL_POWER -> "лечение на " + p + (up ? " сильнее" : " слабее");
            case FOOD_EXHAUSTION -> "голод наступает на " + p + (up ? " быстрее" : " медленнее");
            case FOOD_RESTORE -> "еда насыщает на " + p + (up ? " больше" : " меньше");
            case EFFECT_STIM -> "стимуляторы и обезболивающие действуют на " + p + (up ? " дольше" : " короче");
            case EFFECT_ANTIBIOTIC -> "антибиотик действует на " + p + (up ? " дольше" : " короче");
            case VISIBILITY -> "мобы замечают вас с расстояния на " + p + (up ? " больше" : " меньше");
            case WEAR_WEAPON -> "оружие изнашивается на " + p + (up ? " быстрее" : " медленнее");
            case WEAR_TOOL -> "инструменты изнашиваются на " + p + (up ? " быстрее" : " медленнее");
            case HP_MAX -> "максимум здоровья " + (up ? "больше" : "меньше") + " на " + p;
            case MOVE_SPEED -> "вы двигаетесь на " + p + (up ? " быстрее" : " медленнее");
            case ATTACK_SPEED -> "вы бьёте на " + p + (up ? " чаще" : " реже");
            case KNOCKBACK_DEAL -> "ваши удары отбрасывают врагов " + (up ? "сильнее" : "слабее") + " (" + (up ? "+" : "−") + n + ")";
            case KNOCKBACK_RESIST -> "вас на " + p + (up ? " труднее" : " легче") + " сбить с ног и отбросить";
            case ARMOR -> (up ? "+" : "−") + n + " к броне";
            case REACH -> "вы дотягиваетесь на " + n + " блока" + (up ? " дальше" : " ближе");
            case STEP -> up ? "вы без прыжка шагаете на уступы выше на " + n + " блока" : "шаг ниже на " + n + " блока";
            case HEAT_RESIST -> "жара переносится " + (up ? "легче" : "тяжелее") + " (" + (up ? "+" : "−") + n + "°)";
            case COLD_RESIST -> "холод переносится " + (up ? "легче" : "тяжелее") + " (" + (up ? "+" : "−") + n + "°)";
            case BODY_RESIST -> "руки, ноги и тело получают на " + p + (up ? " больше" : " меньше") + " урона";
            case HEAL -> (up ? "лечит на " : "отнимает ") + p + " от максимума здоровья";
            case STAMINA_RESTORE -> (up ? "возвращает " : "отнимает ") + p + " выносливости";
            case PULSE_SHIFT -> "пульс " + (up ? "подскакивает" : "падает") + " на " + n + " уд/мин";
            case BLOOD_ADD -> "вас забрызгивает кровью (" + (up ? "+" : "−") + p + ")";
        };
    }

    private static String pctText(double v) {
        return Math.round(v * 100) + "%";
    }

    private static String duration(double seconds) {
        if (seconds >= 120 && seconds % 60 == 0) {
            return (long) (seconds / 60) + " мин";
        }
        return num(seconds) + " с";
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Short value for compact places: "+15%". */
    public String valueText() {
        return switch (stat.kind) {
            case PERCENT -> (value >= 0 ? "+" : "−") + Math.round(Math.abs(value) * 100) + "%";
            case FLAT -> stat == Stat.COTH_RESIST || stat == Stat.LOOT_BONUS
                    ? (value >= 0 ? "+" : "−") + Math.round(Math.abs(value) * 100) + "%"
                    : stat == Stat.CHEAT_DEATH ? "" : (value >= 0 ? "+" : "−") + num(Math.abs(value));
            case INSTANT -> stat == Stat.PULSE_SHIFT ? (value >= 0 ? "+" : "−") + num(Math.abs(value))
                    : (value >= 0 ? "+" : "−") + Math.round(Math.abs(value) * 100) + "%";
        };
    }

    /** Good for the player (gold) or bad (blood). */
    public boolean isGood() {
        boolean up = value >= 0;
        return stat.lowerIsBetter() || stat == Stat.PULSE_SHIFT ? !up : up;
    }

    private static String num(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 100) / 100.0);
    }
}
