package com.voltyx.efw.gore;

import com.voltyx.efw.EFW;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Cities and structures carry no corpses: the bodies, animal carcasses and heads of Horror Element Mod and Doomsday Decoration
 * (the bloodstains stay) are replaced by air in every chunk that loads, whichever structure or mod placed them.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class CorpseCleaner {
    private static final String HORROR = "horror_element_mod";
    private static final String DOOMSDAY = "doomsday_decoration";

    /** Horror Element Mod blocks that are a body, a carcass, a head or a part of one. */
    private static final java.util.Set<String> HORROR_BLOCKS = java.util.Set.of(
            "alex_head", "barbed_wire_body", "beheaded_body", "chained_entity", "colonial_marine_body", "colonial_marine_head",
            "cordycepsfarmer", "crucified_body", "dead_cow", "dead_experiment", "dead_pig", "desert_marine_head",
            "german_officer_head", "hanging_corpse_1", "hanging_corpse_2", "hanging_villager", "hazzmat_body", "hazzmat_head",
            "herobrine_head", "human_remains", "impaled_head", "impaled_knight", "impaled_skeleton", "impaled_soldier",
            "marine_body", "phished_head", "primitive_head", "rotten_body", "russian_officer_head", "scientist_body",
            "self_terminated_body", "severed_hand", "slashed_skull", "sliced_body", "spider_head", "spiked_body",
            "spiked_villager", "steve_head", "trapped_leg", "villager_head");

    private static final Predicate<BlockState> IS_CORPSE = state -> isCorpse(state.getBlock());
    private static final Map<Block, Boolean> CACHE = new HashMap<>();

    private CorpseCleaner() {
    }

    private static boolean isCorpse(Block block) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(block, b -> {
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
                if (id.getNamespace().equals(HORROR)) {
                    return HORROR_BLOCKS.contains(id.getPath());
                }
                return id.getNamespace().equals(DOOMSDAY) && (id.getPath().equals("bodybag") || id.getPath().startsWith("remains_"));
            });
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load e) {
        LevelAccessor level = e.getLevel();
        ChunkAccess chunk = e.getChunk();
        if (level == null || level.isClientSide()) {
            return;
        }
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(IS_CORPSE)) {
                continue;
            }
            int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        if (IS_CORPSE.test(section.getBlockState(x, y, z))) {
                            chunk.setBlockState(new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z),
                                    Blocks.AIR.defaultBlockState(), false);
                        }
                    }
                }
            }
        }
    }
}
