package com.voltyx.efw.hook.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.voltyx.efw.EFW;
import com.voltyx.efw.hook.ChainHookItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * The chain in the off hand in first person, while it is not simply held:
 * <ul>
 * <li>swung (the use key held): a length of chain with the hook circles low at the side of the view, round the view's side axis, a
 * wheel turned a little toward the eye; it grows wider and faster the longer it is swung;</li>
 * <li>thrown: the hand holds nothing more to draw, the chain runs from this fist to the hook (ChainHookRenderer, from
 * {@link HookFx#firstPersonHand}).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class HookSwingView {
    /** Where the fist is in the view (lower and further out than an item in the off hand). */
    static final float SIDE = 0.62F;
    static final float DOWN = -0.62F;
    static final float DEPTH = -0.85F;
    /** Size of one block of chain in the view. */
    private static final float SCALE = 0.45F;
    /** The wheel turned toward the eye so it reads as a circle. */
    private static final float TURN = 35.0F;
    private static final float MIN_RADIUS = 0.5F;
    private static final float MAX_RADIUS = 1.1F;
    private static final float HOOK_SIZE = 0.8F;

    private HookSwingView() {
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (event.getHand() != InteractionHand.OFF_HAND || player == null || !(player.getOffhandItem().getItem() instanceof ChainHookItem)) {
            return;
        }
        if (HookClient.hasHookOut(player)) {
            event.setCanceled(true); // the hook is on the end of the chain now, not in the hand
            return;
        }
        if (!player.isUsingItem() || player.getUsedItemHand() != InteractionHand.OFF_HAND) {
            return;
        }
        event.setCanceled(true);
        float ticks = player.getTicksUsingItem() + event.getPartialTick();
        float charge = Mth.clamp(ticks / ChainHookItem.FULL_CHARGE, 0.0F, 1.0F);
        float radius = MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * charge;
        float angle = ChainHookItem.swingAngle(ticks);
        int side = player.getMainArm() == HumanoidArm.RIGHT ? -1 : 1;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(side * SIDE, DOWN, DEPTH);
        pose.mulPose(Axis.YP.rotationDegrees(side * TURN));
        pose.scale(SCALE, SCALE, SCALE);
        Vector3f grip = new Vector3f();
        // round the side axis: the hook goes up, forward, down and back past the fist
        Vector3f end = new Vector3f(side * 0.1F, Mth.sin(angle) * radius, -Mth.cos(angle) * radius);
        int light = event.getPackedLight();
        ChainDraw.chain(pose, event.getMultiBufferSource(), grip, end, new Vector3f(0.0F, -1.0F, 0.0F), 0.02F, 1.0F, p -> light);
        ChainDraw.hook(pose, event.getMultiBufferSource(), light, end, new Vector3f(end).sub(grip), HOOK_SIZE, 0.0F);
        pose.popPose();
    }
}
