package com.voltyx.efw.coth;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** The Call of the Hive of Scape and Run: Parasites, looked up by id (SRP is optional). */
public final class Coth {
    public static final ResourceLocation ID = new ResourceLocation("srparasites", "coth");
    private static MobEffect effect;

    private Coth() {
    }

    /** SRP's COTH effect, null without SRP. */
    public static MobEffect effect() {
        if (effect == null) {
            effect = ForgeRegistries.MOB_EFFECTS.getValue(ID);
        }
        return effect;
    }

    /** 0 healthy, else the stage of the sickness: 1..3 (the level of the effect, which SRP raises by itself). */
    public static int stage(LivingEntity entity) {
        MobEffect coth = effect();
        MobEffectInstance instance = coth == null ? null : entity.getEffect(coth);
        return instance == null ? 0 : Math.min(3, instance.getAmplifier() + 1);
    }
}
