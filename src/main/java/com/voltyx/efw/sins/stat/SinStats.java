package com.voltyx.efw.sins.stat;

import com.voltyx.efw.blood.BloodManager;
import com.voltyx.efw.sins.SinData;
import com.voltyx.efw.sins.SinNetwork;
import com.voltyx.efw.stamina.EfwConfig;
import com.voltyx.efw.stamina.PlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The one question every system asks: how much does SEVEN change this stat for this player right now. The answer is the sum of the
 * effects of the burnt skills, the cards (scaled by their rank) and the manual ranks whose conditions hold, whose trigger window is
 * open and which are not resting. Works on both sides (the client has the same data and content).
 */
public final class SinStats {
    private SinStats() {
    }

    /** The effects the player has, rebuilt only when the data or the content changed. */
    public static List<SinEffect> effects(Player p) {
        SinData data = SinData.get(p);
        if (data == null) {
            return List.of();
        }
        SinRuntime rt = SinRuntime.get(p);
        int version = data.version() * 31 + SinDefs.generation();
        if (rt.cachedVersion != version) {
            rt.cached = build(data, SinDefs.get());
            rt.cachedVersion = version;
        }
        return rt.cached;
    }

    private static List<SinEffect> build(SinData data, SinDefs defs) {
        List<SinEffect> out = new ArrayList<>();
        if (data.getChosenSin() != null) {
            for (int node = 0; node < SinDefs.NODES; node++) {
                if (data.hasSkill(node)) {
                    SinDefs.SkillDef skill = defs.skill(data.getChosenSin(), node);
                    if (skill != null) {
                        out.addAll(skill.effects());
                    }
                }
            }
        }
        for (Map.Entry<String, Integer> e : data.getCards().entrySet()) {
            SinDefs.CardDef card = defs.card(e.getKey());
            if (card == null) {
                continue;
            }
            double buff = defs.buffScale(e.getValue());
            double cost = defs.costScale(e.getValue());
            card.buffs().forEach(b -> out.add(b.scaled(buff)));
            card.costs().forEach(c -> out.add(c.scaled(cost)));
        }
        for (int cat = 0; cat < SinDefs.MANUALS; cat++) {
            for (int r = 0; r < data.manualRank(cat); r++) {
                SinDefs.RankDef rank = defs.manuals[cat][r];
                if (rank != null) {
                    out.addAll(rank.effects());
                }
            }
        }
        return List.copyOf(out);
    }

    /** Sum of the working effects of the stat (fraction for percent stats, plain number for flat ones). */
    public static double value(Player p, Stat stat) {
        return value(p, stat, null, null);
    }

    public static double value(Player p, Stat stat, @Nullable LivingEntity target, @Nullable List<SinEffect> used) {
        List<SinEffect> all = effects(p);
        if (all.isEmpty()) {
            return 0.0;
        }
        SinRuntime rt = SinRuntime.get(p);
        long now = p.level().getGameTime();
        double sum = 0.0;
        for (SinEffect e : all) {
            if (e.stat() != stat || !works(e, p, rt, target, now)) {
                continue;
            }
            sum += e.value();
            if (used != null) {
                used.add(e);
            }
        }
        return sum;
    }

    private static boolean works(SinEffect e, Player p, SinRuntime rt, @Nullable LivingEntity target, long now) {
        if (e.cooldown() > 0 && rt.resting(e.uid(), now)) {
            return false;
        }
        if (e.after() != null && !rt.within(e.after(), e.forSeconds(), now)) {
            return false;
        }
        if (target == null && e.needsTarget()) {
            return false;
        }
        return e.conditionsHold(p, rt, target);
    }

    /** 1 + the sum, never below 5%. */
    public static double mult(Player p, Stat stat) {
        return Math.max(0.05, 1.0 + value(p, stat));
    }

    /** For a hit: the stat against this target; effects with a cooldown that made it stronger go to rest. */
    public static double multAgainst(Player p, Stat stat, @Nullable LivingEntity target) {
        List<SinEffect> used = new ArrayList<>();
        double v = value(p, stat, target, used);
        rest(p, used);
        return Math.max(0.05, 1.0 + v);
    }

    /** The effects were used: those with a cooldown rest now. */
    public static void rest(Player p, List<SinEffect> used) {
        SinRuntime rt = SinRuntime.get(p);
        long now = p.level().getGameTime();
        for (SinEffect e : used) {
            rt.rest(e.uid(), e.cooldown(), now);
        }
    }

    /**
     * A trigger fired. Server: opens the windows, applies the instant effects and tells the client; client (from that packet): opens
     * its own windows and applies what only the client can (the pulse).
     */
    public static void fire(Player p, Trigger t) {
        SinRuntime rt = SinRuntime.get(p);
        long now = p.level().getGameTime();
        rt.fire(t, now);
        for (SinEffect e : effects(p)) {
            if (e.stat().kind != Stat.Kind.INSTANT || e.after() != t) {
                continue;
            }
            if ((e.cooldown() > 0 && rt.resting(e.uid(), now)) || !e.conditionsHold(p, rt, null)) {
                continue;
            }
            rt.rest(e.uid(), e.cooldown(), now);
            applyInstant(p, e);
        }
        if (p instanceof ServerPlayer sp) {
            SinNetwork.sendToPlayer(sp, new SinNetwork.TriggerFired(t.ordinal()));
        }
    }

    private static void applyInstant(Player p, SinEffect e) {
        boolean client = p.level().isClientSide;
        switch (e.stat()) {
            case HEAL -> {
                if (!client && e.value() > 0) {
                    p.heal((float) (p.getMaxHealth() * e.value()));
                }
            }
            case STAMINA_RESTORE -> {
                PlayerData data = PlayerData.get(p);
                if (!client && data != null) {
                    double max = EfwConfig.MAX_STAMINA.get() * mult(p, Stat.STAMINA_MAX);
                    data.stamina = Math.max(0, Math.min(max, data.stamina + max * e.value()));
                }
            }
            case BLOOD_ADD -> {
                if (!client) {
                    BloodManager.gain(p, (float) e.value());
                }
            }
            case PULSE_SHIFT -> {
                if (client && pulseShift != null) {
                    pulseShift.accept((float) e.value());
                }
            }
            default -> {
            }
        }
    }

    /** Set by the client: moves the heartbeat. */
    public static volatile java.util.function.Consumer<Float> pulseShift;
}
