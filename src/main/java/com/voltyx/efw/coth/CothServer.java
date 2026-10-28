package com.voltyx.efw.coth;

import com.voltyx.efw.EFW;
import com.voltyx.efw.blood.BloodManager;
import com.voltyx.efw.init.EfwContent;
import com.voltyx.efw.network.EfwNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The fits of vomiting blood of a player sick with the Call of the Hive. Each sick player waits a random time, then feels it coming
 * (only they know: a replica, the view sways), then the fit itself, which everyone around sees: blood from the mouth, the player
 * doubled over. A fit waits while the player is in water, climbing, riding, flying or just got hurt. The antibiotic (amoxicillin)
 * cures the sickness and does not let it come back while it lasts.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class CothServer {
    public static final byte STOP = 0;
    public static final byte WARN = 1;
    public static final byte FIT = 2;

    private static final int RETRY_TICKS = 100;
    private static final Random RANDOM = new Random();
    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final class State {
        byte phase = STOP;
        int ticks;
    }

    private CothServer() {
    }

    @SubscribeEvent
    public static void onApplicable(MobEffectEvent.Applicable e) {
        MobEffect coth = Coth.effect();
        if (coth != null && e.getEffectInstance().getEffect() == coth && e.getEntity().hasEffect(EfwContent.ANTIBIOTIC.get())) {
            e.setResult(Event.Result.DENY);
            return;
        }
        // SEVEN: a chance that the sickness does not get worse (a new stage, or the first one)
        if (coth != null && e.getEffectInstance().getEffect() == coth && e.getEntity() instanceof ServerPlayer player) {
            MobEffectInstance current = player.getEffect(coth);
            boolean worse = current == null || e.getEffectInstance().getAmplifier() > current.getAmplifier();
            double resist = com.voltyx.efw.sins.stat.SinStats.value(player, com.voltyx.efw.sins.stat.Stat.COTH_RESIST);
            if (worse && resist > 0 && RANDOM.nextDouble() < resist) {
                e.setResult(Event.Result.DENY);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != LogicalSide.SERVER || !(e.player instanceof ServerPlayer player)) {
            return;
        }
        MobEffect coth = Coth.effect();
        if (coth == null) {
            return;
        }
        if (player.hasEffect(coth) && player.hasEffect(EfwContent.ANTIBIOTIC.get())) {
            player.removeEffect(coth);
        }
        int stage = Coth.stage(player);
        UUID id = player.getUUID();
        State state = STATES.get(id);
        if (!CothConfig.ENABLED.get() || !CothConfig.VOMIT.get() || stage < CothConfig.VOMIT_STAGE.get() || !player.isAlive()
                || player.isCreative() || player.isSpectator()) {
            if (state != null) {
                if (state.phase != STOP) {
                    EfwNetwork.sendToTracking(player, new EfwNetwork.CothVomit(player.getId(), STOP, 0));
                }
                STATES.remove(id);
            }
            return;
        }
        if (state == null) {
            state = new State();
            state.ticks = pause(player, stage);
            STATES.put(id, state);
        }
        if (--state.ticks > 0) {
            return;
        }
        switch (state.phase) {
            case STOP -> {
                if (!canStart(player)) {
                    state.ticks = RETRY_TICKS;
                } else if (CothConfig.WARN_TICKS.get() > 0) {
                    state.phase = WARN;
                    state.ticks = CothConfig.WARN_TICKS.get();
                    EfwNetwork.sendToPlayer(player, new EfwNetwork.CothVomit(player.getId(), WARN, state.ticks));
                } else {
                    startFit(player, state);
                }
            }
            case WARN -> {
                if (canStart(player)) {
                    startFit(player, state);
                } else {
                    // held it back: it comes again soon
                    state.phase = STOP;
                    state.ticks = RETRY_TICKS;
                    EfwNetwork.sendToPlayer(player, new EfwNetwork.CothVomit(player.getId(), STOP, 0));
                }
            }
            default -> {
                state.phase = STOP;
                state.ticks = pause(player, stage);
            }
        }
    }

    private static void startFit(ServerPlayer player, State state) {
        state.phase = FIT;
        state.ticks = CothConfig.VOMIT_TICKS.get();
        com.voltyx.efw.sins.stat.SinStats.fire(player, com.voltyx.efw.sins.stat.Trigger.VOMIT);
        player.setSprinting(false);
        EfwNetwork.sendToTracking(player, new EfwNetwork.CothVomit(player.getId(), FIT, state.ticks));
        player.causeFoodExhaustion(CothConfig.EXHAUSTION.get().floatValue());
        float damage = CothConfig.DAMAGE.get().floatValue();
        if (damage > 0 && player.getHealth() > damage + 2.0f) {
            player.hurt(player.damageSources().magic(), damage);
        }
        float blood = CothConfig.BLOOD.get().floatValue();
        if (blood > 0) {
            BloodManager.gain(player, blood);
        }
    }

    /** Not in water, on a ladder, mounted, flying, asleep or right after a hit. */
    private static boolean canStart(ServerPlayer player) {
        return !player.isInWater() && !player.onClimbable() && !player.isPassenger() && !player.getAbilities().flying
                && !player.isFallFlying() && !player.isSleeping() && player.hurtTime == 0
                && player.tickCount - player.getLastHurtByMobTimestamp() > 60;
    }

    private static int pause(ServerPlayer player, int stage) {
        int min = CothConfig.VOMIT_MIN.get();
        int max = Math.max(min, CothConfig.VOMIT_MAX.get());
        int seconds = min + RANDOM.nextInt(max - min + 1);
        if (stage >= 3) {
            seconds = seconds * 2 / 3;
        }
        // SEVEN: a strong stomach (or a weak one) moves the pause between the fits
        seconds = (int) Math.round(seconds * com.voltyx.efw.sins.stat.SinStats.mult(player, com.voltyx.efw.sins.stat.Stat.COTH_VOMIT));
        return Math.max(20, seconds * 20);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        STATES.remove(e.getEntity().getUUID());
    }
}
