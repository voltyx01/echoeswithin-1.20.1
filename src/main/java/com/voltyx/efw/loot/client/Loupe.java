package com.voltyx.efw.loot.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.voltyx.efw.EFW;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * The loupe quad, drawn at once with the plain textured shader (not through a text render type, which a shader pack takes over and
 * leaves nothing of), over everything, in the middle of the matrix it is given.
 */
public final class Loupe {
    private static final ResourceLocation ICON = new ResourceLocation(EFW.MOD_ID, "textures/item/placer_tool.png");

    private Loupe() {
    }

    public static void draw(Matrix4f m, float shade, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, ICON);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.vertex(m, -8, -8, 0).uv(0, 0).color(shade, shade, shade, alpha).endVertex();
        b.vertex(m, -8, 8, 0).uv(0, 1).color(shade, shade, shade, alpha).endVertex();
        b.vertex(m, 8, 8, 0).uv(1, 1).color(shade, shade, shade, alpha).endVertex();
        b.vertex(m, 8, -8, 0).uv(1, 0).color(shade, shade, shade, alpha).endVertex();
        BufferUploader.drawWithShader(b.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
