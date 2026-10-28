package com.voltyx.efw.doll;

import com.voltyx.efw.EFW;
import com.voltyx.efw.blood.BloodEvents;
import com.voltyx.efw.blood.BloodManager;
import com.voltyx.efw.blood.client.BloodClient;
import com.voltyx.efw.compat.TaczCompat;
import com.voltyx.efw.sins.stat.SinStats;
import com.voltyx.efw.sins.stat.Stat;
import com.voltyx.efw.sins.stat.Trigger;
import com.voltyx.efw.network.EfwNetwork;
import com.voltyx.efw.speech.SpeechServer;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The active ability of Saya's doll (port of SayaDollManager). With the blood on the body at the maximum, taking the doll into the
 * hand starts the activation: the pulse jumps to 180, the screen goes dark, the player is slowed down and says "I'm sorry"; after
 * three seconds the doll is put away and the frenzy begins: +50% melee damage, the pulse stays high, and kills extend it.
 * The server holds the real state; the client keeps its own copy of the timers (the scalar fields below) for the visuals.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class DollAbility {
    private static final int SPEECH_TICK_DELAY = 20;

    private static final Map<UUID, Integer> LAST_NON_DOLL_SLOT = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LOCKED_SLOT = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ACTIVATION_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> BUFF_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> SPOKEN = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_ACTIVATION_MS = new ConcurrentHashMap<>();
    /** Last "carries the doll" sent to the clients around (they draw it on the belt but do not know the inventory). */
    private static final Map<UUID, Boolean> CARRY_SENT = new ConcurrentHashMap<>();

    // client copy of the timers of the local player (written by DollClient)
    public static volatile int clientActivationTicks;
    public static volatile int clientBuffTicks;
    public static volatile long clientLastActivationMs;

    private DollAbility() {
    }

    public static int activationTotalTicks() {
        return Math.round(DollConfig.ACTIVATION_SECONDS.get().floatValue() * 20.0F);
    }

    public static int buffTotalTicks() {
        return Math.round(DollConfig.BUFF_SECONDS.get().floatValue() * 20.0F);
    }

    /** The frenzy of this player: SEVEN can make it longer or shorter. */
    public static int buffTotalTicks(Player player) {
        return (int) Math.round(buffTotalTicks() * SinStats.mult(player, Stat.DOLL_BUFF_TIME));
    }

    public static int speechTick() {
        return Math.min(SPEECH_TICK_DELAY, activationTotalTicks() - 1);
    }

    private static long cooldownMs() {
        return DollConfig.COOLDOWN_SECONDS.get() * 1000L;
    }

    // ---- state -------------------------------------------------------------------------------------------------------

    /** The droplet of the blood overlay is at the maximum: more than the configured percent of the body is covered. */
    public static boolean isBloodDropletMax(float bloodLevel) {
        return Math.round(bloodLevel * 100.0F) > DollConfig.READY_BLOOD_PERCENT.get();
    }

    /** As above, with the threshold SEVEN moves for this player. */
    public static boolean isBloodDropletMax(Player player, float bloodLevel) {
        double threshold = DollConfig.READY_BLOOD_PERCENT.get() + SinStats.value(player, Stat.DOLL_READY);
        return Math.round(bloodLevel * 100.0F) > Math.max(5.0, Math.min(99.0, threshold));
    }

    public static float getBloodLevel(Player player) {
        if (player.level().isClientSide) {
            return BloodClient.getLevel(player.getUUID());
        }
        return BloodManager.getBloodLevel(player.getUUID());
    }

    public static boolean isReady(Player player) {
        return !isActivating(player) && !isBuffActive(player) && !isOnCooldown(player) && isBloodDropletMax(player, getBloodLevel(player));
    }

    public static boolean isOnCooldown(Player player) {
        long now = System.currentTimeMillis();
        if (player.level().isClientSide) {
            return clientLastActivationMs > 0L && now - clientLastActivationMs < cooldownMs();
        }
        Long last = LAST_ACTIVATION_MS.get(player.getUUID());
        return last != null && now - last < cooldownMs();
    }

    public static boolean isActivating(Player player) {
        if (player.level().isClientSide) {
            return clientActivationTicks > 0;
        }
        Integer t = ACTIVATION_TICKS.get(player.getUUID());
        return t != null && t > 0;
    }

    public static boolean isBuffActive(Player player) {
        if (player.hasEffect(DollContent.SAYA_BUFF.get())) {
            return true;
        }
        if (player.level().isClientSide) {
            return clientBuffTicks > 0;
        }
        Integer t = BUFF_TICKS.get(player.getUUID());
        return t != null && t > 0;
    }

    // ---- server tick -------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        if (player.tickCount % 10 == 0) {
            boolean carries = DollItem.hasDoll(player);
            if (CARRY_SENT.getOrDefault(id, false) != carries) {
                CARRY_SENT.put(id, carries);
                EfwNetwork.sendToTracking(player, new EfwNetwork.DollCarry(id, carries));
            }
        }
        boolean holdingDoll = DollItem.isDoll(player.getMainHandItem());
        if (!holdingDoll && !isActivating(player)) {
            LAST_NON_DOLL_SLOT.put(id, player.getInventory().selected);
        }
        if (holdingDoll && isReady(player)) {
            startActivation(player);
        }

        Integer act = ACTIVATION_TICKS.get(id);
        if (act != null && act > 0) {
            // the hotbar slot is locked, except for the last ticks (the client puts the doll away itself)
            if (act > 4) {
                int locked = LOCKED_SLOT.computeIfAbsent(id, k -> player.getInventory().selected);
                if (locked >= 0 && locked < 9 && player.getInventory().selected != locked) {
                    player.getInventory().selected = locked;
                    player.connection.send(new ClientboundSetCarriedItemPacket(locked));
                }
            }
            int next = act - 1;
            ACTIVATION_TICKS.put(id, next);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, DollConfig.ACTIVATION_SLOWNESS.get(), false, false));
            int elapsed = activationTotalTicks() - next;
            if (elapsed >= speechTick() && !SPOKEN.getOrDefault(id, false)) {
                SPOKEN.put(id, true);
                SpeechServer.sendPersonal(player, "speech.efw.doll.sorry");
            }
            if (next <= 0) {
                ACTIVATION_TICKS.remove(id);
                LOCKED_SLOT.remove(id);
                SPOKEN.remove(id);
                finishActivation(player);
            }
        }

        Integer buff = BUFF_TICKS.get(id);
        if (buff != null && buff > 0) {
            if (buff - 1 <= 0) {
                BUFF_TICKS.remove(id);
                EfwNetwork.sendToPlayer(player, new EfwNetwork.DollSync(0));
            } else {
                BUFF_TICKS.put(id, buff - 1);
            }
        }
    }

    /** Server: the client reported that the doll is in the hand with the blood at the maximum (a little tolerance for the sync). */
    public static void handleClientActivationRequest(ServerPlayer player) {
        if (isActivating(player) || isBuffActive(player) || isOnCooldown(player)) {
            return;
        }
        if (Math.round(BloodManager.getBloodLevel(player.getUUID()) * 100.0F) < DollConfig.READY_BLOOD_PERCENT.get() - 1) {
            return;
        }
        if (!DollItem.isDoll(player.getMainHandItem())) {
            return;
        }
        startActivation(player);
    }

    public static void startActivation(ServerPlayer player) {
        UUID id = player.getUUID();
        ACTIVATION_TICKS.put(id, activationTotalTicks());
        LAST_ACTIVATION_MS.put(id, System.currentTimeMillis());
        LOCKED_SLOT.put(id, player.getInventory().selected);
        SPOKEN.put(id, false);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, activationTotalTicks() + 10, DollConfig.ACTIVATION_SLOWNESS.get(), false, false));
        // the sound is in the activating player's head: they play it for themselves (DollClient), nobody else hears it
        EfwNetwork.sendToPlayer(player, new EfwNetwork.DollSync(-1));
    }

    private static void finishActivation(ServerPlayer player) {
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        putDollAway(player);
        int ticks = buffTotalTicks(player);
        SinStats.fire(player, Trigger.DOLL);
        player.addEffect(new MobEffectInstance(DollContent.SAYA_BUFF.get(), ticks, 0, false, false, true));
        BUFF_TICKS.put(player.getUUID(), ticks);
        EfwNetwork.sendToPlayer(player, new EfwNetwork.DollSync(ticks));
    }

    // ---- putting the doll away -----------------------------------------------------------------------------------------

    private static boolean isWeapon(ItemStack stack) {
        return !stack.isEmpty() && (TaczCompat.isGun(stack) || stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem);
    }

    /** The hotbar slot after the doll: the weapon held before, any weapon, an empty hand, any slot without the doll, slot 0. */
    public static int chooseSlotAfterDoll(Player player, int lastSlot) {
        if (lastSlot >= 0 && lastSlot < 9 && isWeapon(player.getInventory().getItem(lastSlot))) {
            return lastSlot;
        }
        for (int s = 0; s < 9; s++) {
            if (isWeapon(player.getInventory().getItem(s))) {
                return s;
            }
        }
        for (int s = 0; s < 9; s++) {
            if (player.getInventory().getItem(s).isEmpty()) {
                return s;
            }
        }
        if (lastSlot >= 0 && lastSlot < 9 && !DollItem.isDoll(player.getInventory().getItem(lastSlot))) {
            return lastSlot;
        }
        for (int s = 0; s < 9; s++) {
            if (!DollItem.isDoll(player.getInventory().getItem(s))) {
                return s;
            }
        }
        return 0;
    }

    public static int lastNonDollSlot(UUID id) {
        return LAST_NON_DOLL_SLOT.getOrDefault(id, -1);
    }

    public static void putDollAway(ServerPlayer player) {
        int slot = chooseSlotAfterDoll(player, lastNonDollSlot(player.getUUID()));
        player.getInventory().selected = slot;
        LOCKED_SLOT.remove(player.getUUID());
        player.connection.send(new ClientboundSetCarriedItemPacket(slot));
    }

    // ---- the frenzy ------------------------------------------------------------------------------------------------------

    /** A kill adds time to the frenzy, up to the length of the whole frenzy. */
    public static void extendBuff(ServerPlayer player, int addTicks) {
        if (addTicks <= 0) {
            return;
        }
        MobEffectInstance cur = player.getEffect(DollContent.SAYA_BUFF.get());
        int current = cur != null ? cur.getDuration() : BUFF_TICKS.getOrDefault(player.getUUID(), 0);
        int max = buffTotalTicks(player);
        if (current >= max) {
            return;
        }
        int updated = Math.min(current + addTicks, max);
        player.addEffect(new MobEffectInstance(DollContent.SAYA_BUFF.get(), updated, 0, false, false, true));
        BUFF_TICKS.put(player.getUUID(), updated);
        EfwNetwork.sendToPlayer(player, new EfwNetwork.DollSync(updated));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player attacker) || attacker.level().isClientSide || !isBuffActive(attacker)) {
            return;
        }
        // firearms are not changed, melee hits get the bonus
        if (!BloodEvents.isFirearmDamage(source, attacker)) {
            event.setAmount(event.getAmount() * (float) (DollConfig.MELEE_DAMAGE_MULTIPLIER.get() + SinStats.value(attacker, Stat.DOLL_MELEE)));
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer dead) {
            clear(dead.getUUID());
            dead.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            dead.removeEffect(DollContent.SAYA_BUFF.get());
            EfwNetwork.sendToPlayer(dead, new EfwNetwork.DollSync(0));
        }
        DamageSource source = event.getSource();
        Entity killer = source.getEntity();
        if (!(killer instanceof ServerPlayer player) || !isBuffActive(player)) {
            return;
        }
        boolean firearm = BloodEvents.isFirearmDamage(source, player);
        double seconds = firearm ? DollConfig.KILL_BONUS_FIREARM_SECONDS.get() : DollConfig.KILL_BONUS_MELEE_SECONDS.get();
        extendBuff(player, (int) Math.round(seconds * 20.0 * SinStats.mult(player, Stat.DOLL_KILL_EXTEND)));
    }

    /**
     * The frenzy keeps slowness, fatigue and weakness from being given at all (SayaBuffEffect only throws off what was there): a source
     * that gives them again and again, like LSO's penalties for a badly hurt limb, made them come and go all the time, each time with
     * the sound of the effects mods.
     */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (SayaBuffEffect.THROWN_OFF.contains(event.getEffectInstance().getEffect())
                && event.getEntity().hasEffect(DollContent.SAYA_BUFF.get())) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!event.getPlayer().level().isClientSide && isActivating(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            clear(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player.getUUID());
            if (DollItem.isDoll(player.getMainHandItem())) {
                putDollAway(player);
            }
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player target && event.getEntity() instanceof ServerPlayer viewer
                && CARRY_SENT.getOrDefault(target.getUUID(), false)) {
            EfwNetwork.sendToPlayer(viewer, new EfwNetwork.DollCarry(target.getUUID(), true));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clear(event.getEntity().getUUID());
        CARRY_SENT.remove(event.getEntity().getUUID());
    }

    public static void clear(UUID id) {
        ACTIVATION_TICKS.remove(id);
        LOCKED_SLOT.remove(id);
        BUFF_TICKS.remove(id);
        SPOKEN.remove(id);
        LAST_NON_DOLL_SLOT.remove(id);
        LAST_ACTIVATION_MS.remove(id);
    }
}
