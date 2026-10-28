package com.voltyx.efw.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A still picture shown in place of a FancyMenu video background while WaterMedia starts the video (half a second of black
 * otherwise). The poster is a png next to the video with the same name: bg.mp4 -> bg.png. No png, nothing changes.
 */
public final class VideoPoster {
    private static final String LOCAL = "[source:local]";
    private static final Map<String, Optional<Poster>> POSTERS = new HashMap<>();

    private VideoPoster() {
    }

    /** A loaded poster: its texture and the picture size, for fitting it the way the video is fitted. */
    public record Poster(ResourceLocation texture, int width, int height) {
    }

    /** The poster for a resolved FancyMenu video source, loaded on first use; null when the video has none. */
    public static Poster forSource(String source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        return POSTERS.computeIfAbsent(source, VideoPoster::load).orElse(null);
    }

    private static Optional<Poster> load(String source) {
        if (!source.startsWith(LOCAL)) {
            return Optional.empty(); // web sources have no file next to them
        }
        String relative = source.substring(LOCAL.length()).replace('\\', '/');
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        int dot = relative.lastIndexOf('.');
        if (dot <= relative.lastIndexOf('/')) {
            return Optional.empty();
        }
        Path png = FMLPaths.GAMEDIR.get().resolve(relative.substring(0, dot) + ".png");
        if (!Files.isRegularFile(png)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(png)) {
            NativeImage image = NativeImage.read(in);
            int width = image.getWidth();
            int height = image.getHeight();
            ResourceLocation id = new ResourceLocation("efw", "video_poster/" + Integer.toHexString(source.hashCode()));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            return Optional.of(new Poster(id, width, height));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
