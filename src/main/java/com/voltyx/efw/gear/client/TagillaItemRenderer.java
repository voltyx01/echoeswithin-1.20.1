package com.voltyx.efw.gear.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voltyx.efw.EFW;
import com.voltyx.efw.gear.TagillaMaskItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * The Tagilla mask as an item (inventory, hand, ground). The model is made for a head standing 1.5 blocks above its origin, so it is
 * lowered to the middle of the item's space first.
 */
public class TagillaItemRenderer extends GeoItemRenderer<TagillaMaskItem> {
    public TagillaItemRenderer() {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(EFW.MOD_ID, "armor/tagilla_mask")));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        pose.pushPose();
        float scale = context == ItemDisplayContext.GUI ? 1.5f : 1.0f;
        pose.translate(0.5, 0.5, 0.5);
        pose.scale(scale, scale, scale);
        pose.translate(-0.5, -0.5 - 1.78, -0.5);
        super.renderByItem(stack, context, pose, buffer, light, overlay);
        pose.popPose();
    }
}
