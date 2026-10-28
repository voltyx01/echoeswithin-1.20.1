package com.voltyx.efw.hook;

import com.voltyx.efw.compat.TaczCompat;
import com.voltyx.efw.network.EfwNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The finish of the chain combo: rolled out behind the creature, the thrower who knows his firearms (the firearms manual read to
 * {@link #FIREARMS_RANK}) and carries a loaded shotgun on his hotbar takes it up and fires into the creature. His client switches
 * to the gun and shoots it as he would (HookFinisherClient); the pellets that hit that creature in the next moments do
 * {@link #DAMAGE_MULTIPLIER} times the damage (HookFinisherTacz).
 */
public final class HookFinisher {
    public static final int FIREARMS_RANK = 3;
    public static final float DAMAGE_MULTIPLIER = 1.8F;
    /** Ticks the client has to draw the gun, get the camera back, raise the gun and fire. */
    private static final int WINDOW_TICKS = 140;
    /** After the first pellet lands, the rest of the shot still counts this long. */
    private static final int SHOT_TICKS = 3;

    /** Thrower -> {creature's entity id, game time the boosted shot runs out}. */
    private static final Map<UUID, long[]> PENDING = new HashMap<>();

    private HookFinisher() {
    }

    /** The combo is over (the roll done): the finish, when he can make it. */
    static void offer(ServerPlayer owner, LivingEntity target) {
        if (target == null || !target.isAlive() || !TaczCompat.isLoaded()) {
            return;
        }
        if (!ChainHookItem.finisher(owner)) {
            return;
        }
        int slot = loadedShotgun(owner);
        if (slot < 0) {
            return;
        }
        PENDING.put(owner.getUUID(), new long[] {target.getId(), owner.level().getGameTime() + WINDOW_TICKS});
        EfwNetwork.sendToPlayer(owner, new EfwNetwork.HookFinisher(target.getId(), slot));
    }

    /** The hotbar slot of a shotgun with rounds in it (the one in hand first), or -1. */
    private static int loadedShotgun(ServerPlayer owner) {
        int selected = owner.getInventory().selected;
        if (isLoadedShotgun(owner, owner.getInventory().getItem(selected))) {
            return selected;
        }
        for (int i = 0; i < 9; i++) {
            if (isLoadedShotgun(owner, owner.getInventory().getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isLoadedShotgun(ServerPlayer owner, ItemStack stack) {
        return TaczCompat.isShotgun(stack) && TaczCompat.canFire(owner, stack);
    }

    /** The damage multiplier for a pellet of this shooter hitting this creature: the finish's, or 1. */
    public static float multiplier(LivingEntity shooter, LivingEntity hurt) {
        long[] pending = shooter == null ? null : PENDING.get(shooter.getUUID());
        if (pending == null) {
            return 1.0F;
        }
        long now = shooter.level().getGameTime();
        if (now > pending[1]) {
            PENDING.remove(shooter.getUUID());
            return 1.0F;
        }
        if (hurt.getId() != pending[0]) {
            return 1.0F;
        }
        pending[1] = Math.min(pending[1], now + SHOT_TICKS); // the rest of this shot's pellets, not the next shot
        return DAMAGE_MULTIPLIER;
    }
}
