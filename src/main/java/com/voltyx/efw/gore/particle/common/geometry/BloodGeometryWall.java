package com.voltyx.efw.gore.particle.common.geometry;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.BloodTuning;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceAttach;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
public final class BloodGeometryWall {
    @Nonnull
    public static List<Util.Vertex> buildQuad(double cx, double cy, double cz, float particleScale, boolean flipU, @Nonnull TextureAtlasSprite sprite, @Nonnull Direction face, float dripAmount) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float s = 0.1f * particleScale;
        double axisX = 0.0;
        double axisZ = 0.0;
        if (face == Direction.NORTH || face == Direction.SOUTH) {
            axisX = 1.0;
        } else {
            axisZ = 1.0;
        }
        double rX = axisX * (double)s;
        double rZ = axisZ * (double)s;
        double rY = s;
        float uL = flipU ? u0 : u1;
        float uR = flipU ? u1 : u0;
        List<Util.Vertex> poly = new ArrayList<Util.Vertex>(4);
        poly.add(new Util.Vertex(cx - rX, cy - rY, cz - rZ, uL, v1));
        poly.add(new Util.Vertex(cx - rX, cy + rY, cz - rZ, uL, v0));
        poly.add(new Util.Vertex(cx + rX, cy + rY, cz + rZ, uR, v0));
        poly.add(new Util.Vertex(cx + rX, cy - rY, cz + rZ, uR, v1));
        if (dripAmount > 0.0f) {
            poly = BloodGeometryWall.stretch(poly, dripAmount);
        }
        return poly;
    }

    @Nonnull
    public static List<Util.Vertex> buildRenderQuad(@Nonnull ParticleBlood p, @Nonnull Direction face, @Nonnull TextureAtlasSprite sprite) {
        return BloodGeometryWall.buildQuad(p.posX, p.posY, p.posZ, p.getScale(), p.flipU, sprite, face, p.dripAmount);
    }

    public static void accumulateSupport(@Nonnull ParticleBlood p, @Nonnull Direction face, @Nonnull Util.FaceRect origRect, @Nonnull Util.FaceRect clipRect, @Nonnull List<Util.Vertex> clippedPoly, @Nonnull ParticleBlood.StickMode mode, @Nonnull BlockPos bp) {
        if (mode != ParticleBlood.StickMode.MODEL) {
            return;
        }
        if (!BloodTuning.isWallFace(face)) {
            return;
        }
        if (!bp.equals((Object)p.stuckPos)) {
            return;
        }
        if (clipRect.qMin >= origRect.qMin - 1.0E-6) {
            return;
        }
        double minQ = Double.POSITIVE_INFINITY;
        double maxQ = Double.NEGATIVE_INFINITY;
        for (Util.Vertex v : clippedPoly) {
            double q = Util.localQ(face, v);
            if (q < minQ) {
                minQ = q;
            }
            if (!(q > maxQ)) continue;
            maxQ = q;
        }
        double totalH = Math.max(1.0E-6, maxQ - minQ);
        double supportedMin = Math.max(minQ, origRect.qMin);
        double supportedH = maxQ - supportedMin;
        if (supportedH < 0.0) {
            supportedH = 0.0;
        }
        float supportFrac = (float)Util.clamp01(supportedH / totalH);
        p.cache.support.frac = Math.min(p.cache.support.frac, supportFrac);
        if (supportFrac < 0.999f) {
            p.cache.support.airBelow = true;
        }
    }

    @Nonnull
    public static Util.FaceRect buildLooseRect(@Nonnull ParticleBlood p, @Nonnull Direction face, @Nonnull Util.FaceRect r, @Nonnull ParticleBlood.StickMode mode, @Nonnull BlockPos bp, @Nullable Vec3 movingOffset) {
        double airQMin;
        if (mode != ParticleBlood.StickMode.MODEL) {
            return r;
        }
        if (!BloodTuning.isWallFace(face)) {
            return r;
        }
        if (!bp.equals((Object)p.stuckPos)) {
            return r;
        }
        if (p.dripAmount <= 1.0E-4f) {
            return r;
        }
        if (!BloodGeometryWall.isAnchorInsideRect(p, face, r)) {
            return r;
        }
        double qMin = r.qMin;
        double hostBottom = ((net.minecraft.core.Vec3i) bp).getY();
        if (movingOffset != null) {
            hostBottom += movingOffset.y;
        }
        if ((hostBottom -= 5.0E-4) < qMin) {
            qMin = hostBottom;
        }
        if (!Double.isNaN(airQMin = BloodGeometryWall.findAirQMin(p, face, r, bp, movingOffset)) && airQMin < qMin) {
            qMin = airQMin;
        }
        if (qMin >= r.qMin) {
            return r;
        }
        return new Util.FaceRect(r.plane, r.pMin, r.pMax, qMin, r.qMax);
    }

    @Nullable
    private static List<Util.Vertex> stretch(@Nullable List<Util.Vertex> poly, float dripAmount) {
        if (poly == null || poly.size() < 3 || dripAmount <= 0.0f) {
            return poly;
        }
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Util.Vertex v : poly) {
            if (v.y < minY) {
                minY = v.y;
            }
            if (!(v.y > maxY)) continue;
            maxY = v.y;
        }
        double h = maxY - minY;
        if (h < 1.0E-6) {
            return poly;
        }
        ArrayList<Util.Vertex> out = new ArrayList<Util.Vertex>(poly.size());
        for (Util.Vertex v : poly) {
            double t = (maxY - v.y) / h;
            out.add(new Util.Vertex(v.x, v.y - (double)dripAmount * t, v.z, v.u, v.v));
        }
        return out;
    }

    private static boolean isAnchorInsideRect(@Nonnull ParticleBlood p, @Nonnull Direction face, @Nonnull Util.FaceRect r) {
        Vec3 anchor = BloodSurfaceAttach.anchorPoint(p);
        if (anchor == null) {
            return false;
        }
        double planeA = Util.planeCoord(face, anchor);
        if (Math.abs(planeA - r.plane) > 0.01) {
            return false;
        }
        double ap = Util.localP(face, anchor);
        double aq = Util.localQ(face, anchor);
        double pad = 1.0E-4;
        return ap >= r.pMin - 1.0E-4 && ap <= r.pMax + 1.0E-4 && aq >= r.qMin - 1.0E-4 && aq <= r.qMax + 1.0E-4;
    }

    private static double findAirQMin(@Nonnull ParticleBlood p, @Nonnull Direction face, @Nonnull Util.FaceRect r, @Nonnull BlockPos bp, @Nullable Vec3 movingOffset) {
        Vec3 sample;
        BlockPos airPos;
        double hostBottom;
        if (movingOffset != null) {
            return Double.NaN;
        }
        Level w = p.getParticleWorld();
        if (w == null) {
            return Double.NaN;
        }
        double halfH = 0.1 * (double)p.getScale();
        double minQWanted = p.posY - halfH - (double)p.dripAmount;
        if (minQWanted >= (hostBottom = (double)((net.minecraft.core.Vec3i) bp).getY()) - 1.0E-6) {
            return Double.NaN;
        }
        int yMin = Mth.floor((double)(minQWanted + 1.0E-6));
        double pC = 0.5 * (r.pMin + r.pMax);
        double best = Double.NaN;
        for (int y = ((net.minecraft.core.Vec3i) bp).getY() - 1; y >= yMin && w.isLoaded(airPos = BlockPos.containing(((net.minecraft.core.Vec3i) bp).getX(), y, ((net.minecraft.core.Vec3i) bp).getZ())) && w.isEmptyBlock(airPos) && BloodSurfaceAttach.isExposed(p, airPos, face, sample = Util.pointFromPlanePQ(face, r.plane, pC, (double)y + 0.5)); --y) {
            best = (double)y - 5.0E-4;
        }
        return best;
    }
}

