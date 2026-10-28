package com.voltyx.efw.skin.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.voltyx.efw.EFW;
import com.voltyx.efw.network.EfwNetwork;
import com.voltyx.efw.skin.SkinSync;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Client side of SkinSync: sends the launcher's skin on joining and keeps the textures of the skins the server handed out. */
public final class ClientSkins {
    /** Written by the launcher (Account → Skin). */
    private static final Path DIR = FMLPaths.GAMEDIR.get().resolve("skin");
    /** Base layer regions (x0, y0, x1, y1) drawn opaque, as vanilla does with downloaded skins. */
    private static final int[][] OPAQUE = {{0, 0, 32, 16}, {0, 16, 64, 32}, {16, 48, 48, 64}};

    private static final Map<UUID, Entry> SKINS = new HashMap<>();
    private static int counter;

    private ClientSkins() {
    }

    private record Entry(ResourceLocation texture, String model) {
    }

    /** The skin texture handed out for this player, or null (then their usual skin is used). */
    public static ResourceLocation texture(UUID id) {
        Entry e = id == null ? null : SKINS.get(id);
        return e == null ? null : e.texture;
    }

    /** "slim" or "default", or null when the player has no skin from the launcher. */
    public static String model(UUID id) {
        Entry e = id == null ? null : SKINS.get(id);
        return e == null ? null : e.model;
    }

    /** Server -> client: a player's skin, or an empty one when they left. Runs on the render thread. */
    public static void apply(UUID id, byte[] png, boolean slim) {
        Minecraft mc = Minecraft.getInstance();
        Entry old = SKINS.remove(id);
        if (old != null) {
            mc.getTextureManager().release(old.texture);
        }
        if (png.length == 0) {
            return;
        }
        NativeImage img;
        try {
            img = NativeImage.read(new ByteArrayInputStream(png));
        } catch (Exception ex) {
            EFW.LOGGER.warn("[Skin] cannot read the skin of {}: {}", id, ex.toString());
            return;
        }
        if (img.getWidth() != 64 || img.getHeight() != 64) {
            img.close();
            return;
        }
        for (int[] r : OPAQUE) {
            for (int y = r[1]; y < r[3]; y++) {
                for (int x = r[0]; x < r[2]; x++) {
                    img.setPixelRGBA(x, y, img.getPixelRGBA(x, y) | 0xff000000);
                }
            }
        }
        // A new name for every change: mods that cache by texture name (3D skin layers) pick up the new skin
        ResourceLocation loc = new ResourceLocation(EFW.MOD_ID, "skins/" + id.toString().replace("-", "") + "_" + counter++);
        mc.getTextureManager().register(loc, new DynamicTexture(img));
        SKINS.put(id, new Entry(loc, slim ? "slim" : "default"));
    }

    private static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (Entry e : SKINS.values()) {
            mc.getTextureManager().release(e.texture);
        }
        SKINS.clear();
    }

    /** The launcher's skin, checked the way the server checks it; without one the Mojang account's skin. */
    private static void sendOwn() {
        Path file = DIR.resolve("skin.png");
        if (!Files.isRegularFile(file)) {
            sendAccountSkin();
            return;
        }
        try {
            byte[] png = Files.readAllBytes(file);
            if (!SkinSync.isSkinPng(png)) {
                EFW.LOGGER.warn("[Skin] {} is not a 64x64 PNG up to {} bytes, not sent", file, SkinSync.MAX_BYTES);
                return;
            }
            Path model = DIR.resolve("model.txt");
            boolean slim = Files.isRegularFile(model)
                    && Files.readString(model, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT).equals("slim");
            EfwNetwork.CHANNEL.sendToServer(new EfwNetwork.SkinUpload(png, slim));
        } catch (Exception ex) {
            EFW.LOGGER.warn("[Skin] cannot send {}: {}", file, ex.toString());
        }
    }

    /**
     * A world opened to LAN lets players in without asking Mojang (LanOfflineMixin), and then nobody gets the others' account skins.
     * The skin of a Microsoft account is downloaded here and handed out like a launcher skin, so it is seen on any server.
     */
    private static void sendAccountSkin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser().getType() != User.Type.MSA) {
            return;
        }
        GameProfile own = mc.getUser().getGameProfile();
        Util.backgroundExecutor().execute(() -> {
            try {
                GameProfile profile = new GameProfile(own.getId(), own.getName());
                profile.getProperties().putAll(mc.getProfileProperties());
                MinecraftProfileTexture skin = mc.getMinecraftSessionService().getTextures(profile, false).get(MinecraftProfileTexture.Type.SKIN);
                if (skin == null) {
                    return; // default Steve / Alex: everyone draws it from the UUID anyway
                }
                byte[] png = download(skin.getUrl());
                if (!SkinSync.isSkinPng(png)) {
                    EFW.LOGGER.warn("[Skin] the account skin is not a 64x64 PNG up to {} bytes, not sent", SkinSync.MAX_BYTES);
                    return;
                }
                boolean slim = "slim".equals(skin.getMetadata("model"));
                mc.execute(() -> {
                    if (mc.getConnection() != null) {
                        EfwNetwork.CHANNEL.sendToServer(new EfwNetwork.SkinUpload(png, slim));
                    }
                });
            } catch (Exception ex) {
                EFW.LOGGER.warn("[Skin] cannot get the account skin: {}", ex.toString());
            }
        });
    }

    private static byte[] download(String url) throws java.io.IOException {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        try (java.io.InputStream in = c.getInputStream()) {
            return in.readNBytes(SkinSync.MAX_BYTES + 1);
        } finally {
            c.disconnect();
        }
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
            sendOwn();
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            clear();
        }
    }
}
