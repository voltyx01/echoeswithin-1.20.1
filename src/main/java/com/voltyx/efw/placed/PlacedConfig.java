package com.voltyx.efw.placed;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/** Settings of the placed items (PlacedItemInspectConfig): which items open the inspect screen when they are picked up. */
public final class PlacedConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> AUTO_INSPECT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Open the inspect screen when an item listed below is picked up from the floor")
                .define("enabled", true);
        AUTO_INSPECT = b.comment("Registry names of the items that open the inspect screen when they are picked up as a placed item")
                .defineListAllowEmpty("autoInspectItems", List.of("efw:doll", "efw:bloody_necklace"), o -> o instanceof String);
        SPEC = b.build();
    }

    private PlacedConfig() {
    }

    public static boolean isAutoInspect(ItemStack stack) {
        if (stack.isEmpty() || !ENABLED.get()) {
            return false;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) {
            return false;
        }
        for (String s : AUTO_INSPECT.get()) {
            String name = s.trim().toLowerCase();
            if (name.equals(key.toString()) || name.equals(key.getPath())) {
                return true;
            }
        }
        return false;
    }
}
