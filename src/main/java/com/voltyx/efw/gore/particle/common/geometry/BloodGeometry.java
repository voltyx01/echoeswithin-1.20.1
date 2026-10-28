package com.voltyx.efw.gore.particle.common.geometry;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.blocksupport.PistonSupport;
import com.voltyx.efw.gore.particle.common.BloodTuning;
import com.voltyx.efw.gore.particle.common.SurfaceLookup;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.cache.BloodCachesParticle;
import com.voltyx.efw.gore.particle.common.geometry.BloodGeometryCeiling;
import com.voltyx.efw.gore.particle.common.geometry.BloodGeometryGround;
import com.voltyx.efw.gore.particle.common.geometry.BloodGeometryWall;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceAttach;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
public final class BloodGeometry {
    private static final double PLANE_MATCH_EPS = 0.003;
    private static final int MAX_TILE_RADIUS = 4;
    private static final double WALL_SLIDE_REBUILD_DISTANCE = 0.01;

    public static boolean needsGeometryRebuild(@Nullable ParticleBlood p) {
        double epsSq;
        if (p == null) {
            return true;
        }
        if (Double.isNaN(p.cache.view.x) || Double.isNaN(p.cache.view.y) || Double.isNaN(p.cache.view.z)) {
            return true;
        }
        double dx = p.posX - p.cache.view.x;
        double dy = p.posY - p.cache.view.y;
        double dz = p.posZ - p.cache.view.z;
        double distSq = dx * dx + dy * dy + dz * dz;
        double eps = 0.0;
        if (p.isStuck && BloodTuning.isWallFace(p.stuckFace) && p.getParticleWorld() != null && p.stuckPos != null) {
            boolean moving;
            PistonSupport.MovingInfo mi = PistonSupport.getMovingInfo(p.getParticleWorld(), p.stuckPos, 1.0f, p);
            boolean bl = moving = mi != null && !mi.staticBaseNoOffset;
            if (!moving) {
                eps = 0.01;
            }
        }
        return distSq > (epsSq = Math.max(1.0E-12, eps * eps));
    }

    public static void cacheRenderPos(@Nonnull ParticleBlood p) {
        p.cache.view.x = p.posX;
        p.cache.view.y = p.posY;
        p.cache.view.z = p.posZ;
    }

    @Nullable
    public static List<List<Util.Vertex>> clipCustomDecalPolys(@Nonnull ParticleBlood p, @Nonnull List<Util.Vertex> poly) {
        if (poly.size() < 3) {
            return null;
        }
        if (!p.isStuck || p.stuckFace == null || p.stuckPos == null) {
            ArrayList<List<Util.Vertex>> out = new ArrayList<List<Util.Vertex>>(1);
            out.add(poly);
            return out;
        }
        PistonSupport.MovingInfo moving = PistonSupport.getMovingInfo(p.getParticleWorld(), p.stuckPos, 1.0f, p);
        if (moving != null && moving.movedState != null && !moving.staticBaseNoOffset) {
            return BloodGeometry.clipToMovingHost(p, p.stuckFace, poly, p.stuckPlane, p.stickMode, moving);
        }
        return BloodGeometry.clipAcrossBlocks(p, p.stuckFace, poly, p.stuckPlane, p.stickMode);
    }

    public static void rebuildDecalPolys(@Nonnull ParticleBlood p) {
        List<List<Util.Vertex>> tails;
        BloodCachesParticle.resetSupport(p);
        TextureAtlasSprite sprite = p.getSprite();
        if (sprite == null) {
            p.cache.shape.polys = null;
            return;
        }
        if (p.fallingDripActive) {
            p.cache.shape.polys = BloodGeometryCeiling.buildFallPolys(p, sprite);
            return;
        }
        if (!p.isStuck || p.stuckFace == null || p.stuckPos == null) {
            p.cache.shape.polys = null;
            return;
        }
        List<List<Util.Vertex>> roots = BloodGeometry.buildRootPolys(p, p.stuckFace, sprite);
        if (roots == null || roots.isEmpty()) {
            p.cache.shape.polys = null;
            return;
        }
        PistonSupport.MovingInfo moving = PistonSupport.getMovingInfo(p.getParticleWorld(), p.stuckPos, 1.0f, p);
        ArrayList<List<Util.Vertex>> combined = new ArrayList<List<Util.Vertex>>(roots.size() * 2);
        for (List<Util.Vertex> root : roots) {
            List<List<Util.Vertex>> clipped;
            if (root == null || root.size() < 3 || (clipped = moving != null && moving.movedState != null && !moving.staticBaseNoOffset ? BloodGeometry.clipToMovingHost(p, p.stuckFace, root, p.stuckPlane, p.stickMode, moving) : BloodGeometry.clipAcrossBlocks(p, p.stuckFace, root, p.stuckPlane, p.stickMode)) == null || clipped.isEmpty()) continue;
            combined.addAll(clipped);
        }
        if (combined.isEmpty()) {
            p.cache.shape.polys = null;
            return;
        }
        // the list is finished before it is published: the particles tick off the render thread (Async Particles), and the
        // renderer iterating it while the tails were still being added crashed with a ConcurrentModificationException
        if (p.stuckFace == Direction.DOWN && p.ceilingDripEnabled && p.ceilingDripStartAge >= 0 && (tails = BloodGeometryCeiling.buildTailPolys(p, sprite)) != null && !tails.isEmpty()) {
            combined.addAll(tails);
        }
        p.cache.shape.polys = combined;
    }

    @Nullable
    public static List<List<Util.Vertex>> buildRootPolys(@Nonnull ParticleBlood p, @Nonnull Direction face, @Nonnull TextureAtlasSprite sprite) {
        if (BloodTuning.isWallFace(face)) {
            List<Util.Vertex> quad = BloodGeometryWall.buildRenderQuad(p, face, sprite);
            if (quad == null || quad.size() < 3) {
                return null;
            }
            ArrayList<List<Util.Vertex>> out = new ArrayList<List<Util.Vertex>>(1);
            out.add(quad);
            return out;
        }
        return BloodGeometryGround.buildRootPolys(p, face, sprite);
    }

    @Nullable
    public static List<Util.Vertex> buildFlatFootprintQuad(@Nonnull TextureAtlasSprite sprite, double cx, double planeY, double cz, float rotRad, float quadScale, boolean flipU, double yPush) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float uLeft = flipU ? u0 : u1;
        float uRight = flipU ? u1 : u0;
        float half = 0.1f * quadScale;
        double cos = Math.cos(rotRad);
        double sin = Math.sin(rotRad);
        double[] ox = new double[]{-half, -half, half, half};
        double[] oz = new double[]{-half, half, half, -half};
        float[] uu = new float[]{uLeft, uLeft, uRight, uRight};
        float[] vv = new float[]{v1, v0, v0, v1};
        ArrayList<Util.Vertex> poly = new ArrayList<Util.Vertex>(4);
        for (int i = 0; i < 4; ++i) {
            double rx = ox[i] * cos - oz[i] * sin;
            double rz = ox[i] * sin + oz[i] * cos;
            poly.add(new Util.Vertex(cx + rx, planeY + yPush, cz + rz, uu[i], vv[i]));
        }
        return poly;
    }

    public static double renderedBottomY(@Nullable ParticleBlood p) {
        if (p == null || p.cache.shape.polys == null || p.cache.shape.polys.isEmpty()) {
            return Double.NaN;
        }
        double minY = Double.POSITIVE_INFINITY;
        for (List<Util.Vertex> poly : p.cache.shape.polys) {
            if (poly == null) continue;
            for (Util.Vertex v : poly) {
                if (v == null || !(v.y < minY)) continue;
                minY = v.y;
            }
        }
        if (minY == Double.POSITIVE_INFINITY) {
            return Double.NaN;
        }
        double baseY = Double.isNaN(p.cache.view.y) ? p.posY : p.cache.view.y;
        double dy = p.posY - baseY;
        return minY + dy;
    }

    @Nullable
    private static List<List<Util.Vertex>> clipToMovingHost(@Nonnull ParticleBlood p, @Nonnull Direction face, @Nonnull List<Util.Vertex> poly, double plane, @Nonnull ParticleBlood.StickMode mode, @Nonnull PistonSupport.MovingInfo moving) {
        BlockState renderState;
        if (poly.size() < 3 || moving.movedState == null) {
            return null;
        }
        if (p.getParticleWorld() == null || p.stuckPos == null) {
            ArrayList<List<Util.Vertex>> out = new ArrayList<List<Util.Vertex>>(1);
            out.add(poly);
            return out;
        }
        BlockState base = moving.movedState;
        Vec3 off = moving.offset != null ? moving.offset : Util.ZERO;
        long seed = Mth.getSeed((Vec3i)p.stuckPos);
        List<Util.FaceRect> rects = mode == ParticleBlood.StickMode.MODEL ? (SurfaceLookup.isModelRender(renderState = SurfaceLookup.getRenderState(p.getParticleWorld(), base, p.stuckPos)) ? SurfaceLookup.modelRects(p.stuckPos, renderState, face, seed) : SurfaceLookup.collisionRects(p.getParticleWorld(), p.stuckPos, base, face)) : SurfaceLookup.collisionRects(p.getParticleWorld(), p.stuckPos, base, face);
        if (rects == null || rects.isEmpty()) {
            return null;
        }
        ArrayList<List<Util.Vertex>> out = new ArrayList<List<Util.Vertex>>();
        for (Util.FaceRect r0 : rects) {
            Util.FaceRect clipRect;
            List<Util.Vertex> surface;
            Vec3 sample;
            if (r0 == null) continue;
            Util.FaceRect r = Util.offsetRect(face, r0, off);
            if (Math.abs(r.plane - plane) > 0.003 || !BloodSurfaceAttach.isExposed(p, p.stuckPos, face, sample = Util.rectCenter(p.stuckPos, face, r)) || (surface = Util.clipToRect(poly, face, clipRect = BloodGeometryWall.buildLooseRect(p, face, r, mode, p.stuckPos, off))).size() < 3) continue;
            BloodGeometryWall.accumulateSupport(p, face, r, clipRect, surface, mode, p.stuckPos);
            out.add(surface);
        }
        return out.isEmpty() ? null : out;
    }

    @Nullable
    private static List<List<Util.Vertex>> clipAcrossBlocks(@Nonnull ParticleBlood p, @Nonnull Direction face, @Nonnull List<Util.Vertex> poly, double plane, @Nonnull ParticleBlood.StickMode mode) {
        if (poly.size() < 3) {
            return null;
        }
        double minP = Double.POSITIVE_INFINITY;
        double maxP = Double.NEGATIVE_INFINITY;
        double minQ = Double.POSITIVE_INFINITY;
        double maxQ = Double.NEGATIVE_INFINITY;
        for (Util.Vertex v : poly) {
            double pp = Util.localP(face, v);
            double qq = Util.localQ(face, v);
            if (pp < minP) {
                minP = pp;
            }
            if (pp > maxP) {
                maxP = pp;
            }
            if (qq < minQ) {
                minQ = qq;
            }
            if (!(qq > maxQ)) continue;
            maxQ = qq;
        }
        int p0 = Mth.floor((double)(minP - 1.0E-4));
        int p1 = Mth.floor((double)(maxP + 1.0E-4));
        int q0 = Mth.floor((double)(minQ - 1.0E-4));
        int q1 = Mth.floor((double)(maxQ + 1.0E-4));
        int cp = p0 + p1 >> 1;
        int cq = q0 + q1 >> 1;
        p0 = Util.clampInt(p0, cp - 4, cp + 4);
        p1 = Util.clampInt(p1, cp - 4, cp + 4);
        q0 = Util.clampInt(q0, cq - 4, cq + 4);
        q1 = Util.clampInt(q1, cq - 4, cq + 4);
        int fixed = Util.blockCoordForPlane(face, plane);
        ArrayList<List<Util.Vertex>> out = new ArrayList<List<Util.Vertex>>();
        if (face == Direction.UP || face == Direction.DOWN) {
            int y = fixed;
            for (int x = p0; x <= p1; ++x) {
                for (int z = q0; z <= q1; ++z) {
                    BloodGeometry.clipAgainstBlock(p, out, poly, face, plane, BlockPos.containing(x, y, z), mode, minP, maxP, minQ, maxQ);
                }
            }
        } else if (face == Direction.EAST || face == Direction.WEST) {
            int x = fixed;
            for (int z = p0; z <= p1; ++z) {
                for (int y = q0; y <= q1; ++y) {
                    BloodGeometry.clipAgainstBlock(p, out, poly, face, plane, BlockPos.containing(x, y, z), mode, minP, maxP, minQ, maxQ);
                }
            }
        } else {
            int z = fixed;
            for (int x = p0; x <= p1; ++x) {
                for (int y = q0; y <= q1; ++y) {
                    BloodGeometry.clipAgainstBlock(p, out, poly, face, plane, BlockPos.containing(x, y, z), mode, minP, maxP, minQ, maxQ);
                }
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static void clipAgainstBlock(@Nonnull ParticleBlood p, @Nonnull List<List<Util.Vertex>> out, @Nonnull List<Util.Vertex> poly, @Nonnull Direction face, double plane, @Nonnull BlockPos bp, @Nonnull ParticleBlood.StickMode mode, double minP, double maxP, double minQ, double maxQ) {
        List<Util.FaceRect> rects;
        if (p.getParticleWorld() == null) {
            return;
        }
        if (p.getParticleWorld().isEmptyBlock(bp)) {
            return;
        }
        BlockState worldState = p.getParticleWorld().getBlockState(bp);
        boolean fromMovingPistonStaticBase = false;
        BlockState base = worldState;
        PistonSupport.MovingInfo mi = PistonSupport.getMovingInfo(p.getParticleWorld(), bp, 1.0f, p);
        if (mi != null && mi.movedState != null) {
            if (!mi.staticBaseNoOffset) {
                return;
            }
            base = mi.movedState;
            fromMovingPistonStaticBase = true;
        }
        long seed = Mth.getSeed((Vec3i)bp);
        if (mode == ParticleBlood.StickMode.MODEL) {
            BlockState renderState;
            if (fromMovingPistonStaticBase && base.getBlock() instanceof PistonBaseBlock) {
                renderState = base;

            } else {
                renderState = SurfaceLookup.getRenderState(p.getParticleWorld(), base, bp);
            }
            if (!SurfaceLookup.isModelRender(renderState)) {
                return;
            }
            rects = SurfaceLookup.modelRects(bp, renderState, face, seed);
        } else {
            rects = SurfaceLookup.collisionRects(p.getParticleWorld(), bp, base, face);
        }
        if (rects == null || rects.isEmpty()) {
            return;
        }
        for (Util.FaceRect r : rects) {
            Util.FaceRect clipRect;
            List<Util.Vertex> surface;
            Vec3 sample;
            if (r == null || Math.abs(r.plane - plane) > 0.003 || r.pMax < minP || r.pMin > maxP || r.qMax < minQ || r.qMin > maxQ || !BloodSurfaceAttach.isExposed(p, bp, face, sample = Util.rectCenter(bp, face, r)) || (surface = Util.clipToRect(poly, face, clipRect = BloodGeometryWall.buildLooseRect(p, face, r, mode, bp, null))).size() < 3) continue;
            BloodGeometryWall.accumulateSupport(p, face, r, clipRect, surface, mode, bp);
            out.add(surface);
        }
    }
}

