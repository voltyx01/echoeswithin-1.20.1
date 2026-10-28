package com.voltyx.efw.gore.particle.common.geometry;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.amalgamation.BloodAmalgamationGround;
import com.voltyx.efw.gore.particle.state.BloodHeavy;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.core.Vec3i;
public final class BloodGeometryGround {
    private static final int MAX_STAMPS = 8;
    private static final double SPREAD_MIN = 0.02;
    private static final double SPREAD_MAX = 0.16;

    @Nonnull
    public static List<Util.Vertex> buildQuad(double cx, double cy, double cz, float particleScale, float groundRot, @Nonnull TextureAtlasSprite sprite) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float s = 0.1f * particleScale;
        float cos = Mth.cos((float)groundRot);
        float sin = Mth.sin((float)groundRot);
        double ux = (double)cos * (double)s;
        double uz = (double)(-sin) * (double)s;
        double vx = (double)sin * (double)s;
        double vz = (double)cos * (double)s;
        ArrayList<Util.Vertex> poly = new ArrayList<Util.Vertex>(4);
        poly.add(new Util.Vertex(cx - ux - vx, cy, cz - uz - vz, u1, v1));
        poly.add(new Util.Vertex(cx - ux + vx, cy, cz - uz + vz, u1, v0));
        poly.add(new Util.Vertex(cx + ux + vx, cy, cz + uz + vz, u0, v0));
        poly.add(new Util.Vertex(cx + ux - vx, cy, cz + uz - vz, u0, v1));
        return poly;
    }

    @Nonnull
    public static List<List<Util.Vertex>> buildRootPolys(@Nonnull ParticleBlood p, @Nonnull Direction face, @Nonnull TextureAtlasSprite sprite) {
        ArrayList<List<Util.Vertex>> out = new ArrayList<List<Util.Vertex>>(1);
        if (face != Direction.UP || !BloodAmalgamationGround.enabled() || BloodHeavy.isHeavy(p)) {
            out.add(BloodGeometryGround.buildQuad(p.posX, p.posY, p.posZ, p.getScale(), p.groundRot, sprite));
            return out;
        }
        float mass = p.amalgamVisualMass;
        if (mass <= 1.05f) {
            out.add(BloodGeometryGround.buildQuad(p.posX, p.posY, p.posZ, p.getScale(), p.groundRot, sprite));
            return out;
        }
        float blob = (float)Math.sqrt(Math.max(0.0f, mass - 1.0f));
        float stampsF = 1.0f + blob * 1.8f;
        int full = Mth.floor((float)stampsF);
        float frac = stampsF - (float)full;
        if (full < 1) {
            full = 1;
        }
        if (full > 8) {
            full = 8;
        }
        boolean partial = full < 8 && frac > 0.02f;
        int total = full + (partial ? 1 : 0);
        double spread = 0.035 + 0.025 * (double)blob;
        spread = Util.clamp(spread, 0.02, 0.16);
        float baseScale = p.getScale() * 0.92f;
        long seed = 0L;
        if (p.stuckPos != null) {
            seed = Mth.getSeed((Vec3i)p.stuckPos);
        }
        seed ^= (long)Float.floatToIntBits(p.groundRot) * 2654435769L;
        seed ^= (long)Float.floatToIntBits(p.dripSeed) * 2246822507L;
        out.add(BloodGeometryGround.buildQuad(p.posX, p.posY, p.posZ, baseScale * 1.05f, p.groundRot, sprite));
        for (int i = 1; i < total; ++i) {
            long h = BloodGeometryGround.mix64(seed + (long)i * -7046029254386353131L);
            float a01 = BloodGeometryGround.u01(h, 40);
            float b01 = BloodGeometryGround.u01(h, 16);
            float c01 = BloodGeometryGround.u01(h, 0);
            double ang = (double)a01 * (Math.PI * 2);
            float shell = (float)(i - 1) / (float)Math.max(1, 7);
            double r = spread * (0.3 + 0.7 * Math.sqrt(shell)) * (0.75 + 0.25 * (double)b01);
            double dx = Math.cos(ang) * r;
            double dz = Math.sin(ang) * r;
            float rot = p.groundRot + (a01 - 0.5f) * 2.1f + (c01 - 0.5f) * 0.45f;
            float sc = baseScale * (0.85f + 0.25f * c01) * (1.0f - 0.18f * shell);
            if (partial && i == total - 1) {
                float f = Util.clamp01(frac);
                f = Util.smoothstep01(f);
                sc *= 0.35f + 0.65f * f;
            }
            out.add(BloodGeometryGround.buildQuad(p.posX + dx, p.posY, p.posZ + dz, sc, rot, sprite));
        }
        return out;
    }

    private static long mix64(long z) {
        z = (z ^ z >>> 33) * -49064778989728563L;
        z = (z ^ z >>> 33) * -4265267296055464877L;
        return z ^ z >>> 33;
    }

    private static float u01(long z, int shift) {
        long v = z >>> shift & 0xFFFFFFL;
        return (float)v / 1.6777216E7f;
    }
}

