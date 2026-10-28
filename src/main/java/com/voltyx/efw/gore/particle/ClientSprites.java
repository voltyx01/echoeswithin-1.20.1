package com.voltyx.efw.gore.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/** The four blood squares (efw:particle/blood_square_N), on the block atlas as in 1.12 (assets/minecraft/atlases/blocks.json). */
public final class ClientSprites {
    private static final ResourceLocation[] BLOOD_RLS = {
            new ResourceLocation("efw", "particle/blood_square_0"), new ResourceLocation("efw", "particle/blood_square_1"),
            new ResourceLocation("efw", "particle/blood_square_2"), new ResourceLocation("efw", "particle/blood_square_3")};

    private ClientSprites() {
    }

    public static TextureAtlasSprite getBloodSprite(int variantIndex) {
        int idx = Math.floorMod(variantIndex, BLOOD_RLS.length);
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        return atlas.getSprite(BLOOD_RLS[idx]);
    }
}
