package com.voltyx.efw.stamina;

import com.voltyx.efw.sins.stat.SinStats;
import com.voltyx.efw.sins.stat.Stat;
import com.voltyx.efw.sins.stat.Trigger;
import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.IvyTweaks;
import com.voltyx.efw.compat.TaczCompat;
import com.voltyx.efw.init.EfwContent;
import com.voltyx.efw.network.EfwNetwork;
import com.voltyx.efw.roll.RollState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server side of the 1.12 DashEvents: stamina (sprint drain, regeneration, food limit, low stamina effects) and the roll
 * (cost, invulnerability, particles, sound, cooldown). The client only asks for a roll ({@link #requestDash}) and gets values back.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class StaminaEvents {
    private StaminaEvents() {
    }

    @SubscribeEvent
    public static void attachCaps(AttachCapabilitiesEvent<Entity> e) {
        if (e.getObject() instanceof Player) {
            e.addCapability(PlayerData.KEY, new PlayerData.Provider());
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        e.getOriginal().reviveCaps();
        PlayerData from = PlayerData.get(e.getOriginal());
        PlayerData to = PlayerData.get(e.getEntity());
        if (from != null && to != null) {
            to.copyFrom(from);
            if (e.isWasDeath()) {
                to.initialized = false; // full stamina after respawn
                to.cooldown = 0;
            }
        }
        e.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            syncAll(sp);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            syncAll(sp);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            syncAll(sp);
        }
    }

    private static void syncAll(ServerPlayer sp) {
        PlayerData data = PlayerData.get(sp);
        if (data != null) {
            EfwNetwork.sendToPlayer(sp, new EfwNetwork.SyncStamina(data.stamina, data.regenDelay));
            EfwNetwork.sendToPlayer(sp, new EfwNetwork.SyncCooldown(data.cooldown));
            data.lastSentStamina = data.stamina;
            data.lastSentRegenDelay = data.regenDelay;
        }
    }

    /** Ticks of cooldown the server still lets a roll through with (the client's cooldown ends a little earlier with the ping). */
    private static final int COOLDOWN_SLACK_TICKS = 2;

    /** A DashRequest arrived: validate and arm the roll, the next player tick executes it. */
    public static void requestDash(ServerPlayer player, Vec3 dir) {
        PlayerData data = PlayerData.get(player);
        // the client already checked the ground and its own cooldown: the server's view of both lags a little behind (a bump, a block
        // edge, the ping), and turning the roll down here only took its sound and cost while the player rolled anyway
        if (data == null || data.cooldown > COOLDOWN_SLACK_TICKS || TaczCompat.isReloading(player) || IvyTweaks.isInIvy(player)) {
            return;
        }
        Vec3 d = new Vec3(dir.x, 0, dir.z);
        if (d.lengthSqr() < 1.0E-6) {
            return;
        }
        data.dashing = true;
        data.dashDir = d.normalize();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !(e.player instanceof ServerPlayer player)) {
            return;
        }
        PlayerData data = PlayerData.get(player);
        if (data == null) {
            return;
        }
        handleStamina(player, data);

        boolean inLiquid = isInLiquid(player);
        boolean hasStamina = data.stamina >= rollCost(player);
        if (!hasStamina || (inLiquid && !EfwConfig.ALLOW_DASH_IN_LIQUIDS.get())) {
            data.dashing = false;
        }

        if (data.dashing) {
            Vec3 dir = data.dashDir;
            if (dir == null || dir.lengthSqr() == 0) {
                data.dashing = false;
            } else {
                performDash(player, data, dir);
            }
        }

        if (data.postDashTimer > 0) {
            data.postDashTimer--;
        }
        if (data.cooldown > 0) {
            data.cooldown--;
        }
    }

    /** Ticks between two rolls of this player (SEVEN moves it); the client starts the same cooldown when it rolls. */
    public static int cooldownTicks(Player player) {
        return (int) Math.round(EfwConfig.DASH_COOLDOWN_TICKS.get() * SinStats.mult(player, Stat.ROLL_COOLDOWN));
    }

    /** Stamina a roll takes from this player (SEVEN moves it). */
    public static double rollCost(Player player) {
        return EfwConfig.DASH_STAMINA_COST.get() * SinStats.mult(player, Stat.ROLL_COST);
    }

    /** The stamina pool of this player (SEVEN moves it); the HUD asks the same. */
    public static double maxStamina(Player player) {
        return EfwConfig.MAX_STAMINA.get() * SinStats.mult(player, Stat.STAMINA_MAX);
    }

    private static void performDash(ServerPlayer player, PlayerData data, Vec3 dir) {
        data.stamina -= rollCost(player);
        data.regenDelay = EfwConfig.DASH_REGEN_DELAY.get();
        player.fallDistance = 0;

        int guard = EfwConfig.RESISTANCE_DURATION.get() + (int) Math.round(SinStats.value(player, Stat.ROLL_GUARD));
        if (guard > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, guard, EfwConfig.RESISTANCE_LEVEL.get(), false, false));
        }
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EfwConfig.WEAKNESS_DURATION.get(),
                EfwConfig.WEAKNESS_LEVEL.get(), false, false));

        ServerLevel level = player.serverLevel();
        // the rolling player played it already the moment they rolled (RollClient); everybody else hears it from here
        level.playSound(player, player.getX(), player.getY(), player.getZ(), EfwContent.DASH_SOUND.get(), SoundSource.PLAYERS,
                EfwConfig.SOUND_VOLUME.get().floatValue(), EfwConfig.SOUND_PITCH.get().floatValue());

        BlockPos foot = BlockPos.containing(player.getX(), player.getBoundingBox().minY - 0.2, player.getZ());
        BlockState footState = level.getBlockState(foot);
        if (footState.isAir()) {
            foot = foot.below();
            footState = level.getBlockState(foot);
        }
        double spread = EfwConfig.PARTICLE_SPREAD.get();
        if (!footState.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, footState), player.getX(), player.getY() + 0.1, player.getZ(),
                    EfwConfig.PARTICLE_COUNT.get(), dir.x * spread, 0.15, dir.z * spread, 0.15);
        } else {
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + EfwConfig.PARTICLE_Y_OFFSET.get(), player.getZ(),
                    EfwConfig.PARTICLE_COUNT.get(), dir.x * spread, EfwConfig.PARTICLE_HEIGHT.get(), dir.z * spread,
                    EfwConfig.PARTICLE_SPEED.get());
        }

        data.dashing = false;
        data.postDashTimer = EfwConfig.DASH_POST_TIMER_TICKS.get();
        data.cooldown = cooldownTicks(player);
        SinStats.fire(player, Trigger.ROLL);

        EfwNetwork.sendToTracking(player, new EfwNetwork.RollStart(player.getId(), (float) dir.x, (float) dir.z));
        EfwNetwork.sendToPlayer(player, new EfwNetwork.SyncStamina(data.stamina, data.regenDelay));
        EfwNetwork.sendToPlayer(player, new EfwNetwork.SyncCooldown(data.cooldown));
        data.lastSentStamina = data.stamina;
        data.lastSentRegenDelay = data.regenDelay;
    }

    private static void handleStamina(ServerPlayer player, PlayerData data) {
        final double max = maxStamina(player);
        if (!data.initialized) {
            data.initialized = true;
            data.stamina = max;
            data.tickCounter = 0;
            data.regenDelay = 0;
            data.foodLimit = foodLimit(player, max, false);
        }
        if (player.isCreative() || player.isSpectator()) {
            data.stamina = max;
            data.tickCounter = 0;
            data.regenDelay = 0;
            data.foodLimit = max;
            sendIfChanged(player, data);
            return;
        }

        double stamina = data.stamina;
        int tick = data.tickCounter;
        int delay = data.regenDelay;
        boolean boost = player.hasEffect(EfwContent.ENERGY_BOOST.get());

        if (player.isSprinting()) {
            tick++;
            if (tick >= EfwConfig.SPRINT_DRAIN_INTERVAL.get()) {
                tick = 0;
                stamina = Math.max(stamina - EfwConfig.STAMINA_DRAIN_AMOUNT.get() * SinStats.mult(player, Stat.STAMINA_SPRINT_COST), 0);
                delay = EfwConfig.SPRINT_REGEN_DELAY.get();
            }
        } else if (delay > 0) {
            delay--;
            tick = 0;
        } else {
            if (!player.onGround() && !data.jumpedLastTick) {
                stamina = Math.max(stamina - EfwConfig.JUMP_COST.get() * SinStats.mult(player, Stat.STAMINA_JUMP_COST), 0);
                delay = EfwConfig.JUMP_REGEN_DELAY.get();
                data.jumpedLastTick = true;
            }
            if (player.onGround()) {
                data.jumpedLastTick = false;
            }
            int regenTicks = boost ? EfwConfig.REGEN_INTERVAL_BOOST.get() : EfwConfig.REGEN_INTERVAL_NORMAL.get();
            tick++;
            if (tick >= regenTicks) {
                tick = 0;
                stamina = Math.min(stamina + EfwConfig.STAMINA_REGEN_AMOUNT.get() * SinStats.mult(player, Stat.STAMINA_REGEN), max);
            }
        }

        if (EfwConfig.LIMIT_BY_FOOD.get()) {
            double allowed = foodLimit(player, max, boost);
            data.foodLimit = allowed;
            stamina = Math.min(stamina, allowed);
        } else {
            data.foodLimit = max;
        }

        if (stamina <= max * 0.05) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EfwConfig.SLOWNESS_DURATION.get(),
                    EfwConfig.SLOWNESS_AMPLIFIER.get(), false, false));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, EfwConfig.FATIGUE_DURATION.get(),
                    EfwConfig.FATIGUE_AMPLIFIER.get(), false, false));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EfwConfig.LOW_WEAKNESS_DURATION.get(),
                    EfwConfig.LOW_WEAKNESS_AMPLIFIER.get(), false, false));
        }

        data.stamina = stamina;
        data.tickCounter = tick;
        data.regenDelay = delay;
        sendIfChanged(player, data);
    }

    private static double foodLimit(Player player, double max, boolean boost) {
        if (!EfwConfig.LIMIT_BY_FOOD.get() || boost) {
            return max;
        }
        int food = player.getFoodData().getFoodLevel();
        return food <= 6 ? 1 : Math.max(((food - 6) / 14.0) * max, 1);
    }

    private static void sendIfChanged(ServerPlayer player, PlayerData data) {
        if (Math.abs(data.stamina - data.lastSentStamina) > 0.01 || data.regenDelay != data.lastSentRegenDelay) {
            data.lastSentStamina = data.stamina;
            data.lastSentRegenDelay = data.regenDelay;
            EfwNetwork.sendToPlayer(player, new EfwNetwork.SyncStamina(data.stamina, data.regenDelay));
        }
    }

    private static boolean isInLiquid(Player player) {
        BlockPos feet = BlockPos.containing(player.getX(), player.getY() + 0.1, player.getZ());
        BlockPos head = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        return !player.level().getFluidState(feet).isEmpty() || !player.level().getFluidState(head).isEmpty();
    }

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent e) {
        Player player = e.getEntity();
        if (player.level().isClientSide || !(player instanceof ServerPlayer sp)) {
            return;
        }
        PlayerData data = PlayerData.get(player);
        if (data == null || data.postDashTimer <= 0) {
            return;
        }
        data.stamina = Math.max(0, data.stamina - EfwConfig.ATTACK_EXTRA_STAMINA.get() * SinStats.mult(player, Stat.STAMINA_ATTACK_COST));
        if (EfwConfig.ATTACK_EXTRA_COOLDOWN.get()) {
            data.cooldown = EfwConfig.DASH_COOLDOWN_TICKS.get();
            EfwNetwork.sendToPlayer(sp, new EfwNetwork.SyncCooldown(data.cooldown));
        }
        data.postDashTimer = 0;
        data.regenDelay = EfwConfig.DASH_REGEN_DELAY.get();
        data.lastSentStamina = data.stamina;
        data.lastSentRegenDelay = data.regenDelay;
        EfwNetwork.sendToPlayer(sp, new EfwNetwork.SyncStamina(data.stamina, data.regenDelay));
    }

    // ---- invulnerability while rolling ----

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent e) {
        if (e.getEntity() instanceof Player p && RollState.isRolling(p)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof Player p && RollState.isRolling(p)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamage(LivingDamageEvent e) {
        if (e.getEntity() instanceof Player p && RollState.isRolling(p)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKnockBack(LivingKnockBackEvent e) {
        if (e.getEntity() instanceof Player p && RollState.isRolling(p)) {
            e.setCanceled(true);
        }
    }
}
