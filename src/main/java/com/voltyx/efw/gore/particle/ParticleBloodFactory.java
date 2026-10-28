package com.voltyx.efw.gore.particle;

import com.voltyx.efw.gore.json.BloodAmalgamationPolicy;
import com.voltyx.efw.gore.json.BloodBrightnessMode;
import com.voltyx.efw.gore.json.BloodEntityConfig;
import com.voltyx.efw.gore.json.BloodStyle;
import com.voltyx.efw.gore.particle.common.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Makes a blood particle from the packed arguments of a hit (the 1.12 particle factory, id 2000) and lets it fly. */
public final class ParticleBloodFactory {
    private ParticleBloodFactory() {
    }

    /** 1.12 effectRenderer.spawnEffectParticle(2000, ...): the particle, already added to the particle engine (null without a world). */
    @Nullable
    public static ParticleBlood spawn(@Nonnull Minecraft mc, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, int ... args) {
        if (mc.level == null || mc.particleEngine == null) {
            return null;
        }
        ParticleBlood p = create(mc.level, x, y, z, xSpeed, ySpeed, zSpeed, args);
        mc.particleEngine.add(p);
        return p;
    }

    public static ParticleBlood create(@Nonnull Level worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, int ... args) {
        int variant = args.length > 0 ? args[0] : worldIn.random.nextInt(4);
        int rgb = args.length > 1 ? args[1] : BloodEntityConfig.getDefaultRGB();
        int wArg = args.length > 2 ? args[2] : BloodEntityConfig.getDefaultStyle().toArg();
        int aArg = args.length > 3 ? args[3] : BloodEntityConfig.getDefaultAmalgamation().toArg();
        int bArg = args.length > 4 ? args[4] : BloodEntityConfig.getDefaultBrightness().toArg();
        variant = Math.floorMod(variant, 4);
        BloodStyle weight = BloodStyle.fromArg(wArg);
        BloodAmalgamationPolicy policy = BloodAmalgamationPolicy.fromArg(aArg);
        BloodBrightnessMode brightMode = BloodBrightnessMode.fromArg(bArg);
        float sMin = BloodEntityConfig.getDefaultScaleMin();
        float sMax = BloodEntityConfig.getDefaultScaleMax();
        if (args.length > 5) {
            sMin = Float.intBitsToFloat(args[5]);
        }
        if (args.length > 6) {
            sMax = Float.intBitsToFloat(args[6]);
        }
        Util.RangeF sr = BloodEntityConfig.sanitizeScaleRange(sMin, sMax);
        int lifeMin = BloodEntityConfig.getDefaultLifeMin();
        int lifeMax = BloodEntityConfig.getDefaultLifeMax();
        if (args.length > 7) {
            lifeMin = args[7];
        }
        if (args.length > 8) {
            lifeMax = args[8];
        }
        BloodEntityConfig.RangeI lr = BloodEntityConfig.sanitizeLifeRange(lifeMin, lifeMax);
        float dripChance01 = BloodEntityConfig.getDefaultDripChance();
        if (args.length > 9) {
            dripChance01 = Float.intBitsToFloat(args[9]);
        }
        dripChance01 = BloodEntityConfig.sanitizeDripChance(dripChance01, BloodEntityConfig.getDefaultDripChance());
        float viscosity01 = BloodEntityConfig.getDefaultViscosity();
        if (args.length > 10) {
            viscosity01 = Float.intBitsToFloat(args[10]);
        }
        viscosity01 = BloodEntityConfig.sanitizeViscosity(viscosity01, BloodEntityConfig.getDefaultViscosity());
        ParticleBlood p = new ParticleBlood(worldIn, x, y, z, xSpeed, ySpeed, zSpeed, variant, weight, sr.min, sr.max, lr.min, lr.max, viscosity01);
        p.setTintRGB(rgb);
        p.setAmalgamationPolicy(policy);
        p.setDripChance01(dripChance01);
        p.setBrightnessMode(brightMode);
        return p;
    }
}
