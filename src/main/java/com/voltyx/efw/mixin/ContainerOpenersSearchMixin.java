package com.voltyx.efw.mixin;

import com.voltyx.efw.loot.LootContainers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A container being searched is open (lid, doors, sound) without a window: the periodic recount of openers only sees players with
 * the container's menu open and would shut it again a few ticks later, so the searching players count as openers too.
 */
@Mixin(ContainerOpenersCounter.class)
public abstract class ContainerOpenersSearchMixin {
    @Inject(method = "getOpenCount", at = @At("RETURN"), cancellable = true)
    private void efw$countSearchers(Level level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int searching = LootContainers.searchersAt(level, pos);
        if (searching > 0) {
            cir.setReturnValue(cir.getReturnValue() + searching);
        }
    }
}
