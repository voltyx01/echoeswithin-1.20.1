package com.voltyx.efw.hook.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voltyx.efw.hook.ChainHookEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The thrown hook (the item's model) and its chain. The chain runs from the thrower's off hand (in first person from the fist low at the side of
 * the view) to the hook's eye, sagging a little in flight and taut once a creature is caught.
 */
public class ChainHookRenderer extends EntityRenderer<ChainHookEntity> {
    /** The hook model's height in flight, in blocks. */
    private static final float HOOK_SIZE = 0.7F;

    public ChainHookRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(ChainHookEntity hook, Frustum frustum, double x, double y, double z) {
        // the chain reaches the thrower even when the hook itself is out of view
        return hook.getOwner() != null || super.shouldRender(hook, frustum, x, y, z);
    }

    @Override
    public void render(ChainHookEntity hook, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (hook.state() == ChainHookEntity.PASS || hook.state() == ChainHookEntity.ROLL) {
            return; // past the creature the chain is already in his hand: the synced position would only trail it behind
        }
        Vec3 at = hook.getPosition(partial);
        Vec3 hand = hook.getOwner() instanceof Player owner ? handPosition(owner, partial) : at.add(0.0, 1.0, 0.0);
        Vec3 toHand = hand.subtract(at);
        Vector3f eye = new Vector3f();
        Vector3f handLocal = new Vector3f((float) toHand.x, (float) toHand.y, (float) toHand.z);
        // fastened by its bottom link, the hook points away from the chain
        ChainDraw.hook(pose, buffers, light, eye, new Vector3f(handLocal).negate(), HOOK_SIZE, 0.0F);
        if (hook.getOwner() != null) {
            byte state = hook.state();
            boolean loose = state == ChainHookEntity.FLYING || state == ChainHookEntity.RETURN || state == ChainHookEntity.BOUNCE
                    || state == ChainHookEntity.REHOOK;
            float sag = loose ? 0.06F : 0.015F;
            ChainDraw.chain(pose, buffers, eye, handLocal, new Vector3f(0.0F, -1.0F, 0.0F), sag, 1.0F,
                    p -> LevelRenderer.getLightColor(hook.level(), BlockPos.containing(at.x + p.x, at.y + p.y, at.z + p.z)));
        }
        super.render(hook, yaw, partial, pose, buffers, light);
    }

    /** Where the off hand of the thrower is drawn this frame. */
    static Vec3 handPosition(Player owner, float partial) {
        Minecraft mc = Minecraft.getInstance();
        int side = owner.getMainArm() == HumanoidArm.RIGHT ? -1 : 1;
        Vec3 feet = owner.getPosition(partial);
        if (owner == mc.player && mc.options.getCameraType().isFirstPerson()) {
            return HookFx.firstPersonHand(mc.player); // the fist the first person view draws the chain from
        }
        float bodyYaw = Mth.lerp(partial, owner.yBodyRotO, owner.yBodyRot) * Mth.DEG_TO_RAD;
        double sin = Mth.sin(bodyYaw);
        double cos = Mth.cos(bodyYaw);
        double sideways = side * 0.35;
        double forward = 0.45;
        double up = owner.getEyeHeight() - (owner.isCrouching() ? 0.75 : 0.6);
        return feet.add(-cos * sideways - sin * forward, up, -sin * sideways + cos * forward);
    }

    @Override
    public ResourceLocation getTextureLocation(ChainHookEntity hook) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
