package com.voltyx.efw.hook.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voltyx.efw.hook.ChainHookItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The swing seen from outside: while the use key is held the chain is not in the hand as an item (ItemInHandLayerMixin hides it)
 * but circles from the fist round the world's up, the hook on its end, wider and faster the longer it is swung. The arm is raised
 * over the head by the swing animation, so the circle goes over the thrower.
 */
public class HookSwingLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final float MIN_RADIUS = 0.3F;
    private static final float MAX_RADIUS = 0.8F;
    private static final float HOOK_SIZE = 0.6F;

    public HookSwingLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /** The player swings the chain in the off hand right now. */
    public static boolean swinging(Player player) {
        return player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND && player.getUseItem().getItem() instanceof ChainHookItem;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float age, float headYaw, float headPitch) {
        Minecraft mc = Minecraft.getInstance();
        if (!swinging(player) || player.isInvisible() || mc.screen != null) {
            return;
        }
        HumanoidArm arm = player.getMainArm().getOpposite();
        int side = arm == HumanoidArm.LEFT ? -1 : 1; // as ItemInHandLayer moves to the fist
        pose.pushPose();
        getParentModel().translateToHand(arm, pose);
        pose.translate(side / 16.0F, 0.6F, 0.0F); // down the arm to the fist

        // the world's up in this pose (the entity is drawn in the camera's turned space)
        Matrix3f toRender = new Matrix3f(pose.last().pose());
        float scale = toRender.getScale(new Vector3f()).x;
        Quaternionf toView = new Quaternionf(mc.gameRenderer.getMainCamera().rotation()).conjugate();
        Vector3f up = new Matrix3f(toRender).invert().transform(toView.transform(new Vector3f(0.0F, 1.0F, 0.0F))).normalize();
        Vector3f u = (Math.abs(up.y) < 0.9F ? new Vector3f(0.0F, 1.0F, 0.0F) : new Vector3f(1.0F, 0.0F, 0.0F)).cross(up).normalize();
        Vector3f v = new Vector3f(up).cross(u).normalize();

        float ticks = player.getTicksUsingItem() + partialTick;
        float charge = Mth.clamp(ticks / ChainHookItem.FULL_CHARGE, 0.0F, 1.0F);
        float radius = (MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * charge) / scale;
        float angle = ChainHookItem.swingAngle(ticks);
        Vector3f grip = new Vector3f();
        Vector3f end = new Vector3f(u).mul(Mth.cos(angle) * radius).add(new Vector3f(v).mul(Mth.sin(angle) * radius))
                .add(new Vector3f(up).mul((0.12F + 0.08F * charge) / scale));
        ChainDraw.chain(pose, buffers, grip, end, new Vector3f(up).negate(), 0.02F, 1.0F / scale, p -> light);
        ChainDraw.hook(pose, buffers, light, end, new Vector3f(end).sub(grip), HOOK_SIZE / scale, 0.0F);
        pose.popPose();
    }
}
