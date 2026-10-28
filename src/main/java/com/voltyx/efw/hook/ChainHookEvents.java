package com.voltyx.efw.hook;

import com.voltyx.efw.EFW;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * While the chain drags the thrower to a creature that creature's blows miss him, and for a moment after he lands into it too; the
 * flight ends without fall damage.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class ChainHookEvents {
    private ChainHookEvents() {
    }

    /** Ticks after landing during which the creature he landed into still cannot hit him (it would strike the instant he arrives). */
    private static final int LANDING_SHIELD_TICKS = 15;
    /** Player -> {creature's entity id, game time the shield ends}. */
    private static final Map<UUID, long[]> LANDED = new HashMap<>();

    static void shieldAfterLanding(Player player, Entity target) {
        LANDED.put(player.getUUID(), new long[] {target.getId(), player.level().getGameTime() + LANDING_SHIELD_TICKS});
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        ChainHookEntity hook = ChainHookEntity.activeHook(player);
        if (hook != null && hook.shields(player, attacker)) {
            event.setCanceled(true);
            return;
        }
        long[] landed = LANDED.get(player.getUUID());
        if (landed != null) {
            if (player.level().getGameTime() > landed[1]) {
                LANDED.remove(player.getUUID());
            } else if (attacker != null && attacker.getId() == landed[0]) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            ChainHookEntity hook = ChainHookEntity.activeHook(player);
            if (hook != null && hook.state() != ChainHookEntity.FLYING) {
                event.setDamageMultiplier(0.0F);
            }
        }
    }
}
