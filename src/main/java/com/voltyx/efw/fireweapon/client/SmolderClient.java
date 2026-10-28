package com.voltyx.efw.fireweapon.client;

import com.voltyx.efw.EFW;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/** Sparks rising from mobs that were struck by an ignited weapon and smolder for a few seconds. */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class SmolderClient {
    private static final class State {
        int ticksRemaining;
        int marks;

        State(int ticks) {
            this.ticksRemaining = ticks;
        }
    }

    private static final Map<Integer, State> ACTIVE = new ConcurrentHashMap<>();
    private static final Random RAND = new Random();
    private static final int MAX_MARKS = 4;

    private SmolderClient() {
    }

    public static void apply(int entityId, int ticks, boolean soaked) {
        State s = ACTIVE.computeIfAbsent(entityId, id -> new State(ticks));
        s.ticksRemaining = Math.max(s.ticksRemaining, ticks);
        s.marks = Math.min(MAX_MARKS, s.marks + 1);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) {
            return;
        }
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        for (Iterator<Map.Entry<Integer, State>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, State> e = it.next();
            Entity entity = mc.level.getEntity(e.getKey());
            State s = e.getValue();
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || --s.ticksRemaining <= 0) {
                it.remove();
                continue;
            }
            for (int i = 0; i < s.marks; i++) {
                emit(living);
            }
        }
    }

    /** One spark from a random spot of the body: a little outward, a lot upward. */
    private static void emit(LivingEntity living) {
        double angle = RAND.nextDouble() * Math.PI * 2.0;
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        float heightFrac = 0.2f + RAND.nextFloat() * 0.65f;
        double r = living.getBbWidth() * 0.45;
        double x = living.getX() + dx * r;
        double y = living.getY() + living.getBbHeight() * heightFrac;
        double z = living.getZ() + dz * r;
        float vx = (float) dx * (0.05f + RAND.nextFloat() * 0.15f) + (RAND.nextFloat() - 0.5f) * 0.16f;
        float vy = 0.30f + RAND.nextFloat() * 0.50f;
        float vz = (float) dz * (0.05f + RAND.nextFloat() * 0.15f) + (RAND.nextFloat() - 0.5f) * 0.16f;
        FireSparks.spawn(x, y, z, vx, vy, vz);
    }
}
