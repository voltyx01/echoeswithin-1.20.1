package com.voltyx.efw.coth.client;

import com.voltyx.efw.gore.json.BloodEntityConfig;
import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.ParticleBloodFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Blood of a fit of vomiting: the same blood particles a hit throws out of the body (the gore system: its texture, the drops sticking
 * to blocks, the stains and drips they leave), in the colour and style the blood config gives the one who vomits, only smaller drops.
 */
public final class VomitBlood {
    private static final Random RAND = new Random();
    /** The drops are smaller than the blood of a hit. */
    private static final float DROP_SCALE = 0.6f;

    private VomitBlood() {
    }

    /** One drop leaving the mouth of {@code source} (velocity in blocks per second). */
    public static void spawn(Entity source, Vec3 pos, float vx, float vy, float vz) {
        BloodEntityConfig.Values cfg = BloodEntityConfig.getValues(source);
        float min = Math.max(0.03f, cfg.scaleMin * DROP_SCALE);
        float max = Math.max(min, cfg.scaleMax * DROP_SCALE);
        double mx = vx / 20.0, my = vy / 20.0, mz = vz / 20.0;
        ParticleBlood p = ParticleBloodFactory.spawn(Minecraft.getInstance(), pos.x, pos.y, pos.z, mx, my, mz, RAND.nextInt(4), cfg.rgb,
                cfg.style.toArg(), cfg.amalgamation.toArg(), cfg.brightness.toArg(), Float.floatToRawIntBits(min),
                Float.floatToRawIntBits(max), cfg.lifeMin, cfg.lifeMax, Float.floatToRawIntBits(cfg.dripChance),
                Float.floatToRawIntBits(cfg.viscosity));
        if (p != null) {
            p.setExactMotion(mx, my, mz);
        }
    }
}
