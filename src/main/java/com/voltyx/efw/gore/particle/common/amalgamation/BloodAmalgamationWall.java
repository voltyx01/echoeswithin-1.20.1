package com.voltyx.efw.gore.particle.common.amalgamation;

import com.voltyx.efw.gore.ModConfigurationClient;
import com.voltyx.efw.gore.json.BloodAmalgamationPolicy;
import com.voltyx.efw.gore.json.BloodStyle;
import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.BloodTuning;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.amalgamation.BloodAmalgamation;
import com.voltyx.efw.gore.particle.common.cache.BloodCachesParticle;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceAttach;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
public class BloodAmalgamationWall {
    static final double CELL_P = 0.25;
    static final double CELL_Q = 0.25;
    static final int TRIGGER = 2;
    public static final float MAX_MASS = 12.0f;
    public static final float MAX_DRIP = 2.25f;
    public static final float DRIP_EASE = 0.22f;
    public static final float MASS_EASE = 0.22f;
    public static final int ANIM_TICKS = 10;
    static final int CONSUME_TICKS = 14;
    public static final int EXTRA_LIFE_TICKS = 10;
    public static final int PULSE_TICKS = 6;
    public static final float PULSE_MAX = 0.08f;
    static final float MIN_ALPHA = 0.15f;
    private static final double INV_P = 4.0;
    private static final double INV_Q = 4.0;
    private static final double TAKEOVER_EPS = 0.02;
    private static Level world = null;
    private static long tick = Long.MIN_VALUE;
    private static final Map<Key, Bucket> CACHE = new HashMap<Key, Bucket>(256);
    private static final Key LOOKUP = new Key();

    public static boolean enabled() {
        try {
            return ModConfigurationClient.ground.amalgamation;
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static boolean allow(@Nullable BloodAmalgamationPolicy policy, @Nullable BloodStyle style) {
        return BloodAmalgamation.allowDecal(policy, style);
    }

    public static boolean tryMerge(ParticleBlood p, Vec3 onPlane) {
        if (!BloodAmalgamationWall.enabled()) {
            return false;
        }
        if (!BloodAmalgamationWall.eligible(p, onPlane)) {
            return false;
        }
        Level w = p.getParticleWorld();
        if (w == null || p.stuckFace == null) {
            return false;
        }
        BloodAmalgamationWall.beginTick(w);
        Direction face = p.stuckFace;
        int dim = BloodAmalgamation.safeDim(w);
        int planeQ = (int)Math.round(Util.planeCoord(face, onPlane) * 16.0);
        int cellP = Mth.floor((double)(Util.localP(face, onPlane) * 4.0));
        int cellQ = Mth.floor((double)(Util.localQ(face, onPlane) * 4.0));
        TextureAtlasSprite sprite = p.getSprite();
        if (sprite == null) {
            return false;
        }
        LOOKUP.set(dim, face, planeQ, cellP, cellQ, sprite);
        Bucket bucket = CACHE.get(LOOKUP);
        if (bucket == null) {
            if (CACHE.size() >= 4096) {
                CACHE.clear();
            }
            bucket = new Bucket();
            bucket.reset(tick);
            CACHE.put(new Key(dim, face, planeQ, cellP, cellQ, sprite), bucket);
        } else if (bucket.tick != tick) {
            bucket.reset(tick);
        }
        if (!bucket.merge && p.amalgamMass > 1.0001f) {
            bucket.lead = p;
            bucket.merge = true;
            for (ParticleBlood other : bucket.wait) {
                if (other == null || other == p || !BloodAmalgamationWall.canMerge(p, other)) continue;
                BloodAmalgamationWall.merge(p, other);
            }
            bucket.wait.clear();
            return false;
        }
        if (!bucket.merge) {
            bucket.wait.add(p);
            if (bucket.wait.size() < 2) {
                return false;
            }
            ParticleBlood lead = BloodAmalgamationWall.pickLead(bucket.wait);
            if (lead == null) {
                return false;
            }
            bucket.lead = lead;
            bucket.merge = true;
            boolean used = false;
            for (ParticleBlood other : bucket.wait) {
                if (other == null || other == lead || !BloodAmalgamationWall.canMerge(lead, other)) continue;
                BloodAmalgamationWall.merge(lead, other);
                if (other != p) continue;
                used = true;
            }
            bucket.wait.clear();
            return used;
        }
        ParticleBlood lead = bucket.lead;
        if (!BloodAmalgamationWall.leadOk(lead)) {
            bucket.reset(tick);
            bucket.wait.add(p);
            return false;
        }
        if (p == lead) {
            return false;
        }
        if (!BloodAmalgamationWall.canMerge(lead, p)) {
            return false;
        }
        if (BloodAmalgamationWall.shouldLead(p, lead)) {
            BloodAmalgamationWall.merge(p, lead);
            bucket.lead = p;
            return false;
        }
        BloodAmalgamationWall.merge(lead, p);
        return true;
    }

    private static boolean canMerge(ParticleBlood a, ParticleBlood b) {
        if (a == null || b == null) {
            return false;
        }
        if (a == b) {
            return false;
        }
        if (!BloodAmalgamationWall.leadOk(a) || !BloodAmalgamationWall.leadOk(b)) {
            return false;
        }
        if (a.stuckFace == null || a.stuckFace != b.stuckFace) {
            return false;
        }
        if (!BloodTuning.isWallFace(a.stuckFace)) {
            return false;
        }
        Vec3 ap = BloodSurfaceAttach.anchorPoint(a);
        Vec3 bp = BloodSurfaceAttach.anchorPoint(b);
        if (ap == null || bp == null) {
            return false;
        }
        double laneGap = Math.abs(BloodAmalgamationWall.wallLaneCoord(a.stuckFace, ap) - BloodAmalgamationWall.wallLaneCoord(b.stuckFace, bp));
        double verticalGap = Math.abs(ap.y - bp.y);
        double maxLaneGap = 0.12 + 0.05 * (double)Math.max(a.getScale(), b.getScale());
        double maxVerticalGap = 0.16 + 0.08 * (double)Math.max(a.getScale(), b.getScale()) + 0.1 * (double)Math.min(1.0f, Math.max(Math.max(0.0f, a.dripAmount), Math.max(0.0f, b.dripAmount)));
        if (maxLaneGap > 0.22) {
            maxLaneGap = 0.22;
        }
        if (maxVerticalGap > 0.34) {
            maxVerticalGap = 0.34;
        }
        return laneGap <= maxLaneGap && verticalGap <= maxVerticalGap;
    }

    private static double leadScore(ParticleBlood p) {
        float drip = Math.max(0.0f, p.dripAmount);
        float mass = Math.max(1.0f, p.amalgamMass);
        float scale = Math.max(0.001f, p.getScale());
        return (double)drip * 5.0 + (double)mass * 2.5 + (double)scale * 0.75 + (double)p.getAge() * 0.03;
    }

    private static double wallLaneCoord(Direction face, Vec3 p) {
        return face == Direction.EAST || face == Direction.WEST ? p.z : p.x;
    }

    public static float dripMul(float visualMass) {
        float mass = Math.max(1.0f, visualMass);
        float blob = (float)Math.sqrt(Math.max(0.0f, mass - 1.0f));
        float mul = 1.0f + 0.28f * blob;
        if (mul < 1.0f) {
            mul = 1.0f;
        }
        if (mul > 2.25f) {
            mul = 2.25f;
        }
        return mul;
    }

    private static void merge(ParticleBlood lead, ParticleBlood other) {
        boolean dripChanged;
        float weightedDrip;
        float otherDrip;
        if (lead == null || other == null) {
            return;
        }
        if (lead == other) {
            return;
        }
        if (!BloodAmalgamationWall.canMerge(lead, other)) {
            return;
        }
        if (other.isAmalgamConsuming()) {
            return;
        }
        if (other.stuckStartAge >= 0 && (lead.stuckStartAge < 0 || other.stuckStartAge < lead.stuckStartAge)) {
            lead.stuckStartAge = other.stuckStartAge;
        }
        float leadMassBefore = Math.max(1.0f, lead.amalgamMass);
        float otherMass = Math.max(1.0f, other.amalgamMass);
        float totalMass = leadMassBefore + otherMass;
        float leadDrip = Math.max(0.0f, lead.dripAmount);
        float longestDrip = Math.max(leadDrip, otherDrip = Math.max(0.0f, other.dripAmount));
        float transferredDrip = Math.min(2.25f, Math.max(longestDrip * 0.92f, weightedDrip = (leadDrip * leadMassBefore + otherDrip * otherMass) / Math.max(1.0f, totalMass)));
        boolean bl = dripChanged = transferredDrip > lead.dripAmount + 1.0E-5f;
        if (dripChanged) {
            lead.dripAmount = transferredDrip;
            lead.amalgamAnimTicks = Math.max(lead.amalgamAnimTicks, 10);
        }
        float beforeMass = lead.amalgamMass;
        lead.addAmalgamMass(otherMass);
        if (dripChanged && lead.amalgamMass <= beforeMass + 1.0E-5f) {
            BloodCachesParticle.invalidateShape(lead);
            BloodCachesParticle.invalidateView(lead);
        }
        other.startAmalgamConsume(14);
    }

    private static ParticleBlood pickLead(List<ParticleBlood> list) {
        ParticleBlood best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (ParticleBlood p : list) {
            if (!BloodAmalgamationWall.leadOk(p)) continue;
            int nearby = 0;
            float nearbyMass = 0.0f;
            for (ParticleBlood other : list) {
                if (other == null || other == p || !BloodAmalgamationWall.leadOk(other) || !BloodAmalgamationWall.canMerge(p, other)) continue;
                ++nearby;
                nearbyMass += Math.max(1.0f, other.amalgamMass);
            }
            if (nearby <= 0) continue;
            double score = BloodAmalgamationWall.leadScore(p) + (double)nearby * 2.0 + (double)nearbyMass * 0.5;
            if (best != null && !(score > bestScore + 1.0E-6) && (!(Math.abs(score - bestScore) <= 1.0E-6) || p.getAge() <= best.getAge())) continue;
            best = p;
            bestScore = score;
        }
        return best;
    }

    private static boolean shouldLead(ParticleBlood cand, ParticleBlood lead) {
        if (!BloodAmalgamationWall.leadOk(cand) || !BloodAmalgamationWall.leadOk(lead)) {
            return false;
        }
        if (!BloodAmalgamationWall.canMerge(cand, lead)) {
            return false;
        }
        float candDrip = Math.max(0.0f, cand.dripAmount);
        float leadDrip = Math.max(0.0f, lead.dripAmount);
        float candMass = Math.max(1.0f, cand.amalgamMass);
        float leadMass = Math.max(1.0f, lead.amalgamMass);
        if (candDrip < leadDrip + 0.2f) {
            return false;
        }
        if (candMass < leadMass + 0.75f) {
            return false;
        }
        return cand.getAge() >= lead.getAge() + 8;
    }

    private static double axis(ParticleBlood p) {
        if (p == null || p.stuckFace == null) {
            return Double.NEGATIVE_INFINITY;
        }
        Vec3 onPlane = BloodSurfaceAttach.anchorPoint(p);
        if (onPlane != null) {
            return Util.localQ(p.stuckFace, onPlane);
        }
        return p.posY;
    }

    private static boolean eligible(ParticleBlood p, Vec3 onPlane) {
        if (p == null || onPlane == null) {
            return false;
        }
        if (p.isExpiredSafe()) {
            return false;
        }
        if (!p.isStuck || !BloodTuning.isWallFace(p.stuckFace)) {
            return false;
        }
        if (p.fallingDripActive) {
            return false;
        }
        if (p.isAmalgamConsuming()) {
            return false;
        }
        if (p.cache.fade.waterStartAge >= 0) {
            return false;
        }
        if (p.cache.fade.modelStartAge >= 0) {
            return false;
        }
        if (!BloodAmalgamationWall.allow(p.getAmalgamationPolicy(), p.fluidWeight)) {
            return false;
        }
        return p.getAlpha() >= 0.15f && p.getSprite() != null;
    }

    private static boolean leadOk(ParticleBlood p) {
        if (p == null) {
            return false;
        }
        if (p.isExpiredSafe()) {
            return false;
        }
        if (!p.isStuck || !BloodTuning.isWallFace(p.stuckFace)) {
            return false;
        }
        if (p.fallingDripActive) {
            return false;
        }
        if (p.isAmalgamConsuming()) {
            return false;
        }
        if (p.cache.fade.waterStartAge >= 0) {
            return false;
        }
        if (p.cache.fade.modelStartAge >= 0) {
            return false;
        }
        if (!BloodAmalgamationWall.allow(p.getAmalgamationPolicy(), p.fluidWeight)) {
            return false;
        }
        return p.getAlpha() >= 0.15f && p.getSprite() != null;
    }

    private static void beginTick(Level w) {
        long now;
        if (w != world) {
            world = w;
            tick = Long.MIN_VALUE;
            CACHE.clear();
        }
        if ((now = BloodAmalgamation.safeTick(w)) != tick) {
            tick = now;
        }
    }

    private static final class Bucket {
        long tick = Long.MIN_VALUE;
        final ArrayList<ParticleBlood> wait = new ArrayList(2);
        ParticleBlood lead = null;
        boolean merge = false;

        private Bucket() {
        }

        void reset(long t) {
            this.tick = t;
            this.wait.clear();
            this.lead = null;
            this.merge = false;
        }
    }

    private static final class Key {
        int dim;
        Direction face;
        int planeQ;
        int cellP;
        int cellQ;
        TextureAtlasSprite sprite;

        Key() {
        }

        Key(int dim, Direction face, int planeQ, int cellP, int cellQ, TextureAtlasSprite sprite) {
            this.set(dim, face, planeQ, cellP, cellQ, sprite);
        }

        Key set(int dim, Direction face, int planeQ, int cellP, int cellQ, TextureAtlasSprite sprite) {
            this.dim = dim;
            this.face = face;
            this.planeQ = planeQ;
            this.cellP = cellP;
            this.cellQ = cellQ;
            this.sprite = sprite;
            return this;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }
            Key k = (Key)o;
            return this.dim == k.dim && this.face == k.face && this.planeQ == k.planeQ && this.cellP == k.cellP && this.cellQ == k.cellQ && this.sprite == k.sprite;
        }

        public int hashCode() {
            int h = 1;
            h = 31 * h + this.dim;
            h = 31 * h + (this.face != null ? this.face.ordinal() : -1);
            h = 31 * h + this.planeQ;
            h = 31 * h + this.cellP;
            h = 31 * h + this.cellQ;
            h = 31 * h + System.identityHashCode(this.sprite);
            return h;
        }
    }
}

