package com.voltyx.efw.sins.stat;

import java.util.HashMap;
import java.util.Map;

/**
 * Everything the SEVEN progression can change. A system asks {@link SinStats#mult} (percent stats: 1 + sum) or
 * {@link SinStats#value} (flat stats: the sum) at the one place where it uses its number.
 * INSTANT stats are not read by anyone: they happen once when the trigger of their effect fires.
 */
public enum Stat {
    // ---- stamina and roll (stamina/, roll/, client/RollClient)
    STAMINA_MAX("stamina.max", Kind.PERCENT, "Запас выносливости"),
    STAMINA_REGEN("stamina.regen", Kind.PERCENT, "Восстановление выносливости"),
    STAMINA_SPRINT_COST("stamina.sprint_cost", Kind.PERCENT, "Расход выносливости на бег"),
    STAMINA_JUMP_COST("stamina.jump_cost", Kind.PERCENT, "Расход выносливости на прыжок"),
    STAMINA_ATTACK_COST("stamina.attack_cost", Kind.PERCENT, "Расход выносливости на удар после кувырка"),
    ROLL_COST("roll.cost", Kind.PERCENT, "Цена кувырка"),
    ROLL_COOLDOWN("roll.cooldown", Kind.PERCENT, "Перерыв между кувырками"),
    ROLL_DISTANCE("roll.distance", Kind.PERCENT, "Дальность кувырка"),
    ROLL_GUARD("roll.guard", Kind.FLAT, "Защита после кувырка, тиков"),
    // ---- heartbeat (client, heartbeat/)
    PULSE_REST("pulse.rest", Kind.FLAT, "Пульс в покое, уд/мин"),
    PULSE_FEAR("pulse.fear", Kind.PERCENT, "Испуг от тварей"),
    PULSE_DECAY("pulse.decay", Kind.PERCENT, "Скорость, с которой пульс остывает"),
    PULSE_HURT("pulse.hurt", Kind.PERCENT, "Скачок пульса от боли"),
    // ---- call of the hive (coth/)
    COTH_RESIST("coth.resist", Kind.FLAT, "Шанс, что Зов Улья не усилится"),
    COTH_VOMIT("coth.vomit", Kind.PERCENT, "Перерыв между приступами рвоты"),
    // ---- doll and blood (doll/, blood/)
    DOLL_READY("doll.ready", Kind.FLAT, "Кукле нужно крови, %"),
    DOLL_BUFF_TIME("doll.buff_time", Kind.PERCENT, "Длительность «Чувства вины»"),
    DOLL_MELEE("doll.melee", Kind.FLAT, "Ближний урон в «Чувстве вины»"),
    DOLL_KILL_EXTEND("doll.kill_extend", Kind.PERCENT, "Продление «Чувства вины» за убийство"),
    /** 1 or more: after the landing blow the chain throws the thrower up and drags him through the creature again (hook/). */
    HOOK_COMBO("hook.combo", Kind.FLAT, "Цепь: второй заход сквозь цель"),
    /** 1 or more: the chain hook stays in the off hand with a two-handed weapon (Better Combat empties it otherwise). */
    HOOK_TWO_HANDED("hook.two_handed", Kind.FLAT, "Цепь: при двуручном оружии"),
    BLOOD_GAIN("blood.gain", Kind.PERCENT, "Кровь липнет"),
    // ---- looting (loot/)
    LOOT_SPEED("loot.speed", Kind.PERCENT, "Скорость обыска"),
    LOOT_BONUS("loot.bonus", Kind.FLAT, "Шанс найти больше"),
    LOCKPICK_WEAR("lockpick.wear", Kind.PERCENT, "Износ отмычки"),
    // ---- combat
    MELEE_DAMAGE("melee.damage", Kind.PERCENT, "Ближний урон"),
    GUN_DAMAGE("gun.damage", Kind.PERCENT, "Урон огнестрела"),
    HEADSHOT_DAMAGE("headshot.damage", Kind.PERCENT, "Урон в голову"),
    GUN_RELOAD("gun.reload", Kind.PERCENT, "Скорость перезарядки"),
    GUN_SPREAD("gun.spread", Kind.PERCENT, "Разброс"),
    GUN_RECOIL("gun.recoil", Kind.PERCENT, "Отдача"),
    DAMAGE_TAKEN("damage.taken", Kind.PERCENT, "Получаемый урон"),
    DAMAGE_FALL("damage.fall", Kind.PERCENT, "Урон от падения"),
    CHEAT_DEATH("cheat_death", Kind.FLAT, "Смертельный удар оставляет 1 HP"),
    // ---- body
    HEAL_POWER("heal.power", Kind.PERCENT, "Лечение"),
    FOOD_EXHAUSTION("food.exhaustion", Kind.PERCENT, "Расход сытости"),
    FOOD_RESTORE("food.restore", Kind.PERCENT, "Сытость от еды"),
    EFFECT_STIM("effect.stim", Kind.PERCENT, "Действие стимуляторов"),
    EFFECT_ANTIBIOTIC("effect.antibiotic", Kind.PERCENT, "Действие антибиотика"),
    VISIBILITY("visibility", Kind.PERCENT, "Заметность"),
    WEAR_WEAPON("wear.weapon", Kind.PERCENT, "Износ оружия"),
    WEAR_TOOL("wear.tool", Kind.PERCENT, "Износ инструментов"),
    // ---- attributes (applied as AttributeModifiers by SinHooks)
    HP_MAX("hp.max", Kind.PERCENT, "Макс. здоровье"),
    MOVE_SPEED("move.speed", Kind.PERCENT, "Скорость"),
    ATTACK_SPEED("attack.speed", Kind.PERCENT, "Скорость атаки"),
    KNOCKBACK_DEAL("knockback.deal", Kind.FLAT, "Отбрасывание"),
    KNOCKBACK_RESIST("knockback.resist", Kind.FLAT, "Устойчивость"),
    ARMOR("armor", Kind.FLAT, "Броня"),
    REACH("reach", Kind.FLAT, "Досягаемость"),
    STEP("step", Kind.FLAT, "Высота шага"),
    HEAT_RESIST("heat.resist", Kind.FLAT, "Стойкость к жаре"),
    COLD_RESIST("cold.resist", Kind.FLAT, "Стойкость к холоду"),
    BODY_RESIST("body.resist", Kind.PERCENT, "Урон по конечностям"),
    // ---- once, when the trigger fires
    HEAL("heal", Kind.INSTANT, "Залечивает, от макс. HP"),
    STAMINA_RESTORE("stamina.restore", Kind.INSTANT, "Возвращает выносливость"),
    PULSE_SHIFT("pulse.shift", Kind.INSTANT, "Пульс, уд/мин"),
    BLOOD_ADD("blood.add", Kind.INSTANT, "Кровь на тело");

    public enum Kind {
        /** Fraction: 0.15 = +15%. Read as {@code 1 + sum}. */
        PERCENT,
        /** Plain number added. */
        FLAT,
        /** Applied once by a trigger. */
        INSTANT
    }

    private static final Map<String, Stat> BY_ID = new HashMap<>();

    static {
        for (Stat s : values()) {
            BY_ID.put(s.id, s);
        }
    }

    public final String id;
    public final Kind kind;
    public final String nameRu;

    Stat(String id, Kind kind, String nameRu) {
        this.id = id;
        this.kind = kind;
        this.nameRu = nameRu;
    }

    public static Stat byId(String id) {
        return BY_ID.get(id);
    }

    /** For these a positive number is bad for the player (shown red). */
    public boolean lowerIsBetter() {
        return switch (this) {
            case STAMINA_SPRINT_COST, STAMINA_JUMP_COST, STAMINA_ATTACK_COST, ROLL_COST, ROLL_COOLDOWN, PULSE_REST, PULSE_FEAR, PULSE_HURT,
                 DOLL_READY, LOCKPICK_WEAR, GUN_SPREAD, GUN_RECOIL, DAMAGE_TAKEN, DAMAGE_FALL, FOOD_EXHAUSTION, VISIBILITY, WEAR_WEAPON,
                 WEAR_TOOL, BODY_RESIST -> true;
            default -> false;
        };
    }
}
