package com.voltyx.efw.skin;

import com.voltyx.efw.EFW;
import com.voltyx.efw.network.EfwNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Skins without a Mojang account. The launcher puts the chosen skin into the game folder (skin/skin.png, skin/model.txt), the client
 * sends it to the server on joining (ClientSkins), and the server hands it to every player online and to everyone who joins later.
 * Clients swap it in PlayerInfo (PlayerInfoSkinMixin), where the player model, the first person hands, the tab list and other mods
 * take the skin from. Kept only while the player is online: they send it again on every join.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class SkinSync {
    /** A 64x64 PNG is at most ~17 KB even uncompressed; the whole client packet may not exceed 32767 bytes. */
    public static final int MAX_BYTES = 24 * 1024;
    /** One skin change per this long, so a client cannot flood everyone with textures. */
    private static final long MIN_INTERVAL_MS = 2000L;

    private static final Map<UUID, Skin> SKINS = new HashMap<>();
    private static final Map<UUID, Long> LAST_UPLOAD = new HashMap<>();

    private SkinSync() {
    }

    public record Skin(byte[] png, boolean slim) {
    }

    /** Client -> server: the player's skin from the launcher. */
    public static void receive(ServerPlayer player, byte[] png, boolean slim) {
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        Long last = LAST_UPLOAD.get(id);
        if (last != null && now - last < MIN_INTERVAL_MS) {
            return;
        }
        LAST_UPLOAD.put(id, now);
        if (!isSkinPng(png)) {
            EFW.LOGGER.warn("[Skin] {} sent a skin that is not a 64x64 PNG, ignored", player.getGameProfile().getName());
            return;
        }
        SKINS.put(id, new Skin(png, slim));
        EfwNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new EfwNetwork.SkinData(id, png, slim));
    }

    /** PNG signature and a 64x64 IHDR; the pixels themselves are decoded only by the clients. */
    public static boolean isSkinPng(byte[] png) {
        if (png == null || png.length < 33 || png.length > MAX_BYTES) {
            return false;
        }
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
        for (int i = 0; i < sig.length; i++) {
            if (png[i] != sig[i]) {
                return false;
            }
        }
        return readInt(png, 16) == 64 && readInt(png, 20) == 64;
    }

    private static int readInt(byte[] b, int at) {
        return (b[at] & 0xff) << 24 | (b[at + 1] & 0xff) << 16 | (b[at + 2] & 0xff) << 8 | (b[at + 3] & 0xff);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (Map.Entry<UUID, Skin> s : SKINS.entrySet()) {
            EfwNetwork.sendToPlayer(player, new EfwNetwork.SkinData(s.getKey(), s.getValue().png(), s.getValue().slim()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        LAST_UPLOAD.remove(id);
        if (SKINS.remove(id) != null) {
            EfwNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new EfwNetwork.SkinData(id, new byte[0], false));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        SKINS.clear();
        LAST_UPLOAD.clear();
    }
}
