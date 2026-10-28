package com.voltyx.efw.headshot;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * TACZ: the head is decided by the efw head boxes instead of TACZ's own head hitbox. Runs last so that gun data and
 * tacz-tweaks have already set their multiplier. Registered only when TACZ is installed.
 */
public final class TaczHeadshots {
    private TaczHeadshots() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGunHurt(EntityHurtByGunEvent.Pre e) {
        Entity bullet = e.getBullet();
        if (bullet == null || !(e.getHurtEntity() instanceof LivingEntity target) || bullet.level().isClientSide) {
            return;
        }
        boolean head = HeadshotEvents.hitsHead(bullet, HeadshotManager.getHeadBox(target, e.getAttacker()));
        e.setHeadshot(head);
        if (head && !HeadshotConfig.TACZ_USE_GUN_MULTIPLIER.get()) {
            e.setHeadshotMultiplier(HeadshotConfig.DAMAGE_MULTIPLIER.get().floatValue());
        }
        HeadshotEvents.TACZ_RESULTS.put(bullet, head);
    }
}
