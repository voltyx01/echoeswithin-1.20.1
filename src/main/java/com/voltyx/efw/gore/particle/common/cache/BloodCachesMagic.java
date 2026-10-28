package com.voltyx.efw.gore.particle.common.cache;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.state.BloodMagic;
import java.util.WeakHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
public class BloodCachesMagic {
    private static final WeakHashMap<ParticleBlood, BloodMagic.MagicRt> RT = new WeakHashMap();

    @Nullable
    public static BloodMagic.MagicRt get(@Nullable ParticleBlood p) {
        return p != null ? RT.get((Object)p) : null;
    }

    public static void put(@Nonnull ParticleBlood p, @Nonnull BloodMagic.MagicRt rt) {
        RT.put(p, rt);
    }
}

