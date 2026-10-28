package com.voltyx.efw.tech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.voltyx.efw.EFW;
import com.voltyx.efw.tech.OldRadioBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/** Old radio (port of TileEntityOldRadioRenderer). */
public class OldRadioRenderer implements BlockEntityRenderer<OldRadioBlockEntity> {
    private static final BedrockModel MODEL = new BedrockModel();
    private static final ResourceLocation GEO = new ResourceLocation(EFW.MOD_ID, "bedrock/oldradio.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(EFW.MOD_ID, "textures/block/oldradio.png");

    public OldRadioRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(OldRadioBlockEntity te, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        MODEL.load(GEO, null);
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(TerminalRenderer.facingAngle(te.getBlockState().getValue(HorizontalDirectionalBlock.FACING))));
        MODEL.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay);
        pose.popPose();
    }
}
