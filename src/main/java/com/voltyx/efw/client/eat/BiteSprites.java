package com.voltyx.efw.client.eat;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BowlFoodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Procedural eating animation (replaces the Eating Animation mod and its hand drawn compat packs): for every food that is eaten
 * (not drunk, not a soup in a bowl) three bitten copies of its item texture are added to the block atlas, as {@code
 * efw:bitten/<namespace>/<path>_<stage>}. The bites follow what the hand drawn sets do: they start at the top right corner,
 * eat their way down and to the left, take about a fifth, two fifths and two thirds of the food, and leave the end that is held
 * as one piece (bits cut off from it fall away). Each bite is a round mouthful with a scalloped (tooth) rim; the freshly bitten
 * rim is shaded a little darker.
 */
public final class BiteSprites implements SpriteSource {
    public static final int STAGES = 3;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float[] TARGET = {0.2f, 0.42f, 0.66f};
    /**
     * Bite centres in the food's bounding box (0..1, y down), all outside it so every bite takes a mouthful off the edge and leaves
     * one solid piece: the top right corner, the right side, then the top.
     */
    private static final float[][] CENTRES = {{1.08f, -0.08f}, {1.12f, 0.55f}, {0.4f, -0.18f}};
    private static final float EDGE_SHADE = 0.86f;

    public static final BiteSprites INSTANCE = new BiteSprites();
    public static final Codec<BiteSprites> CODEC = Codec.unit(INSTANCE);
    public static SpriteSourceType TYPE;

    /** The foods that got bitten sprites in the last atlas build: item -> its texture. */
    static final Map<Item, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();

    private BiteSprites() {
    }

    public static ResourceLocation spriteId(ResourceLocation texture, int stage) {
        return new ResourceLocation("efw", "bitten/" + texture.getNamespace() + "/" + texture.getPath().replace('/', '_') + "_" + stage);
    }

    /** Eaten (EAT) foods without a bowl to leave behind. */
    public static boolean isBiteable(Item item) {
        if (!item.isEdible() || item instanceof BowlFoodItem) {
            return false;
        }
        ItemStack stack = new ItemStack(item);
        return item.getUseAnimation(stack) == UseAnim.EAT && !stack.getCraftingRemainingItem().is(Items.BOWL);
    }

    @Override
    public void run(ResourceManager resources, Output output) {
        TEXTURES.clear();
        int made = 0;
        for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, Item> e : ForgeRegistries.ITEMS.getEntries()) {
            Item item = e.getValue();
            try {
                if (!isBiteable(item)) {
                    continue;
                }
            } catch (RuntimeException ex) {
                continue;
            }
            ResourceLocation id = e.getKey().location();
            ResourceLocation texture = new ResourceLocation(id.getNamespace(), "item/" + id.getPath());
            ResourceLocation file = new ResourceLocation(id.getNamespace(), "textures/item/" + id.getPath() + ".png");
            Optional<Resource> res = resources.getResource(file);
            // animated textures (a .mcmeta next to them) keep their animation instead
            if (res.isEmpty() || resources.getResource(new ResourceLocation(file.getNamespace(), file.getPath() + ".mcmeta")).isPresent()) {
                continue;
            }
            NativeImage original;
            try (InputStream in = res.get().open()) {
                original = NativeImage.read(in);
            } catch (Exception ex) {
                continue;
            }
            if (original.getWidth() != original.getHeight() || original.getWidth() < 8) {
                original.close();
                continue;
            }
            NativeImage[] stages = bite(original, id.hashCode());
            original.close();
            if (stages == null) {
                continue;
            }
            for (int s = 0; s < STAGES; s++) {
                NativeImage img = stages[s];
                ResourceLocation sprite = spriteId(texture, s);
                output.add(sprite, () -> new SpriteContents(sprite, new FrameSize(img.getWidth(), img.getHeight()), img,
                        AnimationMetadataSection.EMPTY));
            }
            TEXTURES.put(item, texture);
            made++;
        }
        LOGGER.info("Bitten food sprites made for {} foods", made);
    }

    @Override
    public SpriteSourceType type() {
        return TYPE;
    }

    /** The three bitten copies of a food texture, or null when it has too little to bite. */
    static NativeImage[] bite(NativeImage src, long seed) {
        int w = src.getWidth();
        int h = src.getHeight();
        boolean[] solid = new boolean[w * h];
        int x0 = w, y0 = h, x1 = -1, y1 = -1, total = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((src.getPixelRGBA(x, y) >>> 24) > 16) {
                    solid[y * w + x] = true;
                    total++;
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
        }
        if (total < 12) {
            return null;
        }
        RandomSource rnd = RandomSource.create(seed);
        float bw = x1 - x0 + 1;
        float bh = y1 - y0 + 1;
        float size = Math.max(bw, bh);
        boolean[] eaten = new boolean[w * h];
        NativeImage[] out = new NativeImage[STAGES];
        for (int s = 0; s < STAGES; s++) {
            float cx = x0 + (CENTRES[s][0] + (rnd.nextFloat() - 0.5f) * 0.12f) * bw;
            float cy = y0 + (CENTRES[s][1] + (rnd.nextFloat() - 0.5f) * 0.08f) * bh;
            // the tooth marks sit on the side of the bite that faces the rest of the food
            float tilt = (float) Math.atan2(y0 + bh / 2 - cy, x0 + bw / 2 - cx) - (float) Math.PI + (rnd.nextFloat() - 0.5f) * 0.6f;
            // the mouthful grows until the food has lost its share for this stage
            float lo = 0.05f * size, hi = 1.2f * size, r = lo;
            int want = Math.round(TARGET[s] * total);
            for (int it = 0; it < 14; it++) {
                r = (lo + hi) / 2;
                if (count(solid, eaten, w, h, cx, cy, r, tilt) < want) {
                    lo = r;
                } else {
                    hi = r;
                }
            }
            r = hi;
            mark(solid, eaten, w, h, cx, cy, r, tilt);
            out[s] = paint(src, solid, eaten, w, h);
        }
        return out;
    }

    /** One mouthful: a round bite plus three smaller rounds on its rim, the tooth marks. */
    private static boolean inBite(float x, float y, float cx, float cy, float r, float tilt) {
        float dx = x - cx, dy = y - cy;
        if (dx * dx + dy * dy <= r * r) {
            return true;
        }
        float tr = r * 0.34f;
        for (int t = -1; t <= 1; t++) {
            double a = tilt + Math.PI + t * 0.7;
            float tx = cx + (float) Math.cos(a) * r * 0.92f;
            float ty = cy + (float) Math.sin(a) * r * 0.92f;
            float ex = x - tx, ey = y - ty;
            if (ex * ex + ey * ey <= tr * tr) {
                return true;
            }
        }
        return false;
    }

    private static int count(boolean[] solid, boolean[] eaten, int w, int h, float cx, float cy, float r, float tilt) {
        boolean[] trial = eaten.clone();
        mark(solid, trial, w, h, cx, cy, r, tilt);
        int n = 0;
        for (int i = 0; i < trial.length; i++) {
            if (solid[i] && trial[i]) {
                n++;
            }
        }
        return n;
    }

    /** Takes the bite, then everything no longer joined to the biggest remaining piece (8-connected) falls away too. */
    private static void mark(boolean[] solid, boolean[] eaten, int w, int h, float cx, float cy, float r, float tilt) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (solid[i] && inBite(x + 0.5f, y + 0.5f, cx, cy, r, tilt)) {
                    eaten[i] = true;
                }
            }
        }
        int[] piece = new int[w * h];
        int best = 0, bestSize = 0, id = 0;
        int[] stack = new int[w * h];
        for (int start = 0; start < piece.length; start++) {
            if (!solid[start] || eaten[start] || piece[start] != 0) {
                continue;
            }
            id++;
            int size = 0, top = 0;
            stack[top++] = start;
            piece[start] = id;
            while (top > 0) {
                int i = stack[--top];
                size++;
                int x = i % w, y = i / w;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                            continue;
                        }
                        int n = ny * w + nx;
                        if (solid[n] && !eaten[n] && piece[n] == 0) {
                            piece[n] = id;
                            stack[top++] = n;
                        }
                    }
                }
            }
            if (size > bestSize) {
                bestSize = size;
                best = id;
            }
        }
        for (int i = 0; i < piece.length; i++) {
            if (piece[i] != 0 && piece[i] != best) {
                eaten[i] = true;
            }
        }
    }

    private static NativeImage paint(NativeImage src, boolean[] solid, boolean[] eaten, int w, int h) {
        NativeImage img = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int c = src.getPixelRGBA(x, y);
                if (eaten[i]) {
                    img.setPixelRGBA(x, y, 0);
                    continue;
                }
                if (solid[i] && nextToBite(eaten, w, h, x, y)) {
                    c = shade(c);
                }
                img.setPixelRGBA(x, y, c);
            }
        }
        return img;
    }

    private static boolean nextToBite(boolean[] eaten, int w, int h, int x, int y) {
        return (x > 0 && eaten[y * w + x - 1]) || (x < w - 1 && eaten[y * w + x + 1])
                || (y > 0 && eaten[(y - 1) * w + x]) || (y < h - 1 && eaten[(y + 1) * w + x]);
    }

    /** NativeImage pixels are ABGR. */
    private static int shade(int abgr) {
        int a = abgr >>> 24;
        int b = (int) (((abgr >> 16) & 0xFF) * EDGE_SHADE);
        int g = (int) (((abgr >> 8) & 0xFF) * EDGE_SHADE);
        int r = (int) ((abgr & 0xFF) * EDGE_SHADE);
        return a << 24 | b << 16 | g << 8 | r;
    }
}
