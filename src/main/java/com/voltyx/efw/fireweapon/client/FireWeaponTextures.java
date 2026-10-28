package com.voltyx.efw.fireweapon.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.voltyx.efw.EFW;
import com.voltyx.efw.fireweapon.FireWeaponHelper;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-texel weapon texture compositing (port of FireWeaponTextureManager): the cloth wrap, its burning and the charred remains
 * are painted into the pixels of the weapon sprite. Two textures come out of it, the lit picture and an emissive one that holds
 * only the glowing pixels (flame front, embers) and shines in the dark, plus the list of burning pixels the sparks fly from.
 */
public final class FireWeaponTextures {
    private static final ResourceLocation CLOTH = new ResourceLocation(EFW.MOD_ID, "textures/fireweapon/cloth_weapon.png");

    public record Holder(ResourceLocation base, ResourceLocation emissive, List<FireSparks.Voxel> voxels) {
    }

    private static final Map<String, Holder> CACHE = new ConcurrentHashMap<>();
    private static int[] clothPixels;
    private static int clothW, clothH;

    private FireWeaponTextures() {
    }

    public static void clearCache() {
        Minecraft mc = Minecraft.getInstance();
        for (Holder h : CACHE.values()) {
            if (h.base != null) {
                mc.getTextureManager().release(h.base);
            }
            if (h.emissive != null) {
                mc.getTextureManager().release(h.emissive);
            }
        }
        CACHE.clear();
        clothPixels = null;
    }

    /** The textures for the state of the weapon right now, or null if the sprite cannot be read. */
    public static Holder get(ItemStack stack, TextureAtlasSprite sprite, long gameTime) {
        boolean ignited = FireWeaponHelper.isIgnited(stack);
        boolean charPhase = FireWeaponHelper.isInCharPhase(stack);
        boolean soaked = FireWeaponHelper.isSoaked(stack) || FireWeaponHelper.isSoakedReady(stack);
        int step = 0;
        int animFrame = 0;
        float charAlpha = 1.0f;
        if (ignited) {
            float prog = FireWeaponHelper.getBurnProgress(stack, gameTime);
            step = Math.max(1, Math.min(10, (int) Math.ceil(prog * 10.0f)));
            animFrame = (int) ((Util.getMillis() / 150L) % 24); // 24 frames of 150 ms: a calm 3.6 s cycle
        } else if (charPhase) {
            step = 10;
            charAlpha = Math.round(FireWeaponHelper.getCharAlpha(stack, gameTime) * 8.0f) / 8.0f; // 8 levels, not a texture per tick
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()) + "@" + sprite.contents().name();
        String key;
        if (ignited) {
            key = FireWeaponHelper.isSoaked(stack) ? id + "#soaked_f" + animFrame : id + "#step" + step + "_f" + animFrame;
        } else if (charPhase) {
            key = id + "#char" + (int) (charAlpha * 8);
        } else {
            key = id + (soaked ? "#wrapped_soaked" : "#wrapped");
        }
        Holder cached = CACHE.get(key);
        if (cached != null) {
            return cached.base == null ? null : cached;
        }
        Holder made = null;
        try {
            int[] base = readSprite(sprite);
            int[] cloth = cloth();
            if (base != null && cloth != null) {
                int w = sprite.contents().width();
                int h = sprite.contents().height();
                float prog = ignited || charPhase ? step / 10.0f : -1.0f;
                made = compose(base, w, h, cloth, prog, animFrame, charAlpha, ignited && FireWeaponHelper.isSoaked(stack), key, ignited);
            }
        } catch (Throwable t) {
            EFW.LOGGER.warn("Fire weapon texture failed for {}: {}", key, t.toString());
        }
        CACHE.put(key, made != null ? made : new Holder(null, null, List.of()));
        return made;
    }

    private static int abgrToArgb(int p) {
        return (p & 0xFF00FF00) | ((p & 0xFF) << 16) | ((p >> 16) & 0xFF);
    }

    /** The first frame of the sprite as ARGB. */
    private static int[] readSprite(TextureAtlasSprite sprite) {
        NativeImage img = sprite.contents().getOriginalImage();
        int w = sprite.contents().width();
        int h = sprite.contents().height();
        if (img == null || img.getWidth() < w || img.getHeight() < h) {
            return null;
        }
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                px[y * w + x] = abgrToArgb(img.getPixelRGBA(x, y));
            }
        }
        return px;
    }

    private static int[] cloth() {
        if (clothPixels != null) {
            return clothPixels;
        }
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(CLOTH);
            if (res.isPresent()) {
                try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                    clothW = img.getWidth();
                    clothH = img.getHeight();
                    int[] px = new int[clothW * clothH];
                    for (int y = 0; y < clothH; y++) {
                        for (int x = 0; x < clothW; x++) {
                            px[y * clothW + x] = abgrToArgb(img.getPixelRGBA(x, y));
                        }
                    }
                    clothPixels = px;
                    return px;
                }
            }
        } catch (Exception ignored) {
            // procedural olive camouflage below
        }
        clothW = 16;
        clothH = 16;
        int[] px = new int[256];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int pattern = ((x * 3 + y * 7) % 5 < 2) ? 91 : 67;
                px[y * 16 + x] = 0xFF000000 | (pattern << 16) | ((int) (pattern * 1.15f) << 8) | (int) (pattern * 0.65f);
            }
        }
        clothPixels = px;
        return px;
    }

    private static ResourceLocation register(int[] argb, int w, int h, String prefix, String key) {
        NativeImage img = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setPixelRGBA(x, y, abgrToArgb(argb[y * w + x]));
            }
        }
        ResourceLocation loc = new ResourceLocation(EFW.MOD_ID, "fireweapon/" + prefix + Math.abs(key.hashCode()));
        Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(img));
        return loc;
    }

    /**
     * Composites the cloth over the weapon, creating the lit picture, the emissive picture and the burning pixels.
     *
     * @param burnProg 0..1 how far the fire has eaten the wrap (step / 10), or -1 when it does not burn
     */
    private static Holder compose(int[] src, int w, int h, int[] cloth, float burnProg, int animFrame, float charAlpha, boolean soakedMode,
                                  String key, boolean ignited) {
        int[] pixels = src.clone();
        int[] emissive = new int[w * h];
        List<FireSparks.Voxel> voxels = new ArrayList<>();
        float phase = animFrame * (float) (Math.PI * 2.0 / 24.0);

        // bounding box of the weapon pixels
        int minX = w, maxX = 0, minY = h, maxY = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((src[y * w + x] >> 24) & 0xFF) > 25) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return new Holder(register(pixels, w, h, "b", key), null, voxels);
        }
        float spanX = Math.max(1, maxX - minX);
        float spanY = Math.max(1, maxY - minY);
        boolean burning = burnProg >= 0.0f; // the char phase paints the fully burnt state (ash with fading embers)

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                int orig = src[idx];
                int alpha = (orig >> 24) & 0xFF;
                if (alpha <= 25) {
                    pixels[idx] = 0;
                    continue;
                }
                float normX = (x - minX) / spanX;
                float normY = 1.0f - (y - minY) / spanY; // 0 at the bottom, 1 at the top

                // along the weapon: horizontal, vertical or the usual diagonal (hilt bottom-left, tip top-right)
                float axis, perp;
                if (spanX > spanY * 1.5f) {
                    axis = normX;
                    perp = normY - 0.5f;
                } else if (spanY > spanX * 1.5f) {
                    axis = normY;
                    perp = normX - 0.5f;
                } else {
                    axis = (normX + normY) * 0.5f;
                    perp = (normX - normY) * 0.5f;
                }
                // the handle stays clear, the wrap reaches almost to the tip
                if (axis < 0.34f || axis > 0.97f) {
                    continue;
                }
                float t = (axis - 0.34f) / (0.97f - 0.34f);
                float wrapPos = t * 6.0f + perp * 2.2f; // spiral coils
                float coilPhase = wrapPos - (float) Math.floor(wrapPos);

                int cr, cg, cb;
                int origR = (orig >> 16) & 0xFF;
                int origG = (orig >> 8) & 0xFF;
                int origB = orig & 0xFF;

                if (coilPhase >= 0.85f) {
                    // the seam between two coils: a dark shadow over the blade
                    int seamR = (int) (origR * 0.45f);
                    int seamG = (int) (origG * 0.45f);
                    int seamB = (int) (origB * 0.45f);
                    if (burning && (1.0f - t) < burnProg) {
                        float seamEmber = 0.5f + 0.5f * (float) Math.sin(phase + x * 0.8f);
                        seamR = (int) (18 + 40 * seamEmber);
                        seamG = (int) (12 + 14 * seamEmber);
                        seamB = 10;
                        int emAlpha = (int) (150 * seamEmber);
                        if (emAlpha > 15) {
                            emissive[idx] = (emAlpha << 24) | (seamR << 16) | (seamG << 8) | seamB;
                        }
                    }
                    if (charAlpha < 1.0f) {
                        seamR = (int) (seamR * charAlpha + origR * (1.0f - charAlpha));
                        seamG = (int) (seamG * charAlpha + origG * (1.0f - charAlpha));
                        seamB = (int) (seamB * charAlpha + origB * (1.0f - charAlpha));
                    }
                    pixels[idx] = (alpha << 24) | (seamR << 16) | (seamG << 8) | seamB;
                    continue;
                }

                int clothPixel = cloth[(Math.abs(y) % clothH) * clothW + Math.abs(x) % clothW];
                cr = (clothPixel >> 16) & 0xFF;
                cg = (clothPixel >> 8) & 0xFF;
                cb = clothPixel & 0xFF;
                // the curve of the strip and the creased edge
                float normPhase = coilPhase / 0.85f;
                float shade = 0.82f + 0.32f * (float) Math.sin(normPhase * Math.PI);
                if (normPhase < 0.15f) {
                    shade *= 0.72f + 0.28f * (normPhase / 0.15f);
                }
                cr = Math.min(255, (int) (cr * shade));
                cg = Math.min(255, (int) (cg * shade));
                cb = Math.min(255, (int) (cb * shade));

                if (burning) {
                    float x0 = (float) x / w;
                    float x1 = (float) (x + 1) / w;
                    float y0 = (float) (h - 1 - y) / h;
                    float y1 = (float) (h - y) / h;
                    float z0 = 0.46875f;
                    float z1 = 0.53125f;
                    if (soakedMode) {
                        // SOAKED: the whole wrap burns at once
                        voxels.add(new FireSparks.Voxel(x0, x1, y0, y1, z0, z1, true));
                        float flameNoise = (float) Math.sin(x * 0.75f + y * 0.85f + phase * 1.5f) * 0.5f
                                + (float) Math.cos(x * 0.65f - y * 0.75f - phase * 1.2f) * 0.5f;
                        float colorCycle = (float) Math.sin(phase + (normX * 3.5f + normY * 4.5f) + flameNoise * 1.2f);
                        cr = 255;
                        if (colorCycle > 0.15f) {
                            float k = (colorCycle - 0.15f) / 0.85f;
                            cg = (int) (190 + 55 * k);
                            cb = (int) (45 + 75 * k);
                        } else if (colorCycle > -0.35f) {
                            float k = (colorCycle + 0.35f) / 0.50f;
                            cg = (int) (125 + 65 * k);
                            cb = (int) (15 + 30 * k);
                        } else {
                            float k = (colorCycle + 1.0f) / 0.65f;
                            cg = (int) (70 + 55 * k);
                            cb = (int) (10 + 10 * k);
                        }
                        emissive[idx] = (255 << 24) | (cr << 16) | (cg << 8) | cb;
                    } else {
                        // CLOTH: the fire runs from the tip (t = 1) towards the hilt
                        float burnDist = 1.0f - t;
                        float waveHalf = 0.09f;
                        float flameNoise = (float) Math.sin(x * 0.45f + y * 0.55f + phase) * 0.45f
                                + (float) Math.cos(x * 0.55f - y * 0.45f - phase) * 0.45f;
                        float waveShift = (float) Math.sin(phase + (normX + normY) * 2.5f) * 0.02f;
                        float diff = burnDist - burnProg + flameNoise * 0.035f + waveShift;
                        if (diff < -waveHalf) {
                            // burnt ash and breathing embers
                            int ashR = Math.max(16, Math.min(36, (int) (22 + flameNoise * 5)));
                            int ashG = Math.max(14, Math.min(30, (int) (18 + flameNoise * 4)));
                            int ashB = Math.max(14, Math.min(28, (int) (16 + flameNoise * 4)));
                            float wake = -diff - waveHalf;
                            if (wake < 0.22f) {
                                voxels.add(new FireSparks.Voxel(x0, x1, y0, y1, z0, z1, false));
                                float emberDistT = 1.0f - wake / 0.22f;
                                float emberBreath = 0.50f + 0.50f * (float) Math.sin(phase + x * 0.6f + y * 0.8f);
                                float emberHeat = emberDistT * emberBreath;
                                int eR = (int) (230 * emberHeat);
                                int eG = (int) ((80 + 35 * (float) Math.sin(phase * 0.5f + x * 0.4f)) * emberHeat);
                                int eB = (int) (15 * emberHeat);
                                cr = Math.min(255, ashR + eR);
                                cg = Math.min(255, ashG + eG);
                                cb = Math.min(255, ashB + eB);
                                int emAlpha = (int) (245 * emberHeat);
                                if (emAlpha > 15) {
                                    emissive[idx] = (emAlpha << 24) | (cr << 16) | (cg << 8) | cb;
                                }
                            } else if ((float) Math.sin(x * 2.2f + y * 1.9f) > 0.72f) {
                                // an occasional ember in the cold ash
                                voxels.add(new FireSparks.Voxel(x0, x1, y0, y1, z0, z1, false));
                                float blink = 0.5f + 0.5f * (float) Math.sin(phase + x * 1.2f);
                                cr = Math.min(255, ashR + (int) (160 * blink));
                                cg = Math.min(255, ashG + (int) (55 * blink));
                                cb = ashB;
                                int emAlpha = (int) (190 * blink);
                                if (emAlpha > 15) {
                                    emissive[idx] = (emAlpha << 24) | (cr << 16) | (cg << 8) | cb;
                                }
                            } else {
                                cr = ashR;
                                cg = ashG;
                                cb = ashB;
                            }
                        } else if (Math.abs(diff) <= waveHalf) {
                            // the burning front: a slow shimmer between gold, orange and ruby
                            voxels.add(new FireSparks.Voxel(x0, x1, y0, y1, z0, z1, true));
                            float frontT = 1.0f - Math.abs(diff) / waveHalf;
                            float colorCycle = (float) Math.sin(phase + (normX - normY) * 1.8f + flameNoise * 0.6f);
                            cr = 255;
                            if (colorCycle > 0.25f) {
                                float k = (colorCycle - 0.25f) / 0.75f;
                                cg = (int) (175 + 60 * k);
                                cb = (int) (35 + 50 * k);
                            } else if (colorCycle > -0.25f) {
                                float k = (colorCycle + 0.25f) / 0.50f;
                                cg = (int) (115 + 60 * k);
                                cb = (int) (15 + 20 * k);
                            } else {
                                float k = (colorCycle + 1.0f) / 0.75f;
                                cg = (int) (65 + 50 * k);
                                cb = (int) (10 + 8 * k);
                            }
                            int emAlpha = (int) (225 + 30 * frontT);
                            emissive[idx] = (emAlpha << 24) | (cr << 16) | (cg << 8) | cb;
                        } else {
                            // not burnt yet: the heat just ahead of the front
                            float preDist = diff - waveHalf;
                            if (preDist < 0.12f) {
                                float heatT = 1.0f - preDist / 0.12f;
                                float pulse = 0.65f + 0.35f * (float) Math.sin(phase - (normX + normY) * 3.0f);
                                cr = Math.min(255, (int) (cr + 110 * heatT * pulse));
                                cg = Math.min(255, (int) (cg + 35 * heatT * pulse));
                                int emAlpha = (int) (130 * heatT * pulse);
                                if (emAlpha > 15) {
                                    emissive[idx] = (emAlpha << 24) | (cr << 16) | (cg << 8) | cb;
                                }
                            }
                        }
                    }
                }
                if (charAlpha < 1.0f) {
                    cr = (int) (cr * charAlpha + origR * (1.0f - charAlpha));
                    cg = (int) (cg * charAlpha + origG * (1.0f - charAlpha));
                    cb = (int) (cb * charAlpha + origB * (1.0f - charAlpha));
                }
                pixels[idx] = (alpha << 24) | (cr << 16) | (cg << 8) | cb;
            }
        }
        ResourceLocation emissiveLoc = ignited ? register(emissive, w, h, "e", key) : null;
        return new Holder(register(pixels, w, h, "b", key), emissiveLoc, voxels);
    }
}
