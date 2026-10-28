package com.voltyx.efw.sins.stat;

import com.voltyx.efw.stamina.EfwConfig;
import com.voltyx.efw.stamina.PlayerData;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What SEVEN has to remember about a player while the game runs (not saved): when the triggers fired, which effects rest, since when
 * the player stands still or was not hurt, the pulse (the client owns it, the server gets reports). One copy per logical side.
 */
public final class SinRuntime {
    private static final Map<UUID, SinRuntime> SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, SinRuntime> CLIENT = new ConcurrentHashMap<>();

    /** Supplies the client's own pulse; set by the client setup (the heartbeat lives on the client). */
    public static volatile java.util.function.ToDoubleFunction<Player> localPulse;

    final Map<Trigger, Long> fired = new EnumMap<>(Trigger.class);
    final Map<String, Long> restingUntil = new HashMap<>();
    private Vec3 lastPos;
    private long lastMoveTick;
    private long lastHurtTick = Long.MIN_VALUE / 2;
    private float lastHealth = -1;
    /** Pulse reported by the client (server side). */
    public volatile float reportedPulse = 65.0f;
    /** Bumped when the data or the content changes: the cached effect list is rebuilt. */
    int cachedVersion = -1;
    java.util.List<SinEffect> cached = java.util.List.of();

    public static SinRuntime get(Player p) {
        return (p.level().isClientSide ? CLIENT : SERVER).computeIfAbsent(p.getUUID(), id -> new SinRuntime());
    }

    public static void forget(UUID id) {
        SERVER.remove(id);
    }

    public static void clearClient() {
        CLIENT.clear();
    }

    /** Every tick on the side the player lives on. */
    public void tick(Player p) {
        long now = p.level().getGameTime();
        Vec3 pos = p.position();
        if (lastPos == null || pos.distanceToSqr(lastPos) > 0.0025) {
            lastMoveTick = now;
        }
        lastPos = pos;
        float hp = p.getHealth();
        if (lastHealth >= 0 && hp < lastHealth - 0.01f) {
            lastHurtTick = now;
        }
        lastHealth = hp;
    }

    public void markHurt(Player p) {
        lastHurtTick = p.level().getGameTime();
    }

    public double stillSeconds(Player p) {
        return (p.level().getGameTime() - lastMoveTick) / 20.0;
    }

    public double secondsSinceHurt(Player p) {
        return (p.level().getGameTime() - lastHurtTick) / 20.0;
    }

    public double pulse(Player p) {
        if (p.level().isClientSide && localPulse != null) {
            return localPulse.applyAsDouble(p);
        }
        return reportedPulse;
    }

    public double staminaFraction(Player p) {
        PlayerData data = PlayerData.get(p);
        if (data == null) {
            return 1.0;
        }
        double max = EfwConfig.MAX_STAMINA.get() * SinStats.mult(p, Stat.STAMINA_MAX);
        return max <= 0 ? 0 : data.stamina / max;
    }

    public boolean allyWithin(Player p, double radius) {
        double r2 = radius * radius;
        for (Player other : p.level().players()) {
            if (other != p && !other.isSpectator() && other.isAlive() && other.distanceToSqr(p) <= r2) {
                return true;
            }
        }
        return false;
    }

    public void fire(Trigger t, long now) {
        fired.put(t, now);
    }

    /** The window of a trigger is open: it fired no longer than {@code seconds} ago. */
    boolean within(Trigger t, double seconds, long now) {
        Long at = fired.get(t);
        return at != null && now - at <= Math.round(seconds * 20.0);
    }

    boolean resting(String uid, long now) {
        Long until = restingUntil.get(uid);
        return until != null && now < until;
    }

    void rest(String uid, double seconds, long now) {
        if (seconds > 0) {
            restingUntil.put(uid, now + Math.round(seconds * 20.0));
        }
    }

    public static boolean isMeleeWeapon(ItemStack stack) {
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem || stack.getItem() instanceof TridentItem) {
            return true;
        }
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE);
    }
}
