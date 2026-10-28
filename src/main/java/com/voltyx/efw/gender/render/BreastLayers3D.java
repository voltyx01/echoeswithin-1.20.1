package com.voltyx.efw.gender.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.voltyx.efw.EFW;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * The jacket layer of the breasts as 3D voxels when 3D Skin Layers draws the player's own jacket that way (by reflection, the mod
 * stays optional): otherwise the breasts kept the flat vanilla overlay pixels on a 3D body. Each breast gets a mesh from the same
 * skin area the flat overlay box uses, made by 3D Skin Layers' own mesh helper; only the faces the flat box has (front, top, outer
 * side) are taken, the back and the inner side would put stray pixels between the breasts.
 */
final class BreastLayers3D {
    private static final boolean LOADED = ModList.get().isLoaded("skinlayers3d");
    /** The overlay box of each breast: texture origin and size, as in GenderLayer's lBreastWear / rBreastWear. */
    private static final int U_LEFT = 17, U_RIGHT = 21, V = 34, W = 4, H = 5, D = 3;

    private static boolean resolved, broken;
    private static Method getTexture, getMeshHelper, create3DMesh, render;
    private static Object config;

    /** Skin -> {left mesh, right mesh}; null entries when the skin has no jacket pixels there. */
    private static final Map<ResourceLocation, Object[]> MESHES = new HashMap<>();

    private BreastLayers3D() {
    }

    /** 3D Skin Layers draws this player's jacket in 3D right now (it hides the vanilla jacket part for that). */
    static boolean active(AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model) {
        return LOADED && !broken && player.isModelPartShown(PlayerModelPart.JACKET) && !model.jacket.visible && resolve();
    }

    /**
     * Draws the jacket voxels of one breast in the breast's pose (the pose the flat box would be drawn in); false when there is
     * nothing to draw this way, so the caller falls back to the flat box.
     */
    static boolean render(AbstractClientPlayer player, boolean left, PoseStack pose, VertexConsumer buffer, int light, int overlay, float alpha) {
        Object mesh = mesh(player.getSkinTextureLocation(), left);
        if (mesh == null) {
            return false;
        }
        float voxel = configFloat("baseVoxelSize", 1.15F);
        float width = configFloat("bodyVoxelWidthSize", 1.05F);
        pose.pushPose();
        try {
            // the mesh is centred on x and z with its top at 0; the flat box spans x -4..0 (left) or 0..4, y 0..5, z 0..3 in pixels
            pose.translate((left ? -2.0F : 2.0F) / 16.0F, 0.0F, 1.5F / 16.0F);
            pose.scale(width, 1.035F, voxel);
            int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
            render.invoke(mesh, null, pose, buffer, light, overlay, (a << 24) | 0xFFFFFF);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return false;
        } finally {
            pose.popPose();
        }
    }

    private static Object mesh(ResourceLocation skin, boolean left) {
        if (skin == null) {
            return null;
        }
        Object[] pair = MESHES.get(skin);
        if (pair == null) {
            pair = build(skin);
            if (pair == null) {
                return null; // the skin is not readable yet (still downloading): tried again next frame
            }
            if (MESHES.size() > 64) {
                MESHES.clear();
            }
            MESHES.put(skin, pair);
        }
        return pair[left ? 0 : 1];
    }

    private static Object[] build(ResourceLocation skin) {
        try {
            // 3D Skin Layers' own skin cache: the image is theirs and stays open
            NativeImage image = (NativeImage) getTexture.invoke(null, skin, null);
            if (image == null || image.getWidth() != 64 || image.getHeight() != 64) {
                return null;
            }
            Object helper = getMeshHelper.invoke(null);
            return new Object[] {breastMesh(helper, image, true), breastMesh(helper, image, false)};
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return null;
        }
    }

    private static Object breastMesh(Object helper, NativeImage skin, boolean left) throws ReflectiveOperationException {
        int u = left ? U_LEFT : U_RIGHT;
        try (NativeImage part = new NativeImage(64, 64, true)) {
            // the box's texture layout: top (u+D, V), front (u+D, V+D), sides (u, V+D) and (u+D+W, V+D); left keeps the first side
            copy(skin, part, u + D, V, W, D);
            copy(skin, part, u + D, V + D, W, H);
            if (left) {
                copy(skin, part, u, V + D, D, H);
            } else {
                copy(skin, part, u + D + W, V + D, D, H);
            }
            return create3DMesh.invoke(helper, part, W, H, D, u, V, true, 0.0F, false);
        }
    }

    private static void copy(NativeImage from, NativeImage to, int x0, int y0, int w, int h) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                to.setPixelRGBA(x, y, from.getPixelRGBA(x, y));
            }
        }
    }

    private static float configFloat(String name, float fallback) {
        try {
            return config != null ? config.getClass().getField(name).getFloat(config) : fallback;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }

    private static boolean resolve() {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> skinUtil = Class.forName("dev.tr7zw.skinlayers.SkinUtil");
                Class<?> skullSettings = Class.forName("dev.tr7zw.skinlayers.accessor.SkullSettings");
                getTexture = skinUtil.getMethod("getTexture", ResourceLocation.class, skullSettings);
                Class<?> api = Class.forName("dev.tr7zw.skinlayers.api.SkinLayersAPI");
                getMeshHelper = api.getMethod("getMeshHelper");
                create3DMesh = Class.forName("dev.tr7zw.skinlayers.api.MeshHelper").getMethod("create3DMesh", NativeImage.class,
                        int.class, int.class, int.class, int.class, int.class, boolean.class, float.class, boolean.class);
                render = Class.forName("dev.tr7zw.skinlayers.api.Mesh").getMethod("render", ModelPart.class, PoseStack.class,
                        VertexConsumer.class, int.class, int.class, int.class);
                config = Class.forName("dev.tr7zw.skinlayers.SkinLayersModBase").getField("config").get(null);
            } catch (ReflectiveOperationException | RuntimeException e) {
                fail(e);
            }
        }
        return !broken;
    }

    private static void fail(Throwable e) {
        if (!broken) {
            broken = true;
            EFW.LOGGER.warn("[Gender] 3D Skin Layers breasts disabled, the flat overlay is used: {}", e.toString());
        }
    }
}
