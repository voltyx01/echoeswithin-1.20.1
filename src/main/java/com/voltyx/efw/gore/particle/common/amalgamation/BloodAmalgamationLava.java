package com.voltyx.efw.gore.particle.common.amalgamation;

import com.voltyx.efw.gore.particle.common.amalgamation.BloodAmalgamationLiquid;
import com.voltyx.efw.gore.particle.state.liquid.lava.BloodLava;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
public class BloodAmalgamationLava {
    private static final Map<Long, BloodLava> CACHE = new HashMap<Long, BloodLava>(256);

    public static void tryMerge(@Nullable BloodLava p) {
        long self;
        BloodLava prev;
        float give;
        if (p == null || !p.isAlive()) {
            return;
        }
        if (!BloodAmalgamationLiquid.allow(p.getAmalgamationPolicy(), p.getFluidWeight())) {
            return;
        }
        if (!p.isOnSurface()) {
            return;
        }
        if (p.isAmalgMergingOut()) {
            return;
        }
        if (p.getBoilVisual01() > 0.001f) {
            return;
        }
        if (!BloodAmalgamationLiquid.mature(p.getAge(), p.getSurfaceGrowStartAge(), 3.0f)) {
            return;
        }
        Level w = p.getParticleWorld();
        if (w == null) {
            return;
        }
        BloodAmalgamationLiquid.beginTick(w);
        float cell = Math.max(0.05f, 0.25f);
        int cx = Mth.floor((double)(p.getPosX() / (double)cell));
        int cz = Mth.floor((double)(p.getPosZ() / (double)cell));
        int layer = p.getCache() != null && p.getCache().getCachedTop() != null ? ((net.minecraft.core.Vec3i) p.getCache().getCachedTop()).getY() : Mth.floor((double)p.getPosY());
        double areaP = BloodAmalgamationLiquid.area(p.getSurfaceScale(), p.getAmalgMul());
        BloodLava best = null;
        double bestD2 = Double.POSITIVE_INFINITY;
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                double r;
                double d2;
                double areaQ;
                long key = BloodAmalgamationLiquid.key(layer, cx + dx, cz + dz);
                BloodLava q = CACHE.get(key);
                if (q == null) continue;
                if (!q.isAlive() || !q.isOnSurface() || q.isAmalgMergingOut()) {
                    CACHE.remove(key);
                    continue;
                }
                if (!BloodAmalgamationLiquid.allow(q.getAmalgamationPolicy(), q.getFluidWeight())) {
                    CACHE.remove(key);
                    continue;
                }
                if (q.getBoilVisual01() > 0.001f || !BloodAmalgamationLiquid.mature(q.getAge(), q.getSurfaceGrowStartAge(), 3.0f) || !q.canAcceptAmalgIn() || (areaQ = BloodAmalgamationLiquid.area(q.getSurfaceScale(), q.getAmalgMul())) < areaP * (double)1.1f || !((d2 = BloodAmalgamationLiquid.dist2(p.getPosX(), p.getPosZ(), q.getPosX(), q.getPosZ())) <= (r = BloodAmalgamationLiquid.range(p.getSurfaceScale(), p.getAmalgMul(), q.getSurfaceScale(), q.getAmalgMul(), 0.45f)) * r) || !(d2 < bestD2)) continue;
                bestD2 = d2;
                best = q;
            }
        }
        if (best != null && (give = BloodAmalgamationLiquid.give(best.getSurfaceScale(), best.getAmalgTargetMul(), p.getSurfaceScale(), p.getAmalgMul())) > 1.0E-6f) {
            p.startAmalgMergeOut(best, give);
            return;
        }
        if (p.canAcceptAmalgIn() && ((prev = CACHE.get(self = BloodAmalgamationLiquid.key(layer, cx, cz))) == null || !prev.isAlive() || BloodAmalgamationLiquid.area(prev.getSurfaceScale(), prev.getAmalgMul()) < areaP)) {
            CACHE.put(self, p);
        }
    }

    static void clearCache() {
        CACHE.clear();
    }
}

