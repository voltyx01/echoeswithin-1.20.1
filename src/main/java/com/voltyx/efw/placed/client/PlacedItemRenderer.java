package com.voltyx.efw.placed.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.voltyx.efw.EFW;
import com.voltyx.efw.placed.PlacedItemBlock;
import com.voltyx.efw.placed.PlacedItemBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Draws the item of a placed item block with its offset, rotation and scale, and the small loupe icon above it. */
public class PlacedItemRenderer implements BlockEntityRenderer<PlacedItemBlockEntity> {
    private static final ResourceLocation ICON = new ResourceLocation(EFW.MOD_ID, "textures/item/placer_tool.png");
    private static final double FULL_DIST = 4.5;
    private static final double MAX_DIST = 6.0;

    public PlacedItemRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(PlacedItemBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = be.getStack();
        if (stack.isEmpty() || be.getLevel() == null) {
            return;
        }
        int turns = be.getBlockState().getValue(PlacedItemBlock.TURNS);

        pose.pushPose();
        turn(pose, turns);
        pose.translate(be.offsetX(), be.offsetY(), be.offsetZ());
        pose.mulPose(Axis.YP.rotationDegrees(be.yaw()));
        pose.mulPose(Axis.XP.rotationDegrees(be.pitch()));
        pose.mulPose(Axis.ZP.rotationDegrees(be.roll()));
        float scale = be.scale();
        if (scale != 1.0f) {
            pose.scale(scale, scale, scale);
        }
        net.minecraft.world.entity.EquipmentSlot worn = WornItemModel.wornSlot(stack);
        if (worn != null) {
            WornItemModel.render(stack, worn, be.getLevel(), pose, buffers, light, partial);
        } else {
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose,
                    buffers, be.getLevel(), (int) be.getBlockPos().asLong());
        }
        pose.popPose();

        renderIcon(be, turns, partial, pose, buffers);
    }

    /** The structure was placed turned: the whole layout turns around the middle of the block. */
    private static void turn(PoseStack pose, int turns) {
        if (turns != 0) {
            pose.translate(0.5, 0.0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-90.0f * turns));
            pose.translate(-0.5, 0.0, -0.5);
        }
    }

    private static float[] turned(float x, float z, int turns) {
        for (int i = 0; i < turns; i++) {
            float nx = 1.0f - z;
            z = x;
            x = nx;
        }
        return new float[]{x, z};
    }

    private void renderIcon(PlacedItemBlockEntity be, int turns, float partial, PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        Entity view = mc.getCameraEntity();
        if (view == null || be.getLevel() == null) {
            return;
        }
        float[] at = turned(be.offsetX(), be.offsetZ(), turns);
        double wx = be.getBlockPos().getX() + at[0];
        double wy = be.getBlockPos().getY() + be.offsetY();
        double wz = be.getBlockPos().getZ() + at[1];
        Vec3 eye = view.getEyePosition(partial);
        double distSq = eye.distanceToSqr(wx, wy, wz);
        boolean hovered = mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(be.getBlockPos());

        float alpha;
        if (hovered) {
            alpha = 1.0f;
        } else {
            if (distSq > MAX_DIST * MAX_DIST) {
                return;
            }
            // no icon through walls
            BlockHitResult los = be.getLevel().clip(new ClipContext(eye, new Vec3(wx, wy + 0.15, wz), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, view));
            if (los.getType() == HitResult.Type.BLOCK && !los.getBlockPos().equals(be.getBlockPos())) {
                return;
            }
            double dist = Math.sqrt(distSq);
            alpha = dist > FULL_DIST ? (float) Math.max(0.0, Math.min(1.0, (MAX_DIST - dist) / (MAX_DIST - FULL_DIST))) : 1.0f;
            if (alpha < 0.02f) {
                return;
            }
        }

        double lift = be.offsetY() + 0.32 + Math.max(0.0, (be.scale() - 1.0f) * 0.15) + be.iconY();
        pose.pushPose();
        pose.translate(at[0] + be.iconX(), lift, at[1] + be.iconZ());
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float s = hovered ? 0.018f : 0.015f;
        pose.scale(-s, -s, s);
        com.voltyx.efw.loot.client.Loupe.draw(pose.last().pose(), hovered ? 1.0f : 217 / 255.0f, alpha);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(PlacedItemBlockEntity be) {
        return false;
    }
}
