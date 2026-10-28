package com.voltyx.efw.tech.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Render types of the tech block entities: fullbright colour quads and lines, and the opaque bodycam feed. */
public final class TechRenderTypes extends RenderType {
    /** Fullbright translucent colour quads without culling (screens, indicators, blood stains). */
    public static final RenderType QUADS = create("efw_tech_quads", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256,
            false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(NO_LIGHTMAP)
                    .createCompositeState(false));

    /** 1px lines (tactical boxes on the bodycam feed). */
    public static final RenderType LINES = create("efw_tech_lines", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.DEBUG_LINES, 256,
            false, false, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(NO_LIGHTMAP)
                    .createCompositeState(false));

    private static RenderType feed;

    private TechRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                            Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    /** The bodycam picture: opaque, so that the zero alpha of the sky in the render target does not punch holes. */
    public static RenderType feed(ResourceLocation texture) {
        if (feed == null) {
            feed = create("efw_bodycam_feed", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 256, false, false,
                    CompositeState.builder()
                            .setShaderState(POSITION_TEX_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(NO_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(NO_LIGHTMAP)
                            .createCompositeState(false));
        }
        return feed;
    }
}
