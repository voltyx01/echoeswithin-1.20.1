package com.voltyx.efw.client.eat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bitten food models: while a living entity eats a food, its item is drawn with the bitten sprite of the stage it has reached
 * (thresholds of the Eating Animation mod: 35 %, 70 %, 90 % of the eating time, a little earlier here so the last bite shows). The
 * flat item model is built from the sprite the way vanilla builds item/generated, and keeps the transforms of the food's own model.
 */
public final class BittenModels {
    private static final float[] THRESHOLDS = {0.3f, 0.6f, 0.85f};
    private static final Map<Item, BakedModel[]> CACHE = new ConcurrentHashMap<>();
    private static TextureAtlas atlas;

    private BittenModels() {
    }

    /** The atlas was rebuilt: the old models point at old sprites. */
    public static void clear() {
        CACHE.clear();
        atlas = null;
    }

    public static BakedModel select(BakedModel model, ItemStack stack, @Nullable LivingEntity entity) {
        if (entity == null || !entity.isUsingItem() || stack.isEmpty() || model.isGui3d() || model.isCustomRenderer()) {
            return model;
        }
        ItemStack using = entity.getUseItem();
        if (using != stack && !ItemStack.isSameItem(using, stack)) {
            return model;
        }
        ResourceLocation texture = BiteSprites.TEXTURES.get(stack.getItem());
        if (texture == null) {
            return model;
        }
        int duration = stack.getUseDuration();
        if (duration <= 0) {
            return model;
        }
        float progress = 1.0f - entity.getUseItemRemainingTicks() / (float) duration;
        int stage = -1;
        for (int s = 0; s < THRESHOLDS.length; s++) {
            if (progress >= THRESHOLDS[s]) {
                stage = s;
            }
        }
        if (stage < 0) {
            return model;
        }
        BakedModel[] stages = CACHE.computeIfAbsent(stack.getItem(), i -> new BakedModel[BiteSprites.STAGES]);
        BakedModel bitten = stages[stage];
        if (bitten == null) {
            bitten = build(texture, stage, model);
            stages[stage] = bitten;
        }
        return bitten == null ? model : bitten;
    }

    @Nullable
    private static BakedModel build(ResourceLocation texture, int stage, BakedModel original) {
        if (atlas == null) {
            atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        }
        ResourceLocation id = BiteSprites.spriteId(texture, stage);
        TextureAtlasSprite sprite = atlas.getSprite(id);
        if (sprite == null || sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
            return null;
        }
        List<BakedQuad> quads = new ArrayList<>();
        FaceBakery bakery = new FaceBakery();
        for (BlockElement element : new ItemModelGenerator().processFrames(0, "layer0", sprite.contents())) {
            for (Map.Entry<Direction, BlockElementFace> face : element.faces.entrySet()) {
                quads.add(bakery.bakeQuad(element.from, element.to, face.getValue(), sprite, face.getKey(), BlockModelRotation.X0_Y0,
                        element.rotation, element.shade, id));
            }
        }
        return new Flat(quads, sprite, original);
    }

    private record Flat(List<BakedQuad> quads, TextureAtlasSprite sprite, BakedModel original) implements BakedModel {
        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            return side == null ? quads : List.of();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean usesBlockLight() {
            return original.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return sprite;
        }

        @Override
        public ItemTransforms getTransforms() {
            return original.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }
}
