package com.voltyx.efw.mixin;

import com.voltyx.efw.DeathFade;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** A dead mob is removed after its fall and the fade, not right after the fall ({@link DeathFade}). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathFadeMixin {
    @ModifyConstant(method = "tickDeath", constant = @Constant(intValue = 20))
    private int efw$removeAfterFade(int fallTicks) {
        return DeathFade.applies((LivingEntity) (Object) this) ? fallTicks + DeathFade.FADE_TICKS : fallTicks;
    }
}
