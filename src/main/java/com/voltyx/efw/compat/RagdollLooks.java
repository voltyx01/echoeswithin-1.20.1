package com.voltyx.efw.compat;

import com.voltyx.efw.EFW;
import com.voltyx.efw.blood.BloodManager;
import com.voltyx.efw.doll.DollItem;
import com.voltyx.efw.gear.WornOlympicItem;
import com.voltyx.efw.network.EfwNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * How a dead player's body looks under Ragdollified: the blood that was on them, Saya's doll on the belt, the track jacket. The
 * blood is cleared from the player at death and the jacket and the doll go into the body's inventory, so all of it is taken at
 * the moment of death and handed to the clients with the body. The falling ragdoll gets it right away; the corpse of
 * ragdollifiedpc that the ragdoll settles into keeps the blood in its saved data, and its doll follows its inventory (taken out
 * of the corpse, the doll is gone from the belt). The jacket of a corpse comes from its Curios slots on the client.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class RagdollLooks {
    private static final String CORPSE_CLASS = "com.raiiiden.ragdollifiedpc.entity.CorpseEntity";
    private static final String TAG = "efw_look";

    private record Look(float blood, boolean doll, boolean jacket, long gameTime) {
    }

    /** The look at death, by the dead player's entity id (the id the ragdoll and its corpse are made for). */
    private static final Map<Integer, Look> DEATHS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_DEATH = new HashMap<>();
    /** Loaded corpses -> the doll last sent for them. */
    private static final Map<Entity, Boolean> CORPSES = new WeakHashMap<>();

    private static Method getOwner, getRagdollId, getInventory;
    private static boolean resolved;

    private RagdollLooks() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Look look = new Look(BloodManager.getBloodLevel(player.getUUID()), DollItem.hasDoll(player), WornOlympicItem.wears(player),
                player.level().getGameTime());
        Integer previous = LAST_DEATH.put(player.getUUID(), player.getId());
        if (previous != null) {
            DEATHS.remove(previous);
        }
        DEATHS.put(player.getId(), look);
        EfwNetwork.sendToTracking(player, new EfwNetwork.RagdollLook(player.getId(), null, look.blood, look.doll, look.jacket));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().isClientSide() || !isCorpse(entity)) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(TAG)) {
            Look look = takeDeathLook(entity);
            CompoundTag tag = new CompoundTag();
            tag.putFloat("Blood", look != null ? look.blood : 0.0F);
            data.put(TAG, tag);
        }
        CORPSES.put(entity, corpseHasDoll(entity));
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            CORPSES.remove(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && CORPSES.containsKey(event.getTarget())) {
            Entity corpse = event.getTarget();
            EfwNetwork.sendToPlayer(viewer, lookOf(corpse, CORPSES.get(corpse)));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || CORPSES.isEmpty() || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        Iterator<Map.Entry<Entity, Boolean>> it = CORPSES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, Boolean> entry = it.next();
            Entity corpse = entry.getKey();
            if (corpse.isRemoved()) {
                it.remove();
                continue;
            }
            boolean doll = corpseHasDoll(corpse);
            if (doll != entry.getValue()) {
                entry.setValue(doll);
                EfwNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> corpse), lookOf(corpse, doll));
            }
        }
    }

    private static EfwNetwork.RagdollLook lookOf(Entity corpse, boolean doll) {
        float blood = corpse.getPersistentData().getCompound(TAG).getFloat("Blood");
        return new EfwNetwork.RagdollLook(-1, corpse.getUUID(), blood, doll, false);
    }

    /**
     * The look of the death the corpse is made of: by the ragdoll it settled from, else (the ragdoll never settled) the owner's last
     * death. A corpse spawned in the very tick of a death is the previous body pushed out by the new one, not this death's.
     */
    private static Look takeDeathLook(Entity corpse) {
        Object ragdollId = call(getRagdollId(corpse), corpse);
        if (ragdollId instanceof Integer id && id >= 0) {
            Look look = DEATHS.remove(id);
            if (look != null) {
                return look;
            }
        }
        if (call(getOwner(corpse), corpse) instanceof UUID owner) {
            Integer last = LAST_DEATH.get(owner);
            Look look = last != null ? DEATHS.get(last) : null;
            if (look != null && look.gameTime != corpse.level().getGameTime()) {
                DEATHS.remove(last);
                return look;
            }
        }
        return null;
    }

    private static boolean corpseHasDoll(Entity corpse) {
        if (call(getInventory(corpse), corpse) instanceof Container inv) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (DollItem.containsDoll(inv.getItem(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isCorpse(Entity entity) {
        return entity.getClass().getName().equals(CORPSE_CLASS);
    }

    private static Method getOwner(Entity corpse) {
        resolve(corpse);
        return getOwner;
    }

    private static Method getRagdollId(Entity corpse) {
        resolve(corpse);
        return getRagdollId;
    }

    private static Method getInventory(Entity corpse) {
        resolve(corpse);
        return getInventory;
    }

    private static void resolve(Entity corpse) {
        if (resolved) {
            return;
        }
        resolved = true;
        getOwner = find(corpse, "getOwnerUUID");
        getRagdollId = find(corpse, "getRagdollEntityId");
        getInventory = find(corpse, "getInventory");
    }

    private static Method find(Entity corpse, String name) {
        try {
            return corpse.getClass().getMethod(name);
        } catch (NoSuchMethodException e) {
            EFW.LOGGER.warn("[Ragdoll] corpse has no {}(): the dead body keeps less of how it looked", name);
            return null;
        }
    }

    private static Object call(Method method, Entity corpse) {
        try {
            return method != null ? method.invoke(corpse) : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
