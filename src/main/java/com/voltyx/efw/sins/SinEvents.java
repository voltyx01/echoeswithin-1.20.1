package com.voltyx.efw.sins;

import com.voltyx.efw.EFW;
import com.voltyx.efw.sins.stat.SinDefs;
import com.voltyx.efw.sins.stat.SinRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Attaches, keeps (death and dimension change) and syncs the SEVEN data; loads the content when a server starts. */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class SinEvents {
    private SinEvents() {
    }

    @SubscribeEvent
    public static void onServerStart(ServerAboutToStartEvent e) {
        String problem = SinDefs.loadFromConfig();
        if (problem != null) {
            EFW.LOGGER.error("SEVEN: content not loaded: {}", problem);
        }
    }

    @SubscribeEvent
    public static void attachCaps(AttachCapabilitiesEvent<Entity> e) {
        if (e.getObject() instanceof Player) {
            e.addCapability(SinData.KEY, new SinData.Provider());
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        e.getOriginal().reviveCaps();
        SinData from = SinData.get(e.getOriginal());
        SinData to = SinData.get(e.getEntity());
        if (from != null && to != null) {
            to.copyFrom(from); // all sin data survives death
        }
        e.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            SinNetwork.sendToPlayer(sp, new SinNetwork.SyncDefs(SinDefs.bundle()));
            sync(sp);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        SinRuntime.forget(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            sync(sp);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            sync(sp);
        }
    }

    public static void sync(ServerPlayer player) {
        SinData data = SinData.get(player);
        if (data != null) {
            SinNetwork.sendToPlayer(player, new SinNetwork.SyncSinData(data.serializeNBT()));
        }
    }
}
