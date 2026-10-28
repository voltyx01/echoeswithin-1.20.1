package com.voltyx.efw.eyes;

import com.voltyx.efw.EFW;
import com.voltyx.efw.headshot.HeadshotEvents;
import com.voltyx.efw.network.EfwNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The eyes of a player (the client draws them, see EyesOpen): on a death the client is told how it came: a bullet of a TACZ gun in
 * the head, an explosion or a heavy blow puts the lights out at once.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class EyesServer {
    /** A blow this strong (after armour) puts the lights out at once: a long-arms flinging the player, not a sim human's punch. */
    private static final float HEAVY_HIT = 10.0f;
    /** The damage each source last dealt to a player, for the death that follows. */
    private static final Map<DamageSource, Float> DEALT = Collections.synchronizedMap(new WeakHashMap<>());

    private EyesServer() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent e) {
        if (e.getEntity() instanceof ServerPlayer) {
            DEALT.put(e.getSource(), e.getAmount());
        }
    }

    /** Players who die in the worn track jacket: looked at first, before a corpse mod takes their Curios away. */
    private static final java.util.Set<java.util.UUID> IN_JACKET = new java.util.HashSet<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeathFirst(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer player && com.voltyx.efw.gear.WornOlympicItem.wears(player)) {
            IN_JACKET.add(player.getUUID());
        }
    }

    /** Before the death screen: the packet goes out ahead of the kill packet that opens it. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean jacket = IN_JACKET.remove(player.getUUID());
        if (!e.isCanceled()) {
            EfwNetwork.sendToPlayer(player, new EfwNetwork.EyesDeath(instant(player, e.getSource()), jacket));
        }
    }

    /** A TACZ bullet in the head (judged by the headshot module), an explosion or a heavy blow; anything else closes the eyes slowly. */
    private static boolean instant(ServerPlayer player, DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct != null && direct.getClass().getName().startsWith("com.tacz.guns.entity") && HeadshotEvents.isHeadshot(source)) {
            return true;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return true;
        }
        Float dealt = DEALT.remove(source);
        return dealt != null && dealt >= HEAVY_HIT;
    }
}
