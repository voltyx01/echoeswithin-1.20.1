package com.voltyx.efw.compat;

import com.voltyx.efw.EFW;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * SRP's swimming creatures (the squids) are placed by its own spawn rules, which also let them appear on dry land, where they just lie.
 * A natural spawn of one of them that is not in water is turned down.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class SrpAquaticSpawns {
    private SrpAquaticSpawns() {
    }

    private static boolean aquatic(net.minecraft.world.entity.Mob mob) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (id == null || !id.getNamespace().equals("srparasites")) {
            return false;
        }
        String path = id.getPath();
        return path.contains("squid") || path.contains("fish") || path.contains("dolphin") || path.contains("eel") || path.contains("drown");
    }

    private static boolean inWater(net.minecraft.world.level.LevelAccessor level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        return level.getFluidState(pos).is(FluidTags.WATER) || level.getFluidState(pos.above()).is(FluidTags.WATER);
    }

    @SubscribeEvent
    public static void onPosition(MobSpawnEvent.PositionCheck event) {
        if (aquatic(event.getEntity()) && !inWater(event.getLevel(), event.getX(), event.getY(), event.getZ())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onFinalize(MobSpawnEvent.FinalizeSpawn event) {
        MobSpawnType type = event.getSpawnType();
        boolean natural = type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION || type == MobSpawnType.REINFORCEMENT
                || type == MobSpawnType.PATROL || type == MobSpawnType.EVENT;
        if (natural && aquatic(event.getEntity()) && !inWater(event.getLevel(), event.getX(), event.getY(), event.getZ())) {
            event.setSpawnCancelled(true);
        }
    }
}
