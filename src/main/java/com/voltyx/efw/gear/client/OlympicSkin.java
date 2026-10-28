package com.voltyx.efw.gear.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.voltyx.efw.EFW;
import com.voltyx.efw.gear.WornOlympicItem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.opengl.GL11;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * The worn track jacket as clothing: a player wearing it is drawn with a copy of their own skin that has the jacket
 * (textures/skin/worn_olympic.png) on the body and both arms, base and outer layer. The jacket texture leaves its skin coloured
 * pixels (the hands, the neck in the collar) transparent: there the player's own skin stays, the outer layer too. For slim players the
 * jacket's 4 pixel arms are cut down to 3. Swapped in AbstractClientPlayer.getSkinTextureLocation (OlympicSkinMixin), so everything
 * that draws the player from their skin takes it: the model, the first person hands, the breasts, the blood, Ragdollified's ragdoll.
 */
public final class OlympicSkin {
    private static final ResourceLocation JACKET = new ResourceLocation(EFW.MOD_ID, "textures/skin/worn_olympic.png");
    /** A skin that cannot be read yet (still downloading) is tried again after this long. */
    private static final long RETRY_MS = 2000L;

    /** Base layer regions (x0, y0, x1, y1) and the outer layer over each: body, right arm, left arm. */
    private static final int[][] BASE = {{16, 16, 40, 32}, {40, 16, 56, 32}, {32, 48, 48, 64}};
    private static final int[][] OUTER_OFFSET = {{0, 16}, {0, 16}, {16, 0}};
    /** Arm origins: right base, right outer, left base, left outer. */
    private static final int[][] ARMS = {{40, 16}, {40, 32}, {32, 48}, {48, 48}};

    private static final Map<String, ResourceLocation> COMPOSITES = new HashMap<>();
    /** Jacket version -> the skin it was made from. */
    private static final Map<ResourceLocation, ResourceLocation> ORIGINALS = new HashMap<>();
    private static final Map<String, Long> FAILED = new HashMap<>();
    private static final Map<AbstractClientPlayer, long[]> WEARS = new WeakHashMap<>();
    private static int[] wide;
    private static int[] slim;
    private static boolean jacketMissing;
    /** A failed read of the jacket texture (a resource reload, a jar swapped under the game) is tried again after RETRY_MS. */
    private static long jacketFailedAt = -1L;

    private OlympicSkin() {
    }

    /** The skin to draw the player with: the jacket version while the jacket is worn. */
    public static ResourceLocation skin(AbstractClientPlayer player, ResourceLocation original) {
        if (original == null || !RenderSystem.isOnRenderThread() || !wears(player)) {
            return original;
        }
        return composite(original, "slim".equals(player.getModelName()));
    }

    /**
     * The skin of a dead body (Ragdollified): Ragdollified takes it from the living owner, who may have put the jacket on or off since,
     * so it is turned back into the plain skin and gets the jacket only when the body itself wears one.
     */
    public static ResourceLocation forBody(ResourceLocation skin, boolean slimModel, boolean jacket) {
        if (skin == null || !RenderSystem.isOnRenderThread()) {
            return skin;
        }
        ResourceLocation plain = plain(skin);
        return jacket ? composite(plain, slimModel) : plain;
    }

    /** The skin a jacket version was made from (any other skin as it is). */
    public static ResourceLocation plain(ResourceLocation skin) {
        return skin == null ? null : ORIGINALS.getOrDefault(skin, skin);
    }

    private static ResourceLocation composite(ResourceLocation original, boolean slimModel) {
        String key = original + (slimModel ? "#slim" : "#wide");
        ResourceLocation composite = COMPOSITES.get(key);
        if (composite != null) {
            return composite;
        }
        Long failedAt = FAILED.get(key);
        if (failedAt != null && Util.getMillis() - failedAt < RETRY_MS) {
            return original;
        }
        composite = build(original, slimModel, key);
        if (composite == null) {
            FAILED.put(key, Util.getMillis());
            return original;
        }
        FAILED.remove(key);
        COMPOSITES.put(key, composite);
        ORIGINALS.put(composite, original);
        return composite;
    }

    /** Worn or not, looked up once a tick per player (the skin is asked for many times a frame). */
    private static boolean wears(AbstractClientPlayer player) {
        long tick = player.level().getGameTime();
        long[] cached = WEARS.get(player);
        if (cached == null || cached[0] != tick) {
            cached = new long[]{tick, WornOlympicItem.shows(player) ? 1 : 0};
            WEARS.put(player, cached);
        }
        return cached[1] != 0;
    }

    private static ResourceLocation build(ResourceLocation original, boolean slimModel, String key) {
        int[] jacket = jacket(slimModel);
        if (jacket == null) {
            return null;
        }
        NativeImage img = read(original);
        if (img == null) {
            return null;
        }
        if (img.getWidth() != 64 || img.getHeight() != 64) {
            img.close();
            return null;
        }
        for (int r = 0; r < BASE.length; r++) {
            int[] b = BASE[r];
            int ox = OUTER_OFFSET[r][0], oy = OUTER_OFFSET[r][1];
            for (int y = b[1]; y < b[3]; y++) {
                for (int x = b[0]; x < b[2]; x++) {
                    int base = jacket[y * 64 + x];
                    if (alpha(base) == 0) {
                        continue; // the player's own hand or neck, both layers
                    }
                    img.setPixelRGBA(x, y, base);
                    img.setPixelRGBA(x + ox, y + oy, jacket[(y + oy) * 64 + x + ox]);
                }
            }
        }
        ResourceLocation loc = new ResourceLocation(EFW.MOD_ID, "olympic/" + Integer.toHexString(key.hashCode()));
        Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(img));
        return loc;
    }

    private static int alpha(int abgr) {
        return abgr >>> 24;
    }

    /** The jacket pixels (NativeImage order), with arms cut to 3 pixels for slim skins. */
    private static int[] jacket(boolean slimModel) {
        if (wide == null && !jacketMissing) {
            if (jacketFailedAt >= 0L && Util.getMillis() - jacketFailedAt < RETRY_MS) {
                return null;
            }
            try {
                Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(JACKET);
                if (res.isEmpty()) {
                    jacketMissing = true;
                    return null;
                }
                try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                    wide = new int[64 * 64];
                    for (int y = 0; y < 64; y++) {
                        for (int x = 0; x < 64; x++) {
                            wide[y * 64 + x] = img.getPixelRGBA(x, y);
                        }
                    }
                }
                slim = toSlim(wide);
            } catch (Exception e) {
                EFW.LOGGER.warn("[Olympic] cannot read {}: {}", JACKET, e.toString());
                wide = null;
                jacketFailedAt = Util.getMillis();
                return null;
            }
        }
        return slimModel ? slim : wide;
    }

    /**
     * Classic arms are 4 pixels across, slim ones 3: front, back, top and bottom lose their last column, the inner side moves one
     * pixel in. Every arm area is laid out again, the rest of the texture is kept.
     */
    private static int[] toSlim(int[] src) {
        int[] out = src.clone();
        for (int[] arm : ARMS) {
            int ax = arm[0], ay = arm[1];
            for (int y = ay; y < ay + 16; y++) {
                for (int x = ax; x < ax + 16; x++) {
                    out[y * 64 + x] = 0;
                }
            }
            // {source x, target x, width, y, height} relative to the arm origin
            int[][] faces = {
                    {4, 4, 3, 0, 4},   // top
                    {8, 7, 3, 0, 4},   // bottom
                    {0, 0, 4, 4, 12},  // outer side
                    {4, 4, 3, 4, 12},  // front
                    {8, 7, 4, 4, 12},  // inner side
                    {12, 11, 3, 4, 12} // back
            };
            for (int[] f : faces) {
                for (int dy = 0; dy < f[4]; dy++) {
                    for (int dx = 0; dx < f[2]; dx++) {
                        int y = ay + f[3] + dy;
                        out[y * 64 + ax + f[1] + dx] = src[y * 64 + ax + f[0] + dx];
                    }
                }
            }
        }
        return out;
    }

    /** The skin's pixels: a resource (default skins), else read back from the GPU (downloaded skins are not resources). */
    private static NativeImage read(ResourceLocation res) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Optional<Resource> resource = mc.getResourceManager().getResource(res);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().open()) {
                    return NativeImage.read(in);
                }
            }
        } catch (Exception ignored) {
        }
        try {
            AbstractTexture tex = mc.getTextureManager().getTexture(res, null);
            if (tex == null || tex.getId() <= 0) {
                return null;
            }
            RenderSystem.bindTexture(tex.getId());
            int w = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w != 64 || h != 64) {
                return null;
            }
            NativeImage img = new NativeImage(w, h, false);
            img.downloadTexture(0, false);
            return img;
        } catch (Throwable t) {
            return null;
        }
    }
}
