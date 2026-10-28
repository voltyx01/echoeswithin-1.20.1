package com.voltyx.efw.hook;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** The shotgun of the chain combo's finish hits harder (HookFinisher). Registered only when TACZ is installed. */
public final class HookFinisherTacz {
    private HookFinisherTacz() {
    }

    @SubscribeEvent
    public static void onGunHurt(EntityHurtByGunEvent.Pre e) {
        if (e.getHurtEntity() == null || e.getHurtEntity().level().isClientSide || !(e.getHurtEntity() instanceof LivingEntity hurt)) {
            return;
        }
        float mult = HookFinisher.multiplier(e.getAttacker(), hurt);
        if (mult != 1.0F) {
            e.setBaseAmount(e.getBaseAmount() * mult);
        }
    }
}
