package com.voltyx.efw.gear.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.voltyx.efw.EFW;
import com.voltyx.efw.gear.KawaiiEarsItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * The ears as a GeckoLib armor piece (the way every geo helmet is drawn: the head bone follows the head of the player model), with
 * the two ear bones put into the pose {@link KawaiiEarsAnimator} worked out for the player being drawn.
 * Geometry: assets/efw/geo/item/armor/kawaii_ears.geo.json.
 */
public class KawaiiEarsRenderer extends GeoArmorRenderer<KawaiiEarsItem> {
    /** Resting rotation of the ear bones in the geometry file (degrees); the poses of the animator are offsets from it. */
    private static final float[] BASE_RIGHT = {11.76175f, 9.05478f, 25.33761f};
    private static final float[] BASE_LEFT = {11.76175f, -9.05478f, -25.33761f};

    private final float[] right = new float[3];
    private final float[] left = new float[3];

    public KawaiiEarsRenderer() {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(EFW.MOD_ID, "armor/kawaii_ears")));
    }

    /** With back faces culled: the flat planes of the model (zero thickness) show one face, not two fighting over the same plane. */
    @Override
    public net.minecraft.client.renderer.RenderType getRenderType(KawaiiEarsItem animatable, ResourceLocation texture,
                                                                  MultiBufferSource buffers, float partialTick) {
        return net.minecraft.client.renderer.RenderType.entityCutout(texture);
    }

    @Override
    public void preRender(PoseStack pose, KawaiiEarsItem animatable, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int light, int overlay, float r, float g, float b, float a) {
        super.preRender(pose, animatable, model, buffers, buffer, isReRender, partialTick, light, overlay, r, g, b, a);
        if (getCurrentEntity() instanceof Player player) {
            KawaiiEarsAnimator.offsets(player, Minecraft.getInstance().getFrameTime(), right, left);
        } else {
            java.util.Arrays.fill(right, 0.0f);
            java.util.Arrays.fill(left, 0.0f);
        }
        pose(model, "ear2", BASE_RIGHT, right);
        pose(model, "ear3", BASE_LEFT, left);
    }

    /**
     * GeckoLib keeps a bone's rotation as (-x, -y, z) of the file's angles; the animator's offsets are in the file's convention, so
     * they go through the same sign change.
     */
    private static void pose(BakedGeoModel model, String name, float[] base, float[] offset) {
        GeoBone bone = model.getBone(name).orElse(null);
        if (bone == null) {
            return;
        }
        bone.setRotX(-(float) Math.toRadians(base[0] + offset[0]));
        bone.setRotY(-(float) Math.toRadians(base[1] + offset[1]));
        bone.setRotZ((float) Math.toRadians(base[2] + offset[2]));
    }
}
