package com.voltyx.efw.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** SEVEN stretches the time of drugs (stimulants, the antibiotic) as they are given. */
@Mixin(MobEffectInstance.class)
public interface MobEffectInstanceAccessor {
    @Accessor("duration")
    void efw$setDuration(int duration);
}
