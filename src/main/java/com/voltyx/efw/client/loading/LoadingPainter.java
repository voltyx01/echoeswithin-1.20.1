package com.voltyx.efw.client.loading;

import com.voltyx.efw.EFW;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Whole loading screen picture: background, item card, status text and the square "snake" spinner (port of the 1.12 layout).
 * The background is the modpack's picture, config/fancymenu/assets/rb/loading_screen.png, read from disk once, so it can be
 * swapped without rebuilding the mod; without that file the efw texture is used.
 */
public final class LoadingPainter {
    private static final ResourceLocation BUILT_IN = new ResourceLocation(EFW.MOD_ID, "textures/gui/loading_screen.png");
    private static final ResourceLocation EXTERNAL = new ResourceLocation(EFW.MOD_ID, "loading_screen_external");
    private static final String EXTERNAL_PATH = "config/fancymenu/assets/rb/loading_screen.png";

    private static ResourceLocation background;
    private static int imageW = 256;
    private static int imageH = 144;

    private LoadingPainter() {
    }

    public static void render(Screen screen, GuiGraphics g, Component title, Component subtitle) {
        int w = screen.width;
        int h = screen.height;
        drawCover(g, w, h);
        LoadingCard.draw(g, w, h);
        drawStatus(g, w, h, title, subtitle);
        drawSnake(g, w, h);
    }

    private static ResourceLocation background() {
        if (background == null) {
            background = BUILT_IN;
            Path file = FMLPaths.GAMEDIR.get().resolve(EXTERNAL_PATH);
            if (Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    NativeImage image = NativeImage.read(in);
                    imageW = image.getWidth();
                    imageH = image.getHeight();
                    Minecraft.getInstance().getTextureManager().register(EXTERNAL, new DynamicTexture(image));
                    background = EXTERNAL;
                } catch (Exception e) {
                    imageW = 256;
                    imageH = 144;
                }
            }
        }
        return background;
    }

    /** object-fit: cover, like the 1.12 screen. */
    private static void drawCover(GuiGraphics g, int w, int h) {
        ResourceLocation texture = background();
        double imgAspect = (double) imageW / imageH;
        double screenAspect = (double) w / h;
        int u = 0;
        int v = 0;
        int uw = imageW;
        int vh = imageH;
        if (screenAspect > imgAspect) {
            vh = (int) Math.round(imageW / screenAspect);
            v = (imageH - vh) / 2;
        } else if (screenAspect < imgAspect) {
            uw = (int) Math.round(imageH * screenAspect);
            u = (imageW - uw) / 2;
        }
        g.blit(texture, 0, 0, w, h, u, v, uw, vh, imageW, imageH);
    }

    /** Some mods push a status whose lang key nobody defined (e.g. "progress.working"); showing the raw key looks broken. */
    private static boolean isUntranslatedKey(Component c) {
        return c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                && tc.getFallback() == null
                && !net.minecraft.client.resources.language.I18n.exists(tc.getKey());
    }

    private static void drawStatus(GuiGraphics g, int w, int h, Component title, Component subtitle) {
        Font font = Minecraft.getInstance().font;
        List<String> lines = new ArrayList<>();
        for (Component c : new Component[] {title, subtitle}) {
            if (c == null || isUntranslatedKey(c)) {
                continue;
            }
            for (String part : c.getString().split("\n")) {
                String t = part.trim();
                if (!t.isEmpty() && !lines.contains(t)) {
                    lines.add(t);
                }
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, font.width(line));
        }
        int cx = w - 30;
        int cy = h - 30;
        int textX = Math.max(130, cx - 10 - 12 - maxWidth);
        int lineHeight = font.lineHeight + 2;
        int startY = cy - (lines.size() * lineHeight - 2) / 2;
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), textX, startY + i * lineHeight, 0xFFFFFF, true);
        }
    }

    private static void drawSnake(GuiGraphics g, int w, int h) {
        int cx = w - 30;
        int cy = h - 30;
        int size = 10;
        int bg = 0x22FFFFFF;
        g.fill(cx - size, cy - size, cx + size, cy - size + 1, bg);
        g.fill(cx + size - 1, cy - size, cx + size, cy + size, bg);
        g.fill(cx - size, cy + size - 1, cx + size, cy + size, bg);
        g.fill(cx - size, cy - size, cx - size + 1, cy + size, bg);

        int perimeter = size * 8;
        int pos = (int) ((Util.getMillis() / 15) % perimeter);
        for (int i = 0; i < 16; i++) {
            int dotPos = (pos - i * 2 + perimeter) % perimeter;
            int dx;
            int dy;
            if (dotPos < size * 2) {
                dx = -size + dotPos;
                dy = -size;
            } else if (dotPos < size * 4) {
                dx = size;
                dy = -size + (dotPos - size * 2);
            } else if (dotPos < size * 6) {
                dx = size - (dotPos - size * 4);
                dy = size;
            } else {
                dx = -size;
                dy = size - (dotPos - size * 6);
            }
            int alpha = (int) (255.0f * (1.0f - (i / 16.0f)));
            g.fill(cx + dx - 1, cy + dy - 1, cx + dx + 1, cy + dy + 1, (alpha << 24) | 0xFFFFFF);
        }
    }
}
