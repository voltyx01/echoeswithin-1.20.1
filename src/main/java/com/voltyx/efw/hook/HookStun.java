package com.voltyx.efw.hook;

import com.voltyx.efw.EFW;
import com.voltyx.efw.network.EfwNetwork;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The stun of a creature caught by the chain hook: while the hook is in it and {@link #AFTER_TICKS} after, it neither walks nor
 * strikes (a player is slowed to a crawl and cannot strike), and it shakes (every client draws that from the HookStun packet).
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class HookStun {
    /** Ticks the stun lasts after the hook lets go. */
    static final int AFTER_TICKS = 40;

    /** Creature -> game time the stun ends. */
    private static final Map<LivingEntity, Long> UNTIL = new WeakHashMap<>();
    /** Creature -> the last game time the hook held it (it moves with the chain then, not stopped). */
    private static final Map<LivingEntity, Long> HELD = new WeakHashMap<>();

    private HookStun() {
    }

    /** The hook holds the creature this tick: stunned for as long and {@link #AFTER_TICKS} after. */
    static void hold(LivingEntity target) {
        long now = target.level().getGameTime();
        HELD.put(target, now);
        Long until = UNTIL.get(target);
        long end = now + AFTER_TICKS;
        UNTIL.put(target, end);
        if (until == null || until < now || end - until > 10) {
            EfwNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target), new EfwNetwork.HookStun(target.getId(), AFTER_TICKS));
        }
        if (target instanceof Player player) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, AFTER_TICKS, 4, false, false));
        }
    }

    public static boolean isStunned(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        Long until = UNTIL.get(living);
        return until != null && living.level().getGameTime() <= until;
    }

    private static boolean isHeld(LivingEntity living) {
        Long held = HELD.get(living);
        return held != null && living.level().getGameTime() - held <= 1;
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        LivingEntity living = event.getEntity();
        if (living.level().isClientSide || UNTIL.isEmpty()) {
            return;
        }
        Long until = UNTIL.get(living);
        if (until == null) {
            return;
        }
        if (living.level().getGameTime() > until) {
            UNTIL.remove(living);
            HELD.remove(living);
            return;
        }
        if (living instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setJumping(false);
            mob.setSpeed(0.0F);
        }
        if (!isHeld(living)) {
            // let go of, it stands reeling where it landed
            living.setDeltaMovement(living.getDeltaMovement().multiply(0.3, 1.0, 0.3));
        }
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!event.getEntity().level().isClientSide && isStunned(event.getSource().getEntity())) {
            event.setCanceled(true);
        }
    }
}
