package com.voltyx.efw.fireweapon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.voltyx.efw.fireweapon.FireWeaponHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;

import java.util.List;

/**
 * Draws a wrapped / burning / charred weapon with its composited textures instead of the item atlas (see
 * {@link FireWeaponTextures}), adds the emissive pass that glows in the dark and lets the sparks fly off the burning pixels.
 */
public final class FireWeaponItemRenderer {
    private static final int FULL_BRIGHT = 0xF000F0;

    private FireWeaponItemRenderer() {
    }

    /** Returns true when the item was drawn here and the normal drawing has to be skipped. */
    public static boolean render(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose, MultiBufferSource buffers, int light,
                                 int overlay, BakedModel model) {
        if (stack.isEmpty() || model.isCustomRenderer() || !FireWeaponHelper.hasVisibleCloth(stack) || !FireWeaponHelper.isWeapon(stack)) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        long now = mc.level.getGameTime();
        TextureAtlasSprite sprite = model.getParticleIcon();
        FireWeaponTextures.Holder holder = FireWeaponTextures.get(stack, sprite, now);
        if (holder == null || holder.base() == null) {
            return false;
        }

        pose.pushPose();
        BakedModel transformed = ForgeHooksClient.handleCameraTransforms(pose, model, context, leftHand);
        pose.translate(-0.5F, -0.5F, -0.5F);

        // lit picture (with the enchantment glint if there is one)
        // Cutout, not translucent: the sorted translucent batch made the whole picture flicker on weapons in the world (entity pass).
        RenderType type = RenderType.entityCutoutNoCull(holder.base());
        VertexConsumer base = ItemRenderer.getFoilBufferDirect(buffers, type, true, stack.hasFoil());
        drawQuads(transformed, pose, new UvRemap(base), light, overlay);

        // emissive picture: only the glowing pixels, full bright, added on top
        if (holder.emissive() != null) {
            // the lit picture goes out first, the glow always after it (the entity pass draws its batches in no fixed order)
            if (buffers instanceof MultiBufferSource.BufferSource source) {
                source.endBatch(type);
            }
            VertexConsumer glow = buffers.getBuffer(RenderType.eyes(holder.emissive()));
            drawQuads(transformed, pose, new UvRemap(glow), FULL_BRIGHT, overlay);
        }

        // sparks from the burning pixels (in the world the pose is eye space for the hand and for entities alike)
        if (FireWeaponHelper.isIgnited(stack) && !holder.voxels().isEmpty()) {
            if (FireSparks.inScreen()) {
                // slot item or the player model in an inventory: sparks live in screen space
                FireSparks.emitGui(holder.voxels(), FireWeaponHelper.isSoaked(stack), pose.last().pose());
            } else if (context != ItemDisplayContext.GUI) {
                // every level pass (hand, third person, Better Combat's player model, frames, dropped items): the pose is eye space
                boolean firstPerson = context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
                FireSparks.emit(holder.voxels(), FireWeaponHelper.isSoaked(stack), pose.last().pose(), firstPerson);
            }
        }
        pose.popPose();
        return true;
    }

    private static void drawQuads(BakedModel model, PoseStack pose, UvRemap consumer, int light, int overlay) {
        RandomSource random = RandomSource.create(42L);
        for (Direction dir : Direction.values()) {
            emit(model.getQuads(null, dir, random), pose, consumer, light, overlay);
        }
        emit(model.getQuads(null, null, random), pose, consumer, light, overlay);
    }

    private static void emit(List<BakedQuad> quads, PoseStack pose, UvRemap consumer, int light, int overlay) {
        for (BakedQuad quad : quads) {
            consumer.sprite = quad.getSprite();
            consumer.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, light, overlay);
        }
    }

    /** Turns the atlas coordinates of the quads into 0..1 coordinates of the sprite, which is what the composited textures are. */
    private static final class UvRemap implements VertexConsumer {
        private final VertexConsumer delegate;
        TextureAtlasSprite sprite;

        UvRemap(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            delegate.color(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            if (sprite != null) {
                float du = sprite.getU1() - sprite.getU0();
                float dv = sprite.getV1() - sprite.getV0();
                u = du > 1.0E-6f ? Math.max(0f, Math.min(1f, (u - sprite.getU0()) / du)) : u;
                v = dv > 1.0E-6f ? Math.max(0f, Math.min(1f, (v - sprite.getV0()) / dv)) : v;
            }
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            delegate.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
