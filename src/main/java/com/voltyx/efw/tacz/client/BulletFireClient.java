package com.voltyx.efw.tacz.client;

import com.voltyx.efw.EFW;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entities set on fire by an incendiary TACZ bullet (see {@code BulletFire}): no flame model on them (EntityFireHideMixin) and, for the local
 * player, no fire over the screen. Forgotten once the entity has stopped burning for a moment (the packet may come before the fire flag).
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class BulletFireClient {
    private static final int GRACE_TICKS = 10;
    /** Entity id -> ticks it has not been burning. */
    private static final Map<Integer, Integer> HIDDEN = new ConcurrentHashMap<>();
    private static Level level;

    private BulletFireClient() {
    }

    public static void hide(int entityId) {
        HIDDEN.put(entityId, 0);
    }

    public static boolean isHidden(Entity entity) {
        return !HIDDEN.isEmpty() && HIDDEN.containsKey(entity.getId());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            level = mc.level;
            HIDDEN.clear();
        }
        if (level == null || HIDDEN.isEmpty() || mc.isPaused()) {
            return;
        }
        for (Iterator<Map.Entry<Integer, Integer>> it = HIDDEN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Integer> entry = it.next();
            Entity entity = level.getEntity(entry.getKey());
            if (entity != null && entity.isOnFire()) {
                entry.setValue(0);
            } else if (entry.getValue() >= GRACE_TICKS) {
                it.remove();
            } else {
                entry.setValue(entry.getValue() + 1);
            }
        }
    }

    @SubscribeEvent
    public static void onScreenEffect(RenderBlockScreenEffectEvent e) {
        if (e.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.FIRE && isHidden(e.getPlayer())) {
            e.setCanceled(true);
        }
    }
}
