package com.voltyx.efw.tacz;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.entity.EntityKineticBullet;
import com.voltyx.efw.EFW;
import com.voltyx.efw.network.EfwNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Incendiary TACZ rounds set the target on fire as before (TACZ itself does it right after this event), but its flames are not drawn:
 * the target is marked here and its trackers get {@link EfwNetwork.HiddenFire}. Something already burning from another source keeps its
 * flames. Registered only when TACZ is installed.
 */
public final class BulletFire {
    private static final Set<Entity> MARKED = Collections.newSetFromMap(new WeakHashMap<>());
    private static Field igniteEntity;
    private static boolean lookedUp;

    private BulletFire() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGunHurt(EntityHurtByGunEvent.Pre e) {
        Entity target = e.getHurtEntity();
        if (target == null || target.level().isClientSide || !(e.getBullet() instanceof EntityKineticBullet bullet) || !ignites(bullet)) {
            return;
        }
        if (target.isOnFire() && !MARKED.contains(target)) {
            return;
        }
        MARKED.add(target);
        EfwNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target), new EfwNetwork.HiddenFire(target.getId()));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (MARKED.contains(e.getTarget()) && e.getEntity() instanceof ServerPlayer player) {
            EfwNetwork.sendToPlayer(player, new EfwNetwork.HiddenFire(e.getTarget().getId()));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && !MARKED.isEmpty()) {
            MARKED.removeIf(entity -> entity.isRemoved() || !entity.isOnFire());
        }
    }

    /** The bullet's private igniteEntity flag (TACZ checks it together with its config before setting the target on fire). */
    private static boolean ignites(EntityKineticBullet bullet) {
        if (!lookedUp) {
            lookedUp = true;
            try {
                igniteEntity = EntityKineticBullet.class.getDeclaredField("igniteEntity");
                igniteEntity.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                EFW.LOGGER.warn("TACZ bullet has no igniteEntity field, incendiary flames stay visible", ex);
            }
        }
        try {
            return igniteEntity != null && igniteEntity.getBoolean(bullet) && AmmoConfig.IGNITE_ENTITY.get();
        } catch (IllegalAccessException ex) {
            return false;
        }
    }
}
