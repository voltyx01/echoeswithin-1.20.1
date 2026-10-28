package com.voltyx.efw.gore.particle.common.amalgamation;

import com.voltyx.efw.gore.ModConfigurationClient;
import com.voltyx.efw.gore.json.BloodAmalgamationPolicy;
import com.voltyx.efw.gore.json.BloodStyle;
import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.amalgamation.BloodAmalgamation;
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
public class BloodAmalgamationGround {
    static final double CELL = 0.25;
    static final int TRIGGER = 4;
    public static final float MAX_MASS = 20.0f;
    public static final float MAX_SCALE = 1.75f;
    public static final float SCALE_EASE = 0.22f;
    public static final float MASS_EASE = 0.22f;
    public static final int ANIM_TICKS = 12;
    static final int CONSUME_TICKS = 10;
    public static final int EXTRA_LIFE_TICKS = 12;
    static final float MIN_ALPHA = 0.15f;
    public static final int PULSE_TICKS = 6;
    public static final float PULSE_MAX = 0.07f;
    private static final double INV_CELL = 4.0;
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
        if (!BloodAmalgamationGround.enabled()) {
            return false;
        }
        if (!BloodAmalgamationGround.eligible(p, onPlane)) {
            return false;
        }
        Level w = p.getParticleWorld();
        if (w == null) {
            return false;
        }
        BloodAmalgamationGround.beginTick(w);
        int dim = BloodAmalgamation.safeDim(w);
        int planeQ = (int)Math.round(onPlane.y * 16.0);
        int cellX = Mth.floor((double)(onPlane.x * 4.0));
        int cellZ = Mth.floor((double)(onPlane.z * 4.0));
        TextureAtlasSprite sprite = p.getSprite();
        if (sprite == null) {
            return false;
        }
        LOOKUP.set(dim, planeQ, cellX, cellZ, sprite);
        Bucket bucket = CACHE.get(LOOKUP);
        if (bucket == null) {
            if (CACHE.size() >= 4096) {
                CACHE.clear();
            }
            bucket = new Bucket();
            bucket.reset(tick);
            CACHE.put(new Key(dim, planeQ, cellX, cellZ, sprite), bucket);
        } else if (bucket.tick != tick) {
            bucket.reset(tick);
        }
        if (!bucket.merge && p.amalgamMass > 1.0001f) {
            bucket.lead = p;
            bucket.merge = true;
            for (ParticleBlood other : bucket.wait) {
                if (other == null || other == p) continue;
                BloodAmalgamationGround.merge(p, other);
            }
            bucket.wait.clear();
            return false;
        }
        if (!bucket.merge) {
            bucket.wait.add(p);
            if (bucket.wait.size() < 4) {
                return false;
            }
            ParticleBlood lead = BloodAmalgamationGround.pickLead(bucket.wait);
            if (lead == null) {
                bucket.wait.clear();
                return false;
            }
            bucket.lead = lead;
            bucket.merge = true;
            boolean used = false;
            for (ParticleBlood other : bucket.wait) {
                if (other == null || other == lead) continue;
                BloodAmalgamationGround.merge(lead, other);
                if (other != p) continue;
                used = true;
            }
            bucket.wait.clear();
            return used;
        }
        ParticleBlood lead = bucket.lead;
        if (!BloodAmalgamationGround.leadOk(lead)) {
            bucket.reset(tick);
            bucket.wait.add(p);
            return false;
        }
        if (p == lead) {
            return false;
        }
        if (BloodAmalgamationGround.leadOk(p) && p.amalgamMass > lead.amalgamMass + 0.25f) {
            BloodAmalgamationGround.merge(p, lead);
            bucket.lead = p;
            return false;
        }
        BloodAmalgamationGround.merge(lead, p);
        return true;
    }

    public static float scaleMul(float visualMass) {
        float mass = Math.max(1.0f, visualMass);
        float blob = (float)Math.sqrt(Math.max(0.0f, mass - 1.0f));
        float mul = 1.0f + 0.18f * blob;
        if (mul < 1.0f) {
            mul = 1.0f;
        }
        if (mul > 1.75f) {
            mul = 1.75f;
        }
        return mul;
    }

    private static void merge(ParticleBlood lead, ParticleBlood other) {
        if (lead == null || other == null) {
            return;
        }
        if (lead == other) {
            return;
        }
        if (lead.isExpiredSafe() || other.isExpiredSafe()) {
            return;
        }
        if (!lead.isGroundTop() || !other.isGroundTop()) {
            return;
        }
        if (!BloodAmalgamationGround.allow(lead.getAmalgamationPolicy(), lead.fluidWeight)) {
            return;
        }
        if (!BloodAmalgamationGround.allow(other.getAmalgamationPolicy(), other.fluidWeight)) {
            return;
        }
        if (other.isAmalgamConsuming()) {
            return;
        }
        float add = Math.max(1.0f, other.amalgamMass);
        float before = lead.amalgamMass;
        lead.addAmalgamMass(add);
        if (lead.amalgamMass <= before + 1.0E-5f) {
            lead.amalgamLastMergeAge = lead.getAge();
        }
        other.startAmalgamConsume(10);
    }

    private static ParticleBlood pickLead(List<ParticleBlood> list) {
        ParticleBlood best = null;
        float bestMass = -1.0f;
        int bestAge = -1;
        float bestScale = -1.0f;
        for (ParticleBlood p : list) {
            if (!BloodAmalgamationGround.leadOk(p)) continue;
            float m = p.amalgamMass;
            int age = p.getAge();
            float s = p.getScale();
            if (!(best == null || m > bestMass + 1.0E-6f || m >= bestMass - 1.0E-6f && age > bestAge) && (!(m >= bestMass - 1.0E-6f) || age != bestAge || !(s > bestScale))) continue;
            best = p;
            bestMass = m;
            bestAge = age;
            bestScale = s;
        }
        return best;
    }

    private static boolean eligible(ParticleBlood p, Vec3 onPlane) {
        if (p == null || onPlane == null) {
            return false;
        }
        if (p.isExpiredSafe()) {
            return false;
        }
        if (!p.isStuck || p.stuckFace != Direction.UP) {
            return false;
        }
        if (p.fallingDripActive) {
            return false;
        }
        if (p.isAmalgamConsuming()) {
            return false;
        }
        if (!BloodAmalgamationGround.allow(p.getAmalgamationPolicy(), p.fluidWeight)) {
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
        if (!p.isGroundTop()) {
            return false;
        }
        if (p.isAmalgamConsuming()) {
            return false;
        }
        if (!BloodAmalgamationGround.allow(p.getAmalgamationPolicy(), p.fluidWeight)) {
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
        final ArrayList<ParticleBlood> wait = new ArrayList(4);
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
        int planeQ;
        int cellX;
        int cellZ;
        TextureAtlasSprite sprite;

        Key() {
        }

        Key(int dim, int planeQ, int cellX, int cellZ, TextureAtlasSprite sprite) {
            this.set(dim, planeQ, cellX, cellZ, sprite);
        }

        Key set(int dim, int planeQ, int cellX, int cellZ, TextureAtlasSprite sprite) {
            this.dim = dim;
            this.planeQ = planeQ;
            this.cellX = cellX;
            this.cellZ = cellZ;
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
            return this.dim == k.dim && this.planeQ == k.planeQ && this.cellX == k.cellX && this.cellZ == k.cellZ && this.sprite == k.sprite;
        }

        public int hashCode() {
            int h = 1;
            h = 31 * h + this.dim;
            h = 31 * h + this.planeQ;
            h = 31 * h + this.cellX;
            h = 31 * h + this.cellZ;
            h = 31 * h + System.identityHashCode(this.sprite);
            return h;
        }
    }
}

