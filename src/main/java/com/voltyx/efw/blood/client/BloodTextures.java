package com.voltyx.efw.blood.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.client.RagdollBodies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.opengl.GL11;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Procedural per-texel blood overlay (port of the 1.12 BloodTextureManager). Instead of drawing a sprite over the model, the
 * texture itself is changed: blood spots are composited into the UV space of the player skin and of every armour / clothing
 * texture drawn on the same player. The stained copies are generated on demand per (texture, blood step) and cached.
 */
public final class BloodTextures {
    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, int[]> BASE_PIXELS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, int[]> BASE_SIZE = new ConcurrentHashMap<>();
    private static boolean generating;

    private BloodTextures() {
    }

    public static void clearCache() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation loc : CACHE.values()) {
            if (loc.getNamespace().equals(EFW.MOD_ID) && loc.getPath().startsWith("blood/")) {
                mc.getTextureManager().release(loc);
            }
        }
        CACHE.clear();
        BASE_PIXELS.clear();
        BASE_SIZE.clear();
    }

    /** Called for every texture the render types of the player are built with. */
    public static ResourceLocation replace(ResourceLocation original) {
        if (original == null || generating) {
            return original;
        }
        if (RagdollBodies.isDrawing()) {
            // a dead body (Ragdollified): its own skin and the blood it died with
            ResourceLocation texture = RagdollBodies.texture(original);
            float level = RagdollBodies.blood();
            return level <= 0.001f || !shouldStain(texture, RagdollBodies.skin()) ? texture : stained(texture, level);
        }
        AbstractClientPlayer player = BloodClient.renderingClientPlayer();
        if (player == null) {
            return original;
        }
        float level = BloodClient.getLevel(player.getUUID());
        if (level <= 0.001f || !shouldStain(original, player.getSkinTextureLocation())) {
            return original;
        }
        return stained(original, level);
    }

    private static ResourceLocation stained(ResourceLocation original, float level) {
        // 20 steps of 5 percent
        int step = Math.max(1, Math.min(20, (int) Math.ceil(level * 20.0f)));
        String key = original + "#" + step;
        ResourceLocation cached = CACHE.get(key);
        return cached != null ? cached : generate(original, step / 20.0f, key);
    }

    private static boolean shouldStain(ResourceLocation res, ResourceLocation skin) {
        String path = res.getPath();
        if (res.getNamespace().equals(EFW.MOD_ID) && path.startsWith("blood/")) {
            return false;
        }
        if (path.startsWith("textures/gui/") || path.startsWith("textures/font/") || path.startsWith("textures/particle/")
                || path.startsWith("textures/environment/") || path.startsWith("textures/colormap/") || path.contains("glint")
                || path.contains("shadow") || res.equals(TextureAtlas.LOCATION_BLOCKS)) {
            return false;
        }
        if (res.equals(skin)) {
            return true;
        }
        if (path.startsWith("textures/models/armor/") || path.startsWith("textures/entity/doll")) {
            return true;
        }
        return path.contains("armor") || path.contains("vest") || path.contains("helmet") || path.contains("suit")
                || path.contains("cloth") || path.contains("sleeve") || path.contains("geo/") || path.contains("backpack");
    }

    private static ResourceLocation generate(ResourceLocation original, float level, String key) {
        generating = true;
        try {
            int[] base = loadBase(original);
            if (base == null) {
                CACHE.put(key, original); // never retried every frame
                return original;
            }
            int[] size = BASE_SIZE.get(original);
            int w = size[0];
            int h = size[1];
            int[] stained = stain(base, w, h, original, level);
            NativeImage img = new NativeImage(w, h, false);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    img.setPixelRGBA(x, y, argbToAbgr(stained[y * w + x]));
                }
            }
            ResourceLocation loc = new ResourceLocation(EFW.MOD_ID, "blood/" + Math.abs(key.hashCode()));
            Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(img));
            CACHE.put(key, loc);
            return loc;
        } catch (Throwable t) {
            EFW.LOGGER.warn("Blood overlay skipped {}: {}", original, t.toString());
            CACHE.put(key, original);
            return original;
        } finally {
            generating = false;
        }
    }

    private static int abgrToArgb(int p) {
        return (p & 0xFF00FF00) | ((p & 0xFF) << 16) | ((p >> 16) & 0xFF);
    }

    private static int argbToAbgr(int p) {
        return abgrToArgb(p); // the swap of red and blue is symmetric
    }

    private static int[] loadBase(ResourceLocation res) {
        int[] cached = BASE_PIXELS.get(res);
        if (cached != null) {
            return cached;
        }
        Minecraft mc = Minecraft.getInstance();
        NativeImage img = null;
        try {
            Optional<Resource> resource = mc.getResourceManager().getResource(res);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().open()) {
                    img = NativeImage.read(in);
                }
            }
        } catch (Exception ignored) {
            // downloaded skins are not resources: read them back from the GPU
        }
        if (img == null) {
            img = readFromGpu(res);
        }
        if (img == null) {
            return null;
        }
        try {
            int w = img.getWidth();
            int h = img.getHeight();
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    px[y * w + x] = abgrToArgb(img.getPixelRGBA(x, y));
                }
            }
            BASE_PIXELS.put(res, px);
            BASE_SIZE.put(res, new int[] {w, h});
            return px;
        } finally {
            img.close();
        }
    }

    private static NativeImage readFromGpu(ResourceLocation res) {
        try {
            AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(res, null);
            if (tex == null || tex.getId() <= 0) {
                return null;
            }
            RenderSystem.bindTexture(tex.getId());
            int w = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w <= 0 || h <= 0 || w > 2048 || h > 2048) {
                return null;
            }
            NativeImage img = new NativeImage(w, h, false);
            img.downloadTexture(0, false);
            return img;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------------ painting

    private static int[] stain(int[] base, int w, int h, ResourceLocation original, float level) {
        int[] out = base.clone();
        long baseSeed = (long) original.toString().hashCode() * 374761393L;
        float scale = Math.max(1.0f, w / 64.0f);
        float fadeRange = 0.15f;

        // face / head (18 spots), the overlay layer is stained as well
        for (int i = 0; i < 18; i++) {
            float start = ((float) i / 18) * 0.85f;
            if (level <= start) {
                continue;
            }
            float fade = Math.min(1.0f, (level - start) / fadeRange);
            Random rng = new Random(baseSeed + i * 10007L + 11L);
            boolean outer = rng.nextBoolean();
            float uMin = outer ? 40f / 64f : 8f / 64f;
            float uMax = outer ? 48f / 64f : 16f / 64f;
            int cx = (int) ((uMin + rng.nextFloat() * (uMax - uMin)) * w);
            int cy = (int) ((8f / 64f + rng.nextFloat() * (8f / 64f)) * h);
            splatter(out, base, w, h, cx, cy, (1.0f + rng.nextFloat() * 2.0f) * scale, 0.92f * fade, rng, true);
        }
        // torso (20 spots)
        for (int i = 0; i < 20; i++) {
            float start = ((float) i / 20) * 0.85f;
            if (level <= start) {
                continue;
            }
            float fade = Math.min(1.0f, (level - start) / fadeRange);
            Random rng = new Random(baseSeed + i * 10007L + 23L);
            float vMax = h <= 32 ? 1.0f : 32f / 64f;
            int cx = (int) ((18f / 64f + rng.nextFloat() * (14f / 64f)) * w);
            int cy = (int) ((20f / 64f + rng.nextFloat() * (vMax - 20f / 64f)) * h);
            splatter(out, base, w, h, cx, cy, (1.1f + rng.nextFloat() * 2.2f) * scale, 0.90f * fade, rng, true);
        }
        // arms, hands, sleeves (24 spots); the right arm is the one seen in first person
        for (int i = 0; i < 24; i++) {
            float start = ((float) i / 24) * 0.85f;
            if (level <= start) {
                continue;
            }
            float fade = Math.min(1.0f, (level - start) / fadeRange);
            Random rng = new Random(baseSeed + i * 10007L + 37L);
            boolean right = i % 2 == 0;
            float uMin, uMax, vMin, vMax;
            if (right) {
                uMin = 40f / 64f;
                uMax = 56f / 64f;
                vMin = h <= 32 ? 18f / 32f : 22f / 64f;
                vMax = h <= 32 ? 1.0f : 32f / 64f;
            } else {
                uMin = 32f / 64f;
                uMax = 48f / 64f;
                vMin = h <= 32 ? 18f / 32f : 54f / 64f;
                vMax = 1.0f;
            }
            if (h >= 64 && (i == 0 || rng.nextFloat() < 0.35f)) { // hands and palms
                if (right) {
                    uMin = 44f / 64f;
                    uMax = 52f / 64f;
                    vMin = 16f / 64f;
                    vMax = 20f / 64f;
                } else {
                    uMin = 36f / 64f;
                    uMax = 44f / 64f;
                    vMin = 48f / 64f;
                    vMax = 52f / 64f;
                }
            }
            int cx = (int) ((uMin + rng.nextFloat() * (uMax - uMin)) * w);
            int cy = (int) ((vMin + rng.nextFloat() * (vMax - vMin)) * h);
            float rad = (1.3f + rng.nextFloat() * 1.8f) * scale;
            splatter(out, base, w, h, cx, cy, rad, 0.92f * fade, rng, true);
            if (h >= 64) { // the sleeve layer above the arm
                int sx = right ? cx : cx + (int) ((16f / 64f) * w);
                int sy = right ? cy + (int) ((16f / 64f) * h) : cy;
                if (sx >= 0 && sx < w && sy >= 0 && sy < h && alpha(base[sy * w + sx]) >= 20) {
                    splatter(out, base, w, h, sx, sy, rad, 0.92f * fade, rng, true);
                }
            }
        }
        // organic scatter over any opaque texel: 3D armour, vests, helmets (36 spots)
        for (int i = 0; i < 36; i++) {
            float start = ((float) i / 36) * 0.85f;
            if (level <= start) {
                continue;
            }
            float fade = Math.min(1.0f, (level - start) / fadeRange);
            Random rng = new Random(baseSeed + i * 10007L + 53L);
            for (int attempt = 0; attempt < 25; attempt++) {
                int rx = rng.nextInt(w);
                int ry = rng.nextInt(h);
                if (alpha(base[ry * w + rx]) > 40) {
                    splatter(out, base, w, h, rx, ry, (1.0f + rng.nextFloat() * 2.0f) * scale, 0.86f * fade, rng, false);
                    break;
                }
            }
        }
        return out;
    }

    private static int alpha(int argb) {
        return (argb >> 24) & 0xFF;
    }

    private static void splatter(int[] out, int[] base, int w, int h, int cx, int cy, float radius, float intensity, Random rng, boolean canDrip) {
        int intRad = (int) Math.ceil(radius) + 1;
        int baseR, baseG, baseB;
        switch (rng.nextInt(4)) {
            case 0 -> { // coagulated dark maroon
                baseR = 56 + rng.nextInt(18);
                baseG = 3 + rng.nextInt(6);
                baseB = 5 + rng.nextInt(9);
            }
            case 1 -> { // deep burgundy
                baseR = 74 + rng.nextInt(22);
                baseG = 2 + rng.nextInt(6);
                baseB = 10 + rng.nextInt(14);
            }
            case 2 -> { // dark crimson
                baseR = 92 + rng.nextInt(26);
                baseG = 4 + rng.nextInt(7);
                baseB = 6 + rng.nextInt(12);
            }
            default -> { // dried brownish stain
                baseR = 66 + rng.nextInt(20);
                baseG = 10 + rng.nextInt(10);
                baseB = 6 + rng.nextInt(8);
            }
        }
        for (int dy = -intRad; dy <= intRad; dy++) {
            for (int dx = -intRad; dx <= intRad; dx++) {
                int px = cx + dx;
                int py = cy + dy;
                if (px < 0 || px >= w || py < 0 || py >= h || alpha(base[py * w + px]) < 20) {
                    continue;
                }
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > radius) {
                    continue;
                }
                float norm = dist / radius;
                float falloff = 1.0f - norm * norm;
                float jitter = 0.85f + rng.nextFloat() * 0.3f;
                float a = Math.min(1.0f, Math.max(0.0f, falloff * jitter * intensity));
                boolean core = norm < 0.40f; // the thick centre is darker
                blend(out, base, w, px, py, core ? (int) (baseR * 0.75f) : baseR, core ? (int) (baseG * 0.70f) : baseG,
                        core ? (int) (baseB * 0.75f) : baseB, a);
            }
        }
        int satellites = 1 + rng.nextInt(3);
        for (int s = 0; s < satellites; s++) {
            float ang = rng.nextFloat() * (float) (Math.PI * 2.0);
            float dist = radius * (1.2f + rng.nextFloat() * 1.5f);
            int sx = cx + (int) (Math.cos(ang) * dist);
            int sy = cy + (int) (Math.sin(ang) * dist);
            if (sx >= 0 && sx < w && sy >= 0 && sy < h && alpha(base[sy * w + sx]) >= 20) {
                blend(out, base, w, sx, sy, baseR, baseG, baseB, 0.75f * intensity);
            }
        }
        if (canDrip && rng.nextFloat() < 0.35f) {
            int len = 1 + rng.nextInt(3);
            for (int d = 1; d <= len; d++) {
                int py = cy + (int) radius + d;
                int px = cx + rng.nextInt(3) - 1;
                if (px >= 0 && px < w && py >= 0 && py < h && alpha(base[py * w + px]) >= 20) {
                    blend(out, base, w, px, py, baseR, baseG, baseB, (1.0f - (float) d / (len + 1)) * 0.70f * intensity);
                }
            }
        }
    }

    /** Darkens the surface and tints it crimson; the alpha of the texel is never touched (no see-through holes). */
    private static void blend(int[] out, int[] base, int w, int x, int y, int bloodR, int bloodG, int bloodB, float factor) {
        int p = base[y * w + x];
        int a = alpha(p);
        int r = (p >> 16) & 0xFF;
        int g = (p >> 8) & 0xFF;
        int b = p & 0xFF;
        float f = Math.min(1.0f, factor);
        int outR = Math.min(255, (int) (r * (1.0f - f * 0.62f) + bloodR * f * 0.85f));
        int outG = Math.max(0, (int) (g * (1.0f - f * 0.92f) + bloodG * f * 0.08f));
        int outB = Math.max(0, (int) (b * (1.0f - f * 0.90f) + bloodB * f * 0.10f));
        out[y * w + x] = (a << 24) | (outR << 16) | (outG << 8) | outB;
    }
}
