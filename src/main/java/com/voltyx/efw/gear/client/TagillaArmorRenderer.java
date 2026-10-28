package com.voltyx.efw.gear.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.gear.TagillaMaskItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/** The Tagilla mask on the head (geo/item/armor/tagilla_mask.geo.json). */
public class TagillaArmorRenderer extends GeoArmorRenderer<TagillaMaskItem> {
    public TagillaArmorRenderer() {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(EFW.MOD_ID, "armor/tagilla_mask")));
    }
}
