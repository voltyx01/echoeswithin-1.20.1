package com.voltyx.efw.blood.client;

import com.voltyx.efw.EFW;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client side of the blood system: levels of the visible players and which player is being drawn right now. */
public final class BloodClient {
    private static final Map<UUID, Float> LEVELS = new ConcurrentHashMap<>();
    private static Player rendering;

    private BloodClient() {
    }

    public static void setLevel(UUID id, float level) {
        if (level <= 0.0f) {
            LEVELS.remove(id);
        } else {
            LEVELS.put(id, Math.min(1.0f, level));
        }
    }

    public static float getLevel(UUID id) {
        Float f = LEVELS.get(id);
        return f == null ? 0.0f : f;
    }

    public static Player getRendering() {
        return rendering;
    }

    /** The first person arm is drawn outside of the player render event. */
    public static void beginHand(Player player) {
        rendering = player;
    }

    public static void end() {
        rendering = null;
    }

    public static AbstractClientPlayer renderingClientPlayer() {
        return rendering instanceof AbstractClientPlayer p ? p : null;
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
            rendering = event.getEntity();
        }

        @SubscribeEvent
        public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
            rendering = null;
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            LEVELS.clear();
            rendering = null;
        }
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onReload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> BloodTextures.clearCache());
        }
    }
}
