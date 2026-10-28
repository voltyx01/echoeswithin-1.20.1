package com.voltyx.efw.sins.stat;

import com.voltyx.efw.blood.BloodManager;
import com.voltyx.efw.coth.Coth;
import com.voltyx.efw.compat.LsoSurvival;
import com.voltyx.efw.compat.TaczCompat;
import com.voltyx.efw.doll.DollContent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * One condition of an effect, written in the json as a short string: {@code "hp<0.5"}, {@code "pulse>110"}, {@code "crouch"},
 * {@code "still:5"}, {@code "ally:12"}, {@code "holding:melee"}, {@code "!aim"}. Unknown strings are false (and logged when loaded).
 */
public final class Cond {
    private enum Type {
        HP, PULSE, STAMINA, FOOD, BLOOD, COTH, AMMO, TARGET_HP,
        CROUCH, SPRINT, AIM, DARK, NIGHT, DOLL, SOLO, WET, COLD, HOT, TARGET_STRONGER,
        STILL, NOHURT, ALLY, HOLDING
    }

    private final Type type;
    private final boolean negate;
    /** For comparisons: true is "less than". */
    private final boolean less;
    private final double number;
    private final String arg;

    private Cond(Type type, boolean negate, boolean less, double number, String arg) {
        this.type = type;
        this.negate = negate;
        this.less = less;
        this.number = number;
        this.arg = arg;
    }

    /** @return null when the string is not a known condition */
    @Nullable
    public static Cond parse(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        boolean negate = s.startsWith("!");
        if (negate) {
            s = s.substring(1);
        }
        try {
            int cmp = Math.max(s.indexOf('<'), s.indexOf('>'));
            if (cmp > 0) {
                Type t = switch (s.substring(0, cmp)) {
                    case "hp" -> Type.HP;
                    case "pulse" -> Type.PULSE;
                    case "stamina" -> Type.STAMINA;
                    case "food" -> Type.FOOD;
                    case "blood" -> Type.BLOOD;
                    case "coth" -> Type.COTH;
                    case "ammo" -> Type.AMMO;
                    case "target_hp" -> Type.TARGET_HP;
                    default -> null;
                };
                return t == null ? null : new Cond(t, negate, s.charAt(cmp) == '<', Double.parseDouble(s.substring(cmp + 1)), null);
            }
            int colon = s.indexOf(':');
            if (colon > 0) {
                String arg = s.substring(colon + 1);
                Type t = switch (s.substring(0, colon)) {
                    case "still" -> Type.STILL;
                    case "nohurt" -> Type.NOHURT;
                    case "ally" -> Type.ALLY;
                    case "holding" -> Type.HOLDING;
                    default -> null;
                };
                if (t == null) {
                    return null;
                }
                return new Cond(t, negate, false, t == Type.HOLDING ? 0 : Double.parseDouble(arg), arg);
            }
            Type t = switch (s) {
                case "crouch" -> Type.CROUCH;
                case "sprint" -> Type.SPRINT;
                case "aim" -> Type.AIM;
                case "dark" -> Type.DARK;
                case "night" -> Type.NIGHT;
                case "doll" -> Type.DOLL;
                case "solo" -> Type.SOLO;
                case "wet" -> Type.WET;
                case "cold" -> Type.COLD;
                case "hot" -> Type.HOT;
                case "target_stronger" -> Type.TARGET_STRONGER;
                default -> null;
            };
            return t == null ? null : new Cond(t, negate, false, 0, null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Conditions about the target are only true inside a hit (the target is given). */
    public boolean needsTarget() {
        return type == Type.TARGET_HP || type == Type.TARGET_STRONGER;
    }

    public boolean test(Player p, SinRuntime rt, @Nullable LivingEntity target) {
        return test0(p, rt, target) != negate;
    }

    private boolean cmp(double v) {
        return less ? v < number : v > number;
    }

    private boolean test0(Player p, SinRuntime rt, @Nullable LivingEntity target) {
        return switch (type) {
            case HP -> cmp(p.getHealth() / Math.max(1.0f, p.getMaxHealth()));
            case PULSE -> cmp(rt.pulse(p));
            case STAMINA -> cmp(rt.staminaFraction(p));
            case FOOD -> cmp(p.getFoodData().getFoodLevel() / 20.0);
            case BLOOD -> cmp(BloodManager.getBloodLevel(p.getUUID()));
            case COTH -> cmp(Coth.stage(p));
            case AMMO -> {
                int ammo = TaczCompat.currentAmmo(p.getMainHandItem());
                yield ammo >= 0 && cmp(ammo);
            }
            case TARGET_HP -> target != null && cmp(target.getHealth() / Math.max(1.0f, target.getMaxHealth()));
            case TARGET_STRONGER -> target != null && target.getHealth() > p.getHealth();
            case CROUCH -> p.isCrouching() || p.isVisuallyCrawling();
            case SPRINT -> p.isSprinting();
            case AIM -> TaczCompat.isAiming(p);
            case DARK -> p.level().getMaxLocalRawBrightness(p.blockPosition()) < 5;
            case NIGHT -> {
                long time = p.level().getDayTime() % 24000L;
                yield time > 13000L && time < 23000L;
            }
            case DOLL -> hasDoll(p);
            case SOLO -> !rt.allyWithin(p, 24.0);
            case WET -> p.isInWaterOrRain() || LsoSurvival.wetness(p) > LsoSurvival.WETNESS_LIMIT / 2;
            case COLD -> {
                String t = LsoSurvival.temperature(p);
                yield "COLD".equals(t) || "FROSTBITE".equals(t);
            }
            case HOT -> {
                String t = LsoSurvival.temperature(p);
                yield "HOT".equals(t) || "HEAT_STROKE".equals(t);
            }
            case STILL -> rt.stillSeconds(p) >= number;
            case NOHURT -> rt.secondsSinceHurt(p) >= number;
            case ALLY -> rt.allyWithin(p, number);
            case HOLDING -> holds(p.getMainHandItem(), arg);
        };
    }

    public static boolean hasDoll(Player p) {
        for (ItemStack s : p.getInventory().items) {
            if (!s.isEmpty() && s.getItem() == DollContent.DOLL.get()) {
                return true;
            }
        }
        return p.getOffhandItem().getItem() == DollContent.DOLL.get();
    }

    /** gun, melee, empty, or a TACZ gun type (pistol, rifle, shotgun, smg, sniper, mg, rpg). */
    static boolean holds(ItemStack stack, String what) {
        return switch (what) {
            case "empty" -> stack.isEmpty();
            case "gun" -> TaczCompat.isGun(stack);
            case "melee" -> !stack.isEmpty() && !TaczCompat.isGun(stack) && SinRuntime.isMeleeWeapon(stack);
            default -> what.equals(TaczCompat.gunType(stack));
        };
    }

    /** The doll is a secret until the player has held it: effects that depend on it stay veiled. */
    public boolean isAboutDoll() {
        return type == Type.DOLL;
    }

    /** A clause for "пока ..." / "если ...": «в руках холодное оружие», «пульс выше 110». */
    public String describe() {
        boolean lessNow = less != negate; // "not above" reads as "below" (the edge does not matter for a tooltip)
        String rel = lessNow ? "ниже" : "выше";
        String more = lessNow ? "меньше" : "больше";
        return switch (type) {
            case HP -> "здоровья " + more + " " + pct(number);
            case PULSE -> "пульс " + rel + " " + (int) number;
            case STAMINA -> "выносливости " + more + " " + pct(number);
            case FOOD -> "сытость " + rel + " " + pct(number);
            case BLOOD -> "крови на вас " + more + " " + pct(number);
            case COTH -> lessNow ? (number <= 1 ? "в вас нет Зова Улья" : "Зов Улья слабее стадии " + (int) number)
                    : (number < 1 ? "в вас Зов Улья" : "Зов Улья сильнее стадии " + (int) number);
            case AMMO -> "в магазине " + more + " " + (int) number + " " + rounds((int) number);
            case TARGET_HP -> "у цели " + more + " " + pct(number) + " здоровья";
            case TARGET_STRONGER -> negate ? "у цели не больше здоровья, чем у вас" : "у цели больше здоровья, чем у вас";
            case CROUCH -> negate ? "вы стоите в полный рост" : "вы пригнулись или лежите";
            case SPRINT -> negate ? "вы не бежите" : "вы бежите";
            case AIM -> negate ? "вы стреляете не целясь" : "вы целитесь";
            case DARK -> negate ? "вокруг светло" : "вокруг темно";
            case NIGHT -> negate ? "день" : "ночь";
            case DOLL -> negate ? "куклы нет при вас" : "кукла при вас";
            case SOLO -> negate ? "рядом есть другие игроки" : "рядом нет других игроков";
            case WET -> negate ? "вы сухие" : "вы промокли";
            case COLD -> negate ? "вы не мёрзнете" : "вы замёрзли";
            case HOT -> negate ? "вам не жарко" : "вы перегрелись";
            case STILL -> (negate ? "вы двигались за последние " : "вы не двигаетесь уже ") + fmt(number) + " с";
            case NOHURT -> (negate ? "вас ранили за последние " : "вас не ранили последние ") + fmt(number) + " с";
            case ALLY -> negate ? "в " + fmt(number) + " блоках от вас нет других игроков" : "другой игрок ближе " + fmt(number) + " блоков";
            case HOLDING -> "empty".equals(arg) ? (negate ? "руки заняты" : "руки пусты") : (negate ? "в руках не " : "в руках ") + switch (arg) {
                case "gun" -> "огнестрел";
                case "melee" -> "холодное оружие";
                case "pistol" -> "пистолет";
                case "rifle" -> "винтовка";
                case "sniper" -> "снайперская винтовка";
                case "shotgun" -> "дробовик";
                case "smg" -> "пистолет-пулемёт";
                case "mg" -> "пулемёт";
                case "rpg" -> "гранатомёт";
                default -> arg;
            };
        };
    }

    private static String rounds(int n) {
        int m10 = n % 10;
        int m100 = n % 100;
        if (m10 == 1 && m100 != 11) {
            return "патрона";
        }
        return "патронов";
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
