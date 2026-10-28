package com.voltyx.efw.blood;

import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.TaczCompat;
import com.voltyx.efw.network.EfwNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Who gets blood on them: melee attackers, anyone who takes damage, bystanders of a melee hit. Firearms never stain the shooter. */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class BloodEvents {
    /** Blocks within which a shotgun blast splashes its shooter. */
    private static final double SHOTGUN_SPLASH_RANGE = 3.0;
    private static final Map<UUID, Float> LAST_SENT = new HashMap<>();

    private BloodEvents() {
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player attacker = event.getEntity();
        if (attacker.level().isClientSide || !(event.getTarget() instanceof LivingEntity) || isHoldingFirearm(attacker)) {
            return;
        }
        BloodManager.onMeleeAttack(attacker, BloodManager.MELEE_ATTACKER_GAIN);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0f) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        DamageSource source = event.getSource();
        Entity trueSource = source.getEntity();
        Player attacker = trueSource instanceof Player p ? p : null;
        boolean ranged = isFirearmDamage(source, attacker);
        if (ranged && attacker != null && attacker != victim && TaczCompat.isShotgun(attacker.getMainHandItem())) {
            // only a shotgun at close range splashes the shooter: nothing beyond 3 blocks, twice the melee gain point blank;
            // one gain per shot (the pellets fall into the debounce)
            double dist = attacker.getEyePosition().distanceTo(victim.getBoundingBox().getCenter());
            if (dist < SHOTGUN_SPLASH_RANGE) {
                BloodManager.onMeleeAttack(attacker, (float) (BloodManager.MELEE_ATTACKER_GAIN * 2.0 * (1.0 - dist / SHOTGUN_SPLASH_RANGE)));
            }
        }
        boolean melee = !ranged && !source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.IS_EXPLOSION)
                && !source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypes.MAGIC) && !source.is(DamageTypes.INDIRECT_MAGIC)
                && trueSource instanceof LivingEntity;
        if (melee && attacker != null) {
            BloodManager.onMeleeAttack(attacker, BloodManager.MELEE_ATTACKER_GAIN);
        }
        if (victim instanceof Player p) {
            BloodManager.gain(p, BloodManager.MELEE_VICTIM_GAIN);
        }
        if (melee) {
            AABB area = victim.getBoundingBox().inflate(2.5);
            for (Player bystander : victim.level().getEntitiesOfClass(Player.class, area)) {
                if (bystander != attacker && bystander != victim) {
                    BloodManager.gain(bystander, BloodManager.BYSTANDER_GAIN);
                }
            }
        }
    }

    private static boolean isTaczItem(ItemStack stack) {
        return !stack.isEmpty() && "tacz".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }

    private static boolean isHoldingFirearm(Player player) {
        return player != null && isTaczItem(player.getMainHandItem());
    }

    public static boolean isFirearmDamage(DamageSource source, Player attacker) {
        String id = source.getMsgId();
        if (id != null && id.contains("bullet")) {
            return true;
        }
        Entity direct = source.getDirectEntity();
        if (direct != null && direct.getClass().getName().startsWith("com.tacz.")) {
            return true;
        }
        return isHoldingFirearm(attacker) && direct == attacker;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        BloodManager.tickDecay(player);
        if (player.tickCount % 10 == 0) {
            sync(player);
        }
    }

    /** Sends the level when it changed since the last packet (also the final drop to 0). */
    private static void sync(ServerPlayer player) {
        float level = BloodManager.getBloodLevel(player.getUUID());
        Float last = LAST_SENT.get(player.getUUID());
        if (last == null ? level <= 0.0f : Math.abs(last - level) < 0.001f) {
            return;
        }
        LAST_SENT.put(player.getUUID(), level);
        EfwNetwork.sendToTracking(player, new EfwNetwork.BloodSync(player.getUUID(), level));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player target && event.getEntity() instanceof ServerPlayer viewer) {
            float level = BloodManager.getBloodLevel(target.getUUID());
            if (level > 0.0f) {
                EfwNetwork.sendToPlayer(viewer, new EfwNetwork.BloodSync(target.getUUID(), level));
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BloodManager.clear(player.getUUID());
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            BloodManager.clear(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    /** For the command: push the new level to everyone who sees the player right away. */
    static void syncNow(ServerPlayer player) {
        sync(player);
    }
}
