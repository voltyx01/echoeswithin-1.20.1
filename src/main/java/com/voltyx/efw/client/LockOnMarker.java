package com.voltyx.efw.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.voltyx.efw.EFW;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** The lock-on marker: a camera facing 16x16 icon over the target, drawn without depth test. */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class LockOnMarker {
    private static final ResourceLocation TEXTURE = new ResourceLocation(EFW.MOD_ID, "textures/gui/lock.png");

    private LockOnMarker() {
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !LockOnClient.isLockedOn()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LivingEntity target = LockOnClient.target();
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        Vec3 at = new Vec3(net.minecraft.util.Mth.lerp(pt, target.xo, target.getX()),
                net.minecraft.util.Mth.lerp(pt, target.yo, target.getY()) + target.getBbHeight() * 0.66,
                net.minecraft.util.Mth.lerp(pt, target.zo, target.getZ()));

        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        stack.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        stack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        stack.scale(-0.02F, -0.02F, 0.02F);

        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        Matrix4f m = stack.last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(m, -8, -8, 0).uv(0, 0).endVertex();
        b.vertex(m, -8, 8, 0).uv(0, 1).endVertex();
        b.vertex(m, 8, 8, 0).uv(1, 1).endVertex();
        b.vertex(m, 8, -8, 0).uv(1, 0).endVertex();
        BufferUploader.drawWithShader(b.end());

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        stack.popPose();
    }
}
