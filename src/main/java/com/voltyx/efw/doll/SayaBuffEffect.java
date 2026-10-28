package com.voltyx.efw.doll;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

/**
 * "Feeling of Guilt" (PotionSayaBuff): the frenzy of the doll; it throws off slowness, fatigue and weakness, and while it lasts they
 * cannot be given again (DollAbility.onEffectApplicable).
 */
public class SayaBuffEffect extends MobEffect {
    public static final Set<MobEffect> THROWN_OFF = Set.of(MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SLOWDOWN, MobEffects.WEAKNESS);

    public SayaBuffEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xCC1133);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        for (MobEffect effect : THROWN_OFF) {
            if (entity.hasEffect(effect)) {
                entity.removeEffect(effect);
            }
        }
    }
}
