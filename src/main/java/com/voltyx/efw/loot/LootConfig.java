package com.voltyx.efw.loot;

import com.voltyx.efw.config.ConfigRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Searching of world containers (efw-looting.toml): where the ring around the crosshair sits, whether an unknown container sounds
 * like a chest, and which blocks are always or never searched. Lists take registry names ("minecraft:chest") or a whole mod
 * ("doomsdaydecoration:*").
 */
public final class LootConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue RING_OFFSET;
    public static final ForgeConfigSpec.BooleanValue CHEST_SOUND_FOR_UNKNOWN;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> WHITELIST;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ConfigRegistry.section("Container looting");
        b.push("looting");
        RING_OFFSET = ConfigRegistry.decimal(b, "ringOffset", 0.5, -10.0, 10.0,
                "Shift of the search ring from the screen centre, in GUI pixels (both right and down; tenths allowed).");
        CHEST_SOUND_FOR_UNKNOWN = ConfigRegistry.bool(b, "chestSoundForUnknown", true,
                "Play the vanilla chest sound when searching a container whose own door sound is unknown (crates, bins and the like).");
        BLACKLIST = b.comment("Blocks that are never searched, even if they are loot containers: \"modid:block\" or \"modid:*\"")
                .defineListAllowEmpty(List.of("blacklist"), List::of, o -> o instanceof String);
        WHITELIST = b.comment("Blocks that are always searched when they have an inventory, even if they are not recognised as loot containers: \"modid:block\" or \"modid:*\"")
                .defineListAllowEmpty(List.of("whitelist"), List::of, o -> o instanceof String);
        b.pop();
        SPEC = b.build();
        ConfigRegistry.bind(b, SPEC);
    }

    private LootConfig() {
    }

    public static boolean blacklisted(Block block) {
        return listed(BLACKLIST.get(), block);
    }

    public static boolean whitelisted(Block block) {
        return listed(WHITELIST.get(), block);
    }

    private static boolean listed(List<? extends String> list, Block block) {
        if (list.isEmpty()) {
            return false;
        }
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        if (key == null) {
            return false;
        }
        for (String s : list) {
            String name = s.trim().toLowerCase();
            if (name.equals(key.toString()) || name.equals(key.getNamespace() + ":*")) {
                return true;
            }
        }
        return false;
    }
}
