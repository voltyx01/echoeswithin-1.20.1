package com.voltyx.efw.fireweapon;

import com.voltyx.efw.network.EfwNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of the smoldering: a mob struck by an ignited weapon loses health once per second for a few seconds, as fire
 * damage that ignores armour, without being set on fire. The clients of its trackers show the rising sparks.
 */
public final class SmolderManager {
    private static final class Entry {
        int ticksRemaining;
        int untilDamage = 20;
        final float damage;

        Entry(int ticks, float damage) {
            this.ticksRemaining = ticks;
            this.damage = damage;
        }
    }

    private static final Map<Integer, List<Entry>> ACTIVE = new ConcurrentHashMap<>();

    private SmolderManager() {
    }

    public static void apply(LivingEntity target, int ticks, float damagePerSecond, boolean soaked) {
        if (ticks <= 0 || target.level().isClientSide) {
            return;
        }
        ACTIVE.computeIfAbsent(target.getId(), id -> new ArrayList<>()).add(new Entry(ticks, damagePerSecond));
        EfwNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new EfwNetwork.SmolderSync(target.getId(), ticks, soaked));
    }

    public static void update(ServerLevel level) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Integer, List<Entry>>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, List<Entry>> e = it.next();
            Entity entity = level.getEntity(e.getKey());
            if (entity == null) {
                continue; // another dimension, or not loaded right now
            }
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                it.remove();
                continue;
            }
            List<Entry> entries = e.getValue();
            for (Iterator<Entry> burn = entries.iterator(); burn.hasNext(); ) {
                Entry entry = burn.next();
                entry.ticksRemaining--;
                if (--entry.untilDamage <= 0) {
                    entry.untilDamage = 20;
                    living.invulnerableTime = 0; // the strike itself has just set the hurt cooldown
                    living.hurt(level.damageSources().onFire(), entry.damage);
                }
                if (entry.ticksRemaining <= 0) {
                    burn.remove();
                }
            }
            if (entries.isEmpty()) {
                it.remove();
            }
        }
    }
}
