package com.voltyx.efw.mixin;

import com.voltyx.efw.compat.LostCitiesCompat;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Streams Reflowing carves its water into every chunk it generates, including the streets and buildings of Lost Cities (water
 * columns standing in the city). Its worldgen features (the carver, the current, the water skin, the bank plants, the snow
 * cleanup) place nothing in a chunk that Lost Cities marks as a city.
 */
@Pseudo
@Mixin(targets = {"dev.streamsreflowing.worldgen.RiverCarverFeature", "dev.streamsreflowing.worldgen.StreamRiverFlowFeature",
        "dev.streamsreflowing.worldgen.WaterSkinFeature", "dev.streamsreflowing.worldgen.StreamVegetationFeature",
        "dev.streamsreflowing.worldgen.StreamSnowCleanupFeature"}, remap = false)
public abstract class StreamsSkipCitiesMixin {
    @Inject(method = "m_142674_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void efw$skipCities(FeaturePlaceContext<?> context, CallbackInfoReturnable<Boolean> cir) {
        if (LostCitiesCompat.isCityChunk(context.level(), context.origin().getX() >> 4, context.origin().getZ() >> 4)) {
            cir.setReturnValue(false);
        }
    }
}
