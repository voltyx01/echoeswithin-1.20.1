package com.voltyx.efw.client.dark;

import com.mojang.blaze3d.platform.NativeImage;

/** Procedural dark theme filter with sharp corners and color retention (port of the 1.12 DarkGuiFilter). */
public final class DarkGuiFilter {
    private static final int[] LUT = new int[256];

    static {
        for (int v = 0; v <= 255; v++) {
            if (v <= 20) {
                LUT[v] = Math.max(0, (int) (v * 0.5));
            } else if (v <= 70) {
                // shadow borders and slot shadows (vanilla 55 -> 13)
                LUT[v] = (int) (10 + (v - 20.0f) / 50.0f * 4);
            } else if (v <= 165) {
                // slot interiors (vanilla 139 -> 26)
                LUT[v] = (int) (15 + (v - 70.0f) / 95.0f * 14);
            } else if (v <= 225) {
                // container background (vanilla 198 -> 41)
                LUT[v] = (int) (32 + (v - 165.0f) / 60.0f * 13);
            } else {
                // light highlights (vanilla 255 -> 64)
                LUT[v] = (int) (48 + (v - 225.0f) / 30.0f * 16);
            }
        }
    }

    private DarkGuiFilter() {
    }

    /** The grey a vanilla grey of this value (0-255) turns into: for things drawn by code that should match the filtered textures. */
    public static int shade(int vanilla) {
        int v = LUT[Math.max(0, Math.min(255, vanilla))];
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }

    /** The same in the colours of the style in use, as RGB (no alpha): v2 (menu) has its warm darks. */
    public static int shadeRgb(int vanilla, boolean menu) {
        int v = Math.max(0, Math.min(255, vanilla));
        if (!menu) {
            return shade(v) & 0xFFFFFF;
        }
        int abgr = MENU_LUT[v];
        return (abgr & 0xFF) << 16 | (abgr & 0xFF00) | (abgr >> 16) & 0xFF;
    }

    private static int alpha(int abgr) {
        return abgr >>> 24;
    }

    /** Returns a new image; NativeImage pixels are ABGR, the filter is symmetric in R and B so the order does not matter. */
    public static NativeImage transform(NativeImage src) {
        return transform(src, false);
    }

    /** keepBright: white pixels stay (the frame of the selected hotbar slot). */
    public static NativeImage transform(NativeImage src, boolean keepBright) {
        return transform(src, keepBright, false);
    }

    /**
     * Dark theme v2, the palette of the main menu buttons: the vanilla greys run through warm, slightly violet darks, from the
     * near black frame of the menu buttons (vanilla shadows) over their face (container backgrounds) to their light bevel.
     */
    private static final int[][] MENU_ANCHORS = {
            {0, 0, 0, 0}, {55, 27, 19, 23}, {139, 31, 27, 30}, {198, 41, 39, 41}, {255, 83, 79, 82}};
    private static final int[] MENU_LUT = new int[256];

    static {
        for (int v = 0; v <= 255; v++) {
            int k = 1;
            while (k < MENU_ANCHORS.length - 1 && v > MENU_ANCHORS[k][0]) {
                k++;
            }
            int[] a = MENU_ANCHORS[k - 1];
            int[] b = MENU_ANCHORS[k];
            float t = Math.max(0, Math.min(1, (v - a[0]) / (float) Math.max(1, b[0] - a[0])));
            int r = Math.round(a[1] + (b[1] - a[1]) * t);
            int g = Math.round(a[2] + (b[2] - a[2]) * t);
            int bl = Math.round(a[3] + (b[3] - a[3]) * t);
            MENU_LUT[v] = (bl << 16) | (g << 8) | r; // ABGR without the alpha
        }
    }

    public static NativeImage transform(NativeImage src, boolean keepBright, boolean menu) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pixels[y * w + x] = src.getPixelRGBA(x, y);
            }
        }
        NativeImage dst = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int p = pixels[row + x];
                int a = alpha(p);
                if (a < 10) {
                    // fills the transparent 1px corner notches so the panels get sharp corners
                    boolean right = x + 1 < w && alpha(pixels[row + x + 1]) > 200;
                    boolean left = x - 1 >= 0 && alpha(pixels[row + x - 1]) > 200;
                    boolean down = y + 1 < h && alpha(pixels[row + w + x]) > 200;
                    boolean up = y - 1 >= 0 && alpha(pixels[row - w + x]) > 200;
                    boolean corner = (right && down) || (left && down) || (right && up) || (left && up);
                    dst.setPixelRGBA(x, y, corner ? 0xFF000000 : 0);
                    continue;
                }
                int r = p & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = (p >> 16) & 0xFF;
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                // coloured pixels (fire, mana, energy, fluids, coloured icons) and bright highlights of sprite sheets stay
                if (max - min > 18 || (x >= 176 && max > 240) || (keepBright && max > 240)) {
                    dst.setPixelRGBA(x, y, p);
                } else {
                    if (menu) {
                        dst.setPixelRGBA(x, y, (a << 24) | MENU_LUT[(r + g + b) / 3]);
                    } else {
                        int v = LUT[(r + g + b) / 3];
                        dst.setPixelRGBA(x, y, (a << 24) | (v << 16) | (v << 8) | v);
                    }
                }
            }
        }
        return dst;
    }

    /** The three 200x20 vanilla buttons of widgets.png (disabled, normal, hovered) drawn the way the main menu draws its buttons. */
    public static void paintMenuButtons(NativeImage img) {
        int scale = img.getWidth() / 256;
        if (scale < 1 || img.getWidth() % 256 != 0 || img.getHeight() < 106 * scale) {
            return;
        }
        // face shades, bevel light and dark, frame (RGB)
        button(img, 46, scale, new int[][]{{30, 28, 30}, {31, 29, 31}, {32, 30, 32}}, 0x3C393B, 0x302D2F, 0x1B1317);
        button(img, 66, scale, new int[][]{{41, 39, 41}, {43, 41, 43}, {44, 42, 44}}, 0x534F52, 0x454244, 0x1B1317);
        button(img, 86, scale, new int[][]{{72, 31, 33}, {75, 33, 34}, {77, 34, 35}}, 0x6B4242, 0x603637, 0x1B1317);
    }

    private static void button(NativeImage img, int top, int scale, int[][] face, int light, int dark, int frame) {
        int w = 200 * scale;
        int h = 20 * scale;
        int y0 = top * scale;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = x / scale;
                int py = y / scale;
                int rgb;
                if (px == 0 || py == 0 || px == 199 || py == 19) {
                    rgb = frame;
                } else if (px == 1 || py == 1) {
                    rgb = light;
                } else if (px == 198 || py == 18) {
                    rgb = dark;
                } else {
                    // the fine grain of the menu buttons: three close shades in a fixed pattern
                    int[] c = face[((px * 7 + py * 13) ^ (px >> 2)) % 3];
                    rgb = c[0] << 16 | c[1] << 8 | c[2];
                }
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                img.setPixelRGBA(x, y0 + y, 0xFF000000 | b << 16 | g << 8 | r);
            }
        }
    }

    /** Mid-grey button faces and panels get dark; white glyphs, black outlines and coloured icons stay as they are. */
    public static NativeImage transformButtonSheet(NativeImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        NativeImage dst = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = src.getPixelRGBA(x, y);
                int r = p & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = (p >> 16) & 0xFF;
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                int avg = (r + g + b) / 3;
                if ((p >>> 24) < 10 || max - min > 18 || avg < 60 || avg > 200) {
                    dst.setPixelRGBA(x, y, p);
                } else {
                    int v = Math.min(255, avg * 40 / 100);
                    dst.setPixelRGBA(x, y, (p & 0xFF000000) | (v << 16) | (v << 8) | v);
                }
            }
        }
        return dst;
    }
}
