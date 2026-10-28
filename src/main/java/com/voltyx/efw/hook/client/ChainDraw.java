package com.voltyx.efw.hook.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.voltyx.efw.EFW;
import com.voltyx.efw.hook.HookContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Drawing the hook (the item's model) and its chain (vanilla chain links along a line with a sag): the thrown hook and the swing. */
final class ChainDraw {
    /** The hook without the item's display transforms; the chain is fastened to its bottom link at (8, 0, 8) px, the hook grows up. */
    static final ResourceLocation HOOK_MODEL = new ResourceLocation(EFW.MOD_ID, "item/chain_hook_head");

    interface Light {
        int at(Vector3f point);
    }

    private ChainDraw() {
    }

    /**
     * The hook fastened at {@code attach}, pointing along {@code away} (away from the chain); {@code size} is its height in the units
     * of {@code pose} (the model is one unit), {@code spin} turns it round its own length.
     */
    static void hook(PoseStack pose, MultiBufferSource buffers, int light, Vector3f attach, Vector3f away, float size, float spin) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getModelManager().getModel(HOOK_MODEL);
        pose.pushPose();
        pose.translate(attach.x, attach.y, attach.z);
        Vector3f dir = new Vector3f(away);
        if (dir.lengthSquared() > 1.0E-6F) {
            pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), dir.normalize()));
        }
        pose.mulPose(Axis.YP.rotation(spin));
        pose.scale(size, size, size);
        // ItemRenderer moves the model by -0.5 on every axis itself: what is left puts the fastening point (8, 0, 8 px) on the origin
        pose.translate(0.0, 0.5, 0.0);
        mc.getItemRenderer().render(new ItemStack(HookContent.CHAIN_HOOK.get()), ItemDisplayContext.NONE, false, pose, buffers, light,
                OverlayTexture.NO_OVERLAY, model);
        pose.popPose();
    }

    /**
     * Links from {@code from} to {@code to}, sagging along {@code down} by {@code sag} of the length in the middle. The vanilla chain
     * block is drawn at its own size (one block a piece, the pieces only evened out to fit the length), so the links look like the
     * placed chain; {@code size} is how long one block is in the units of {@code pose}.
     */
    static void chain(PoseStack pose, MultiBufferSource buffers, Vector3f from, Vector3f to, Vector3f down, float sag, float size, Light light) {
        float length = from.distance(to);
        if (length < 0.05F * size) {
            return;
        }
        int pieces = Math.max(1, Math.round(length / size));
        BlockState chain = Blocks.CHAIN.defaultBlockState();
        Vector3f prev = new Vector3f(from);
        for (int i = 1; i <= pieces; i++) {
            float t = i / (float) pieces;
            Vector3f next = new Vector3f(to).sub(from).mul(t).add(from).add(new Vector3f(down).mul(sag * length * 4.0F * t * (1.0F - t)));
            Vector3f piece = new Vector3f(next).sub(prev);
            float len = piece.length();
            if (len > 1.0E-5F) {
                pose.pushPose();
                pose.translate(prev.x, prev.y, prev.z);
                pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), piece.div(len)));
                pose.scale(size, len, size);
                pose.translate(-0.5, 0.0, -0.5);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(chain, pose, buffers, light.at(prev), OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            prev = next;
        }
    }
}
