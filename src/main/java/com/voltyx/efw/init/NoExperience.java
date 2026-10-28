package com.voltyx.efw.init;

import com.voltyx.efw.EFW;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * No vanilla experience or levels: no orbs ever appear (mobs, ores, furnaces, breeding, fishing, bottles), points and levels never
 * change and what an old save still has is cleared on login. The bar is hidden by NoExperienceHud, and the anvil works without levels
 * (AnvilNoLevelsMixin, AnvilScreenNoCostMixin).
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class NoExperience {
    private NoExperience() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getEntity() instanceof ExperienceOrb) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onXp(PlayerXpEvent.XpChange e) {
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLevel(PlayerXpEvent.LevelChange e) {
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        Player player = e.getEntity();
        if (player.experienceLevel != 0 || player.totalExperience != 0 || player.experienceProgress != 0.0f) {
            player.experienceLevel = 0;
            player.totalExperience = 0;
            player.experienceProgress = 0.0f;
            if (player instanceof ServerPlayer server) {
                server.connection.send(new ClientboundSetExperiencePacket(0.0f, 0, 0));
            }
        }
    }
}
