package com.voltyx.efw.doll.client;

import com.voltyx.efw.EFW;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The players around who carry the doll, as the server reports it (DollCarry): the inventory of other players never reaches this
 * client, only their hands, armour and Curios slots, so a doll in a pocket or a backpack would not hang on their belt otherwise.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class DollCarriers {
    private static final Set<UUID> CARRIERS = ConcurrentHashMap.newKeySet();

    private DollCarriers() {
    }

    public static void set(UUID player, boolean carries) {
        if (carries) {
            CARRIERS.add(player);
        } else {
            CARRIERS.remove(player);
        }
    }

    public static boolean carries(UUID player) {
        return CARRIERS.contains(player);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CARRIERS.clear();
    }
}
