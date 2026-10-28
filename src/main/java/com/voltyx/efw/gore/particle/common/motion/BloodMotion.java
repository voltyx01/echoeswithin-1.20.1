package com.voltyx.efw.gore.particle.common.motion;

import com.voltyx.efw.gore.particle.ParticleBlood;
import net.minecraft.util.RandomSource;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
public class BloodMotion {
    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    static int randBetween(RandomSource rand, int min, int max) {
        int lo = Math.min(min, max);
        int hi = Math.max(min, max);
        return lo + (hi > lo ? rand.nextInt(hi - lo + 1) : 0);
    }

    static int randomInclusive(RandomSource rand, int min, int max) {
        int lo = Math.min(min, max);
        int hi = Math.max(min, max);
        return lo + (hi > lo ? rand.nextInt(hi - lo + 1) : 0);
    }

    static void clampHorizontal(ParticleBlood p, double maxH) {
        double hs = p.motionX * p.motionX + p.motionZ * p.motionZ;
        double maxSq = maxH * maxH;
        if (hs > maxSq && hs > 1.0E-12) {
            double m = maxH / Math.sqrt(hs);
            p.motionX *= m;
            p.motionZ *= m;
        }
    }

    static double randRange(RandomSource rand, double min, double max) {
        if (max <= min) {
            return min;
        }
        return min + rand.nextDouble() * (max - min);
    }

    static double randSigned(RandomSource rand) {
        return rand.nextDouble() * 2.0 - 1.0;
    }

    public static Vec3 normalizeOr(Vec3 v, Vec3 fallback) {
        double lenSq;
        double d = lenSq = v != null ? v.lengthSqr() : 0.0;
        if (lenSq > 1.0E-12) {
            return v.scale(1.0 / Math.sqrt(lenSq));
        }
        double fbLenSq = fallback.lengthSqr();
        if (fbLenSq > 1.0E-12) {
            return fallback.scale(1.0 / Math.sqrt(fbLenSq));
        }
        return new Vec3(1.0, 0.0, 0.0);
    }

    static Vec3 collisionNormal(double reqX, double reqY, double reqZ, boolean colX, boolean colZ, boolean hitCeiling) {
        Vec3 n;
        double nx = 0.0;
        double ny = 0.0;
        double nz = 0.0;
        if (colX) {
            nx += reqX > 0.0 ? -1.0 : 1.0;
        }
        if (colZ) {
            nz += reqZ > 0.0 ? -1.0 : 1.0;
        }
        if (hitCeiling) {
            ny -= 1.0;
        }
        if ((n = new Vec3(nx, ny, nz)).lengthSqr() < 1.0E-12) {
            n = Math.abs(reqX) >= Math.abs(reqZ) ? new Vec3(reqX > 0.0 ? -1.0 : 1.0, 0.0, 0.0) : new Vec3(0.0, 0.0, reqZ > 0.0 ? -1.0 : 1.0);
        }
        return BloodMotion.normalizeOr(n, new Vec3(1.0, 0.0, 0.0));
    }

    static Vec3 wallTangent(Vec3 normal, Vec3 tangent, RandomSource rand) {
        Vec3 fallback;
        if (tangent != null && tangent.lengthSqr() > 1.0E-12) {
            return BloodMotion.normalizeOr(tangent, new Vec3(0.0, 1.0, 0.0));
        }
        Vec3 vec3d = fallback = Math.abs(normal.y) > 0.7 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(normal.z, 0.0, -normal.x);
        if (rand.nextBoolean()) {
            fallback = fallback.scale(-1.0);
        }
        return BloodMotion.normalizeOr(fallback, new Vec3(1.0, 0.0, 0.0));
    }

    static final class BlockContact {
        final BlockPos pos;
        final Direction face;
        final Vec3 hit;

        BlockContact(BlockPos pos, Direction face, Vec3 hit) {
            this.pos = pos;
            this.face = face;
            this.hit = hit;
        }
    }
}

