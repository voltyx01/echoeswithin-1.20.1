package com.voltyx.efw.gore.particle.common.surface;

import com.voltyx.efw.gore.particle.common.GoreUtil;

import com.voltyx.efw.gore.ModConfigurationClient;
import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.blocksupport.DynamicPoseSupport;
import com.voltyx.efw.gore.particle.blocksupport.FenceGateSupport;
import com.voltyx.efw.gore.particle.blocksupport.PistonSupport;
import com.voltyx.efw.gore.particle.common.BloodTuning;
import com.voltyx.efw.gore.particle.common.SurfaceLookup;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlpha;
import com.voltyx.efw.gore.particle.common.cache.BloodCachesParticle;
import com.voltyx.efw.gore.particle.common.geometry.BloodGeometry;
import com.voltyx.efw.gore.particle.common.motion.BloodMotionHeavy;
import com.voltyx.efw.gore.particle.common.motion.BloodMotionHot;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceCeiling;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceGround;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceWall;
import com.voltyx.efw.gore.particle.state.BloodHotBlocks;
import com.voltyx.efw.gore.particle.state.BloodMagic;
import com.voltyx.efw.gore.particle.state.BloodSlimy;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.LiquidBlock;
import com.voltyx.efw.gore.particle.common.GoreMaterial;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
public class BloodSurfaceAttach {
    static final double TALL_TOP_Y = 1.001;
    static final int WALL_REATTACH_COOLDOWN_TICKS = 20;
    private static final double CEILING_SEAM_PLANE_EPS = 0.003;
    private static final double CEILING_SEAM_RANGE_EPS = 0.001;

    public static boolean tryAttach(ParticleBlood p, BlockPos pos, Direction hintFace, Vec3 rayStart, Vec3 rayEnd, Vec3 hitGuess) {
        if (p == null || pos == null || hintFace == null) {
            return false;
        }
        if (!BloodMagic.allowAttachmentAttempt(p, hintFace)) {
            return false;
        }
        if (!BloodMotionHeavy.allowAttach(p, hintFace)) {
            return false;
        }
        if (!BloodSlimy.allowAttachmentAttempt(p, hintFace)) {
            return false;
        }
        if (p.wallAttachCooldownTicks > 0 && BloodTuning.isWallFace(hintFace)) {
            return false;
        }
        Level w = p.getParticleWorld();
        if (w == null || w.isEmptyBlock(pos)) {
            return false;
        }
        BlockState base = w.getBlockState(pos);
        if (BloodTuning.isWallFace(hintFace) && BloodHotBlocks.isHotBlock(base)) {
            return false;
        }
        BlockState renderState = SurfaceLookup.getRenderState(w, base, pos);
        long seed = Mth.getSeed((Vec3i)pos);
        if (SurfaceLookup.isModelRender(renderState) && BloodSurfaceAttach.tryModel(p, pos, renderState, seed, hintFace, rayStart, rayEnd, hitGuess)) {
            return true;
        }
        return BloodSurfaceAttach.tryCollision(p, pos, base, hintFace, hitGuess);
    }

    static boolean attach(ParticleBlood p, ParticleBlood.StickMode mode, BlockPos pos, Direction face, double plane, Vec3 pointOnPlane) {
        boolean allowWallDrip;
        if (p == null || pos == null || face == null) {
            return false;
        }
        if (!BloodMagic.allowAttachmentAttempt(p, face)) {
            return false;
        }
        if (face == Direction.UP && plane > (double)((net.minecraft.core.Vec3i) pos).getY() + 1.001) {
            return false;
        }
        if (!BloodMotionHeavy.allowAttach(p, face)) {
            return false;
        }
        if (!BloodSlimy.allowAttachmentAttempt(p, face)) {
            return false;
        }
        if (p.wallAttachCooldownTicks > 0 && BloodTuning.isWallFace(face)) {
            return false;
        }
        if (BloodTuning.isWallFace(face) && BloodSurfaceAttach.wallInWater(p.getParticleWorld(), pos, face, pointOnPlane)) {
            return false;
        }
        if (face == Direction.DOWN && !ModConfigurationClient.ceiling.stick) {
            return false;
        }
        BlockState baseForCache = null;
        Level w = p.getParticleWorld();
        if (w != null && w.isLoaded(pos) && !w.isEmptyBlock(pos)) {
            try {
                baseForCache = w.getBlockState(pos);
            }
            catch (Throwable t) {
                baseForCache = null;
            }
            PistonSupport.MovingInfo mi = PistonSupport.getMovingInfo(w, pos, 1.0f);
            if (mi != null && mi.movedState != null) {
                baseForCache = mi.movedState;
            }
        }
        if (BloodTuning.isWallFace(face) && BloodHotBlocks.isHotBlock(baseForCache)) {
            return false;
        }
        if (face == Direction.DOWN && !BloodTuning.ceilingAttachEnabledForHost(baseForCache)) {
            return false;
        }
        if (face == Direction.DOWN && pointOnPlane != null && BloodSurfaceAttach.blocksCeilingAttachFromBelow(p, pos, pointOnPlane)) {
            return false;
        }
        AttachSnapshot snap = new AttachSnapshot(p);
        p.wallAttachCooldownTicks = 0;
        p.isStuck = true;
        p.stickMode = mode;
        p.stuckPos = pos;
        p.stuckFace = face;
        p.stuckPlane = plane;
        p.stuckStartAge = p.getAge();
        p.cache.fade.modelStartAge = -1;
        p.cache.fade.modelTicks = 0;
        p.relocateGraceTicks = 0;
        p.sideDetachAge = -1;
        p.resetCeilingDripRuntime();
        p.resetFallingDripRuntime();
        p.noAirFlutter = false;
        p.cache.host.base = baseForCache;
        BloodSlimy.resetGroundBouncePlan(p);
        BloodSlimy.beginSurfacePop(p);
        boolean bl = allowWallDrip = BloodTuning.isWallFace(face) && BloodTuning.dripEnabledForHost(p.cache.host.base);
        if (allowWallDrip) {
            float chance = BloodSlimy.effectiveWallDripChance01(p);
            if (chance > 0.0f && p.planWallDetachRoll < chance) {
                int delay = Math.max(0, BloodSlimy.effectiveWallDetachDelayTicks(p));
                int detachAge = p.stuckStartAge + delay;
                int needAge = detachAge + 8;
                int futureMaxAge = Math.max(p.getMaxAge(), needAge);
                float predicted = BloodAlpha.predictAlpha(p, detachAge, futureMaxAge);
                float minAlpha = 0.5f;
                if (minAlpha <= 0.0f || predicted >= minAlpha - 1.0E-6f) {
                    p.sideDetachAge = detachAge;
                    if (p.getMaxAge() < needAge) {
                        p.setMaxAge(needAge);
                    }
                } else {
                    p.sideDetachAge = -1;
                }
            } else {
                p.sideDetachAge = -1;
            }
        }
        if (face == Direction.DOWN) {
            BloodSurfaceCeiling.capture(p);
        }
        Vec3 off = Util.ZERO;
        PistonSupport.MovingInfo miOff = PistonSupport.getMovingInfo(p.getParticleWorld(), pos, 1.0f);
        if (miOff != null && miOff.offset != null && !miOff.staticBaseNoOffset) {
            off = miOff.offset;
        }
        p.stuckLocalOnPlane = new Vec3(pointOnPlane.x - (double)((net.minecraft.core.Vec3i) pos).getX() - off.x, pointOnPlane.y - (double)((net.minecraft.core.Vec3i) pos).getY() - off.y, pointOnPlane.z - (double)((net.minecraft.core.Vec3i) pos).getZ() - off.z);
        p.motionX = 0.0;
        p.motionY = 0.0;
        p.motionZ = 0.0;
        p.setGravity(0.0f);
        FenceGateSupport.captureOnAttach(p);
        if (!FenceGateSupport.isGateState(p.cache.host.base)) {
            DynamicPoseSupport.captureOnAttach(p);
        } else {
            p.cache.host.piece = null;
        }
        int oldSlimyGrowth = BloodSlimy.pushRapidGrowthWindow(p);
        if (BloodTuning.isWallFace(face)) {
            BloodSurfaceWall.update(p);
        } else {
            BloodSurfaceGround.update(p);
            if (face == Direction.DOWN) {
                BloodSurfaceCeiling.update(p);
            } else {
                p.dripAmount = 0.0f;
            }
        }
        BloodSlimy.restoreRapidGrowthWindow(p, oldSlimyGrowth);
        BloodSlimy.applyScaleForCurrentState(p);
        BloodSurfaceAttach.moveStuck(p, pointOnPlane);
        p.prevPosX = p.posX;
        p.prevPosY = p.posY;
        p.prevPosZ = p.posZ;
        BloodCachesParticle.invalidateShape(p);
        BloodCachesParticle.invalidateView(p);
        BloodGeometry.rebuildDecalPolys(p);
        p.cache.shape.scale = p.getScale();
        p.cache.shape.drip = p.dripAmount;
        BloodGeometry.cacheRenderPos(p);
        if (p.cache.shape.polys == null || p.cache.shape.polys.isEmpty()) {
            BloodSurfaceAttach.detach(p);
            int detachCooldown = p.wallAttachCooldownTicks;
            snap.restore(p);
            p.wallAttachCooldownTicks = Math.max(snap.wallCooldown, detachCooldown);
            return false;
        }
        return true;
    }

    public static void hotJump(ParticleBlood p, double upMin, double upMax, double sideJitter, double maxHorizontal, int wobbleTicks) {
        if (p == null) {
            return;
        }
        if (!p.isStuck || p.stuckFace != Direction.UP) {
            return;
        }
        Vec3 onPlane = BloodSurfaceAttach.anchorPoint(p);
        BloodSurfaceAttach.clearStuck(p);
        BloodSlimy.onDetachedFromSurface(p);
        p.resetCeilingDripRuntime();
        p.resetFallingDripRuntime();
        p.noAirFlutter = false;
        BloodSurfaceAttach.beginFall(p, 0, true);
        AABB bb = p.getBoundingBox();
        double halfH = (bb.maxY - bb.minY) * 0.5;
        double out = halfH + 0.003;
        p.setPosition(onPlane.x, onPlane.y + out, onPlane.z);
        double up = upMin;
        if (upMax > upMin) {
            up += p.getRand().nextDouble() * (upMax - upMin);
        }
        p.motionX = (p.getRand().nextDouble() - 0.5) * sideJitter;
        p.motionY = up;
        p.motionZ = (p.getRand().nextDouble() - 0.5) * sideJitter;
        BloodSurfaceAttach.clampHotJump(p, maxHorizontal);
        p.detachWobbleTicks = Math.max(p.detachWobbleTicks, Math.max(0, wobbleTicks));
        p.idleTicks = 0;
        BloodMotionHot.beginGroundBurst(p);
        BloodSurfaceAttach.syncPrev(p);
    }

    public static void detach(ParticleBlood p) {
        Direction fromFace = p.stuckFace;
        boolean fromWall = BloodTuning.isWallFace(fromFace);
        Vec3 onPlane = fromFace != null ? BloodSurfaceAttach.pointOnPlaneFromPos(p) : null;
        double wallBottomY = fromWall ? BloodGeometry.renderedBottomY(p) : Double.NaN;
        float detachScale = p.getScale();
        BloodSurfaceAttach.clearStuck(p);
        BloodSlimy.onDetachedFromSurface(p);
        p.resetCeilingDripRuntime();
        p.resetFallingDripRuntime();
        p.noAirFlutter = false;
        int reattachCooldown = fromWall ? BloodSlimy.wallReattachCooldownTicks(p, 20) : 0;
        float cs = BloodSurfaceAttach.beginFall(p, reattachCooldown, true);
        if (fromWall && onPlane != null) {
            BloodSurfaceAttach.dropWall(p, fromFace, onPlane, wallBottomY, cs, detachScale);
            return;
        }
        BloodSurfaceAttach.impulse(p, fromFace);
    }

    @Nullable
    public static BlockState baseState(ParticleBlood p) {
        BlockState now;
        if (p.getParticleWorld() == null || p.stuckPos == null) {
            return null;
        }
        if (!p.getParticleWorld().isLoaded(p.stuckPos)) {
            return null;
        }
        if (p.getParticleWorld().isEmptyBlock(p.stuckPos)) {
            return null;
        }
        try {
            now = p.getParticleWorld().getBlockState(p.stuckPos);
        }
        catch (Throwable t) {
            return null;
        }
        PistonSupport.MovingInfo mi = PistonSupport.getMovingInfo(p.getParticleWorld(), p.stuckPos, 1.0f, p);
        if (mi != null && mi.movedState != null) {
            return mi.movedState;
        }
        return now;
    }

    public static Vec3 anchorPoint(ParticleBlood p) {
        if (p.stuckPos != null && p.stuckLocalOnPlane != null) {
            Vec3 base = new Vec3((double)((net.minecraft.core.Vec3i) p.stuckPos).getX() + p.stuckLocalOnPlane.x, (double)((net.minecraft.core.Vec3i) p.stuckPos).getY() + p.stuckLocalOnPlane.y, (double)((net.minecraft.core.Vec3i) p.stuckPos).getZ() + p.stuckLocalOnPlane.z);
            PistonSupport.MovingInfo mi = PistonSupport.getMovingInfo(p.getParticleWorld(), p.stuckPos, 1.0f, p);
            if (mi != null && mi.offset != null && !mi.staticBaseNoOffset) {
                base = base.add(mi.offset);
            }
            return base;
        }
        return BloodSurfaceAttach.pointOnPlaneFromPos(p);
    }

    static boolean wallInWater(@Nullable Level world, @Nullable BlockPos hostPos, @Nullable Direction face, @Nullable Vec3 pointOnPlane) {
        BlockState fluidState;
        if (world == null || hostPos == null || face == null) {
            return false;
        }
        if (!BloodTuning.isWallFace(face)) {
            return false;
        }
        BlockPos fluidPos = hostPos.relative(face);
        if (!world.isLoaded(fluidPos)) {
            return false;
        }
        try {
            fluidState = world.getBlockState(fluidPos);
        }
        catch (Throwable t) {
            return false;
        }
        if (fluidState == null || GoreMaterial.of(fluidState) != GoreMaterial.WATER) {
            return false;
        }
        if (pointOnPlane == null) {
            return true;
        }
        double height01 = 1.0;
        try {
            if (fluidState.getBlock() instanceof LiquidBlock) {
                int lvl;
                Integer lvlObj = (Integer)fluidState.getValue((Property)LiquidBlock.LEVEL);
                int n = lvl = lvlObj != null ? lvlObj : 0;
                if (lvl >= 8) {
                    height01 = 1.0;
                } else {
                    height01 = (8.0 - (double)lvl) / 8.0;
                    height01 = Util.clamp(height01, 0.0, 1.0);
                }
            }
        }
        catch (Throwable ignored) {
            height01 = 1.0;
        }
        double waterSurfaceY = (double)((net.minecraft.core.Vec3i) fluidPos).getY() + height01;
        return pointOnPlane.y <= waterSurfaceY + 1.0E-6;
    }

    private static boolean blocksCeilingAttachFromBelow(@Nullable ParticleBlood p, @Nonnull BlockPos pos, @Nonnull Vec3 pointOnPlane) {
        BlockState belowBase;
        if (p == null) {
            return false;
        }
        Level w = p.getParticleWorld();
        if (w == null) {
            return false;
        }
        BlockPos below = pos.below();
        if (!w.isLoaded(below) || w.isEmptyBlock(below)) {
            return false;
        }
        try {
            belowBase = w.getBlockState(below);
        }
        catch (Throwable t) {
            return false;
        }
        PistonSupport.MovingInfo mi = PistonSupport.getMovingInfo(w, below, 1.0f, p);
        if (mi != null && mi.movedState != null) {
            belowBase = mi.movedState;
        }
        if (BloodSurfaceAttach.upFaceRectsContainPoint(SurfaceLookup.collisionRects(w, below, belowBase, Direction.UP), pointOnPlane)) {
            return true;
        }
        BlockState renderState = SurfaceLookup.getRenderState(w, belowBase, below);
        if (!SurfaceLookup.isModelRender(renderState)) {
            return false;
        }
        long seed = Mth.getSeed((Vec3i)below);
        return BloodSurfaceAttach.upFaceRectsContainPoint(SurfaceLookup.modelRects(below, renderState, Direction.UP, seed), pointOnPlane);
    }

    private static boolean upFaceRectsContainPoint(@Nullable List<Util.FaceRect> rects, @Nonnull Vec3 pointOnPlane) {
        if (rects == null || rects.isEmpty()) {
            return false;
        }
        double x = pointOnPlane.x;
        double y = pointOnPlane.y;
        double z = pointOnPlane.z;
        for (Util.FaceRect rect : rects) {
            if (rect == null || Math.abs(rect.plane - y) > 0.003 || x < rect.pMin - 0.001 || x > rect.pMax + 0.001 || z < rect.qMin - 0.001 || z > rect.qMax + 0.001) continue;
            return true;
        }
        return false;
    }

    public static boolean isExposed(ParticleBlood p, BlockPos owner, Direction face, Vec3 pointOnFacePlane) {
        Vec3 out = pointOnFacePlane.add((double)face.getStepX() * 0.002, (double)face.getStepY() * 0.002, (double)face.getStepZ() * 0.002);
        Vec3 end = out.add((double)face.getStepX() * 0.05, (double)face.getStepY() * 0.05, (double)face.getStepZ() * 0.05);
        BlockHitResult r = GoreUtil.rayTrace(p.getParticleWorld(), out, end, false, true, true);
        return r == null;
    }

    public static boolean sameHost(@Nullable BlockState a, @Nullable BlockState b) {
        if (a == null || b == null) {
            return false;
        }
        return a == b || a.getBlock() == b.getBlock();
    }

    public static boolean tryRelocate(ParticleBlood p) {
        if (p.getParticleWorld() == null || p.stuckPos == null || p.stuckFace == null) {
            return false;
        }
        if (p.stuckLocalOnPlane == null) {
            return false;
        }
        BlockState wanted = p.cache.host.base;
        if (wanted == null) {
            return false;
        }
        Vec3 currentOnPlane = BloodSurfaceAttach.pointOnPlaneFromPos(p);
        int bestRank = Integer.MAX_VALUE;
        double bestDistSq = Double.POSITIVE_INFINITY;
        BlockPos bestPos = null;
        for (Direction dir : Direction.values()) {
            int rank;
            BlockState candNow;
            BlockPos cand = p.stuckPos.relative(dir);
            if (!p.getParticleWorld().isLoaded(cand) || p.getParticleWorld().isEmptyBlock(cand)) continue;
            try {
                candNow = p.getParticleWorld().getBlockState(cand);
            }
            catch (Throwable t) {
                continue;
            }
            BlockState candEff = candNow;
            Vec3 candOff = Util.ZERO;
            PistonSupport.MovingInfo mi = PistonSupport.getMovingInfo(p.getParticleWorld(), cand, 1.0f, p);
            if (mi != null && mi.movedState != null) {
                candEff = mi.movedState;
                if (mi.offset != null && !mi.staticBaseNoOffset) {
                    candOff = mi.offset;
                }
            }
            if (candEff == wanted) {
                rank = 0;
            } else {
                if (candEff.getBlock() != wanted.getBlock()) continue;
                rank = 1;
            }
            Vec3 candOnPlane = new Vec3((double)((net.minecraft.core.Vec3i) cand).getX() + p.stuckLocalOnPlane.x + candOff.x, (double)((net.minecraft.core.Vec3i) cand).getY() + p.stuckLocalOnPlane.y + candOff.y, (double)((net.minecraft.core.Vec3i) cand).getZ() + p.stuckLocalOnPlane.z + candOff.z);
            double dsq = candOnPlane.distanceToSqr(currentOnPlane);
            if (rank >= bestRank && (rank != bestRank || !(dsq < bestDistSq))) continue;
            bestRank = rank;
            bestDistSq = dsq;
            bestPos = cand;
        }
        if (bestPos != null) {
            p.stuckPos = bestPos;
            return true;
        }
        return false;
    }

    public static void moveStuck(ParticleBlood p, Vec3 pointOnPlane) {
        if (pointOnPlane == null || p.stuckFace == null) {
            return;
        }
        double px = pointOnPlane.x + (double)p.stuckFace.getStepX() * p.surfaceOffset;
        double py = pointOnPlane.y + (double)p.stuckFace.getStepY() * p.surfaceOffset;
        double pz = pointOnPlane.z + (double)p.stuckFace.getStepZ() * p.surfaceOffset;
        p.setPosition(px, py, pz);
    }

    @Nullable
    static Direction clearStuck(ParticleBlood p) {
        Direction fromFace = p.stuckFace;
        p.resetAmalgamState(fromFace == Direction.UP);
        p.isStuck = false;
        p.stickMode = ParticleBlood.StickMode.MODEL;
        p.stuckFace = null;
        p.stuckPos = null;
        p.stuckPlane = 0.0;
        p.cache.shape.polys = null;
        p.stuckLocalOnPlane = null;
        p.cache.host.base = null;
        p.cache.host.piece = null;
        p.cache.host.poseSnapPrev = false;
        p.cache.host.poseGraceTicks = 0;
        p.cache.gate.has = false;
        p.cache.gate.open = false;
        p.cache.gate.part = 0;
        p.relocateGraceTicks = 0;
        p.cache.shape.scale = Float.NaN;
        p.cache.shape.drip = Float.NaN;
        p.cache.view.x = Double.NaN;
        p.cache.view.y = Double.NaN;
        p.cache.view.z = Double.NaN;
        p.stuckStartAge = -1;
        p.sideDetachAge = -1;
        p.slimySurfaceAnimStartAge = -1;
        p.slimySurfaceAnimTicks = 0;
        return fromFace;
    }

    static float beginFall(ParticleBlood p, int wallCooldownTicks, boolean resetSupportCaches) {
        p.wallAttachCooldownTicks = Math.max(0, wallCooldownTicks);
        p.setGravity(0.8f);
        p.setCanCollide(true);
        p.setOnGroundFlag(false);
        if (resetSupportCaches) {
            BloodCachesParticle.resetSupport(p);
            BloodCachesParticle.resetModelFade(p);
            BloodCachesParticle.resetWaterFade(p);
        }
        float cs = BloodTuning.collisionSizeForScale(p.getScale());
        p.setSizeSafe(cs, cs);
        return cs;
    }

    static void syncPrev(ParticleBlood p) {
        p.prevPosX = p.posX;
        p.prevPosY = p.posY;
        p.prevPosZ = p.posZ;
    }

    static Vec3 pointOnPlaneFromPos(ParticleBlood p) {
        if (p.stuckFace == null) {
            return new Vec3(p.posX, p.posY, p.posZ);
        }
        return new Vec3(p.posX - (double)p.stuckFace.getStepX() * p.surfaceOffset, p.posY - (double)p.stuckFace.getStepY() * p.surfaceOffset, p.posZ - (double)p.stuckFace.getStepZ() * p.surfaceOffset);
    }

    private static void clampHotJump(ParticleBlood p, double maxH) {
        if (p == null) {
            return;
        }
        if (maxH <= 0.0) {
            p.motionX = 0.0;
            p.motionZ = 0.0;
            return;
        }
        double hs = p.motionX * p.motionX + p.motionZ * p.motionZ;
        double maxSq = maxH * maxH;
        if (hs > maxSq && hs > 1.0E-12) {
            double m = maxH / Math.sqrt(hs);
            p.motionX *= m;
            p.motionZ *= m;
        }
    }

    private static boolean tryModel(ParticleBlood p, BlockPos pos, BlockState renderState, long seed, Direction hintFace, Vec3 rayStart, Vec3 rayEnd, Vec3 hitGuess) {
        if (hintFace == Direction.UP && hitGuess != null && hitGuess.y > (double)((net.minecraft.core.Vec3i) pos).getY() + 1.001) {
            return BloodSurfaceAttach.redirectTallModel(p, pos, renderState, seed, hitGuess);
        }
        SurfaceLookup.Hit mh = SurfaceLookup.raycastModel(pos, renderState, seed, rayStart, rayEnd, hintFace);
        if (mh == null) {
            mh = SurfaceLookup.raycastModel(pos, renderState, seed, rayStart, rayEnd, null);
        }
        if (mh != null) {
            if (mh.face == Direction.UP && mh.plane > (double)((net.minecraft.core.Vec3i) pos).getY() + 1.001) {
                return BloodSurfaceAttach.redirectTallModel(p, pos, renderState, seed, mh.hitPos);
            }
            Vec3 onPlane = Util.clampToRectOnFacePlane(mh.face, mh.rect, mh.hitPos);
            if (!BloodSurfaceAttach.isExposed(p, pos, mh.face, onPlane)) {
                return false;
            }
            return BloodSurfaceAttach.attach(p, ParticleBlood.StickMode.MODEL, pos, mh.face, mh.plane, onPlane);
        }
        List<Util.FaceRect> rects = SurfaceLookup.modelRects(pos, renderState, hintFace, seed);
        if (rects.isEmpty()) {
            return false;
        }
        Util.FaceRect bestRect = Util.pickBestRect(rects, hintFace, hitGuess);
        if (bestRect == null) {
            return false;
        }
        if (hintFace == Direction.UP && bestRect.plane > (double)((net.minecraft.core.Vec3i) pos).getY() + 1.001) {
            Vec3 h = hitGuess != null ? hitGuess : Util.rectCenter(pos, hintFace, bestRect);
            return BloodSurfaceAttach.redirectTallModel(p, pos, renderState, seed, h);
        }
        Vec3 raw = hitGuess != null ? hitGuess : Util.rectCenter(pos, hintFace, bestRect);
        Vec3 onPlane = Util.clampToRectOnFacePlane(hintFace, bestRect, raw);
        if (!BloodSurfaceAttach.isExposed(p, pos, hintFace, onPlane)) {
            return false;
        }
        return BloodSurfaceAttach.attach(p, ParticleBlood.StickMode.MODEL, pos, hintFace, bestRect.plane, onPlane);
    }

    private static boolean tryCollision(ParticleBlood p, BlockPos pos, BlockState base, Direction hintFace, Vec3 hitGuess) {
        if (hintFace == Direction.UP && hitGuess != null && hitGuess.y > (double)((net.minecraft.core.Vec3i) pos).getY() + 1.001) {
            return BloodSurfaceAttach.redirectTallCollision(p, pos, base, hitGuess);
        }
        List<Util.FaceRect> rects = SurfaceLookup.collisionRects(p.getParticleWorld(), pos, base, hintFace);
        if (rects.isEmpty()) {
            return false;
        }
        Util.FaceRect bestRect = Util.pickBestRect(rects, hintFace, hitGuess);
        if (bestRect == null) {
            return false;
        }
        if (hintFace == Direction.UP && bestRect.plane > (double)((net.minecraft.core.Vec3i) pos).getY() + 1.001) {
            Vec3 h = hitGuess != null ? hitGuess : Util.rectCenter(pos, hintFace, bestRect);
            return BloodSurfaceAttach.redirectTallCollision(p, pos, base, h);
        }
        Vec3 raw = hitGuess != null ? hitGuess : Util.rectCenter(pos, hintFace, bestRect);
        Vec3 onPlane = Util.clampToRectOnFacePlane(hintFace, bestRect, raw);
        if (!BloodSurfaceAttach.isExposed(p, pos, hintFace, onPlane)) {
            return false;
        }
        return BloodSurfaceAttach.attach(p, ParticleBlood.StickMode.COLLISION, pos, hintFace, bestRect.plane, onPlane);
    }

    private static void dropWall(ParticleBlood p, Direction fromFace, Vec3 onPlane, double wallBottomY, float collisionSize, float detachScale) {
        double outEps = 0.0015;
        double out = 0.5 * (double)collisionSize + 0.0015;
        double y = p.posY;
        if (!Double.isNaN(wallBottomY)) {
            double halfQuad = 0.1 * (double)detachScale;
            y = wallBottomY + halfQuad;
        }
        p.setPosition(onPlane.x + (double)fromFace.getStepX() * out, y, onPlane.z + (double)fromFace.getStepZ() * out);
        p.motionX = 0.0;
        p.motionZ = 0.0;
        p.motionY = -(0.02 + p.getRand().nextDouble() * 0.01);
        p.detachWobbleTicks = 0;
        p.noAirFlutter = true;
        BloodSurfaceAttach.syncPrev(p);
    }

    private static void impulse(ParticleBlood p, @Nullable Direction fromFace) {
        double nx;
        double sizeFactor = (double)p.getScale() / (double)Math.max(0.001f, p.spawnScale);
        sizeFactor = Util.clamp(sizeFactor, 0.6, 2.0);
        double invSize = 1.0 / sizeFactor;
        double down = fromFace == Direction.UP ? 0.012 + p.getRand().nextDouble() * 0.018 : (fromFace == Direction.DOWN ? 0.02 + p.getRand().nextDouble() * 0.026 : 0.02 + p.getRand().nextDouble() * 0.02);
        double outward = 0.0;
        if (fromFace != null && fromFace != Direction.UP) {
            outward = fromFace == Direction.DOWN ? (0.004 + p.getRand().nextDouble() * 0.007) * invSize : (0.005 + p.getRand().nextDouble() * 0.01) * invSize;
        }
        double side = (0.002 + p.getRand().nextDouble() * 0.008) * invSize;
        double jitter = (p.getRand().nextDouble() - 0.5) * 0.0025;
        double mx = 0.0;
        double my = -down;
        double mz = 0.0;
        if (fromFace != null && fromFace != Direction.UP) {
            mx += (double)fromFace.getStepX() * outward;
            my += (double)fromFace.getStepY() * outward;
            mz += (double)fromFace.getStepZ() * outward;
        }
        if (fromFace != null && fromFace.getAxis().isHorizontal()) {
            Direction sideFace = p.getRand().nextBoolean() ? fromFace.getClockWise() : fromFace.getCounterClockWise();
            double sgn = p.getRand().nextBoolean() ? 1.0 : -1.0;
            mx += (double)sideFace.getStepX() * side * sgn;
            mz += (double)sideFace.getStepZ() * side * sgn;
            my -= (0.01 + p.getRand().nextDouble() * 0.012) * invSize;
        } else {
            double ang = p.getRand().nextDouble() * Math.PI * 2.0;
            mx += Math.cos(ang) * side;
            mz += Math.sin(ang) * side;
        }
        double maxH = 0.05;
        double hSq = (mx += jitter) * mx + (mz -= jitter * 0.85) * mz;
        if (hSq > 0.0025000000000000005) {
            double m = 0.05 / Math.sqrt(hSq);
            mx *= m;
            mz *= m;
        }
        if (my > -0.01) {
            my = -0.01;
        }
        p.motionX = mx;
        p.motionY = my;
        p.motionZ = mz;
        double minOut = 0.0;
        if (fromFace != null) {
            AABB bb = p.getBoundingBox();
            double halfW = (bb.maxX - bb.minX) * 0.5;
            double halfH = (bb.maxY - bb.minY) * 0.5;
            double halfN = fromFace.getAxis() == Direction.Axis.Y ? halfH : halfW;
            minOut = halfN + 0.003;
        }
        if (fromFace != null) {
            if (fromFace == Direction.UP) {
                nx = (p.getRand().nextDouble() - 0.5) * 0.012 * invSize;
                double nz = (p.getRand().nextDouble() - 0.5) * 0.012 * invSize;
                p.setPosition(p.posX + nx, p.posY + minOut, p.posZ + nz);
            } else {
                double nudge = (0.006 + p.getRand().nextDouble() * 0.006) * invSize;
                if (nudge < minOut) {
                    nudge = minOut;
                }
                p.setPosition(p.posX + (double)fromFace.getStepX() * nudge, p.posY + (double)fromFace.getStepY() * nudge, p.posZ + (double)fromFace.getStepZ() * nudge);
            }
        } else {
            nx = (p.getRand().nextDouble() - 0.5) * 0.012 * invSize;
            double nz = (p.getRand().nextDouble() - 0.5) * 0.012 * invSize;
            p.setPosition(p.posX + nx, p.posY, p.posZ + nz);
        }
        int baseWobble = 10 + p.getRand().nextInt(10);
        if (fromFace != null && fromFace.getAxis().isHorizontal()) {
            baseWobble += 6;
        }
        double wobbleScale = Util.clamp(1.35 - 0.55 * sizeFactor, 0.35, 1.25);
        p.detachWobbleTicks = Math.max(0, (int)Math.round((double)baseWobble * wobbleScale));
        BloodSurfaceAttach.syncPrev(p);
    }

    private static boolean redirectTallModel(ParticleBlood p, BlockPos pos, BlockState renderState, long seed, Vec3 hit) {
        Direction[] sides;
        FacePick best = null;
        for (Direction f : sides = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            Vec3 onPlane;
            Util.FaceRect r;
            List<Util.FaceRect> rects = SurfaceLookup.modelRects(pos, renderState, f, seed);
            if (rects.isEmpty() || (r = Util.pickBestRect(rects, f, hit)) == null || !BloodSurfaceAttach.isExposed(p, pos, f, onPlane = Util.clampToRectOnFacePlane(f, r, hit))) continue;
            double d = Util.distanceSqToRect(f, r, hit);
            if (best != null && !(d < best.distSq)) continue;
            best = new FacePick(f, r, d, onPlane);
        }
        if (best == null) {
            return false;
        }
        return BloodSurfaceAttach.attach(p, ParticleBlood.StickMode.MODEL, pos, best.face, best.rect.plane, best.pointOnPlane);
    }

    private static boolean redirectTallCollision(ParticleBlood p, BlockPos pos, BlockState base, Vec3 hit) {
        Direction[] sides;
        FacePick best = null;
        for (Direction f : sides = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            Vec3 onPlane;
            Util.FaceRect r;
            List<Util.FaceRect> rects = SurfaceLookup.collisionRects(p.getParticleWorld(), pos, base, f);
            if (rects.isEmpty() || (r = Util.pickBestRect(rects, f, hit)) == null || !BloodSurfaceAttach.isExposed(p, pos, f, onPlane = Util.clampToRectOnFacePlane(f, r, hit))) continue;
            double d = Util.distanceSqToRect(f, r, hit);
            if (best != null && !(d < best.distSq)) continue;
            best = new FacePick(f, r, d, onPlane);
        }
        if (best == null) {
            return false;
        }
        return BloodSurfaceAttach.attach(p, ParticleBlood.StickMode.COLLISION, pos, best.face, best.rect.plane, best.pointOnPlane);
    }

    private static final class FacePick {
        final Direction face;
        final Util.FaceRect rect;
        final double distSq;
        final Vec3 pointOnPlane;

        FacePick(Direction face, Util.FaceRect rect, double distSq, Vec3 pointOnPlane) {
            this.face = face;
            this.rect = rect;
            this.distSq = distSq;
            this.pointOnPlane = pointOnPlane;
        }
    }

    private static final class AttachSnapshot {
        final double posX;
        final double posY;
        final double posZ;
        final double prevX;
        final double prevY;
        final double prevZ;
        final float width;
        final float height;
        final int slimyBounceAnimStartAge;
        final int slimyBounceAnimTicks;
        final float slimyBounceAnimStrength;
        final double motionX;
        final double motionY;
        final double motionZ;
        final float gravity;
        final boolean canCollide;
        final boolean onGround;
        final int maxAge;
        final float scale;
        final float drip;
        final boolean noAirFlutter;
        final boolean ceilingEnabled;
        final boolean ceilingConsumed;
        final int ceilingNext;
        final int ceilingStart;
        final int ceilingBuild;
        final float ceilingTarget;
        final boolean fallingActive;
        final int fallingStart;
        final int fallingShrinkTicks;
        final float fallingStartLen;
        final float fallingLen;
        final int wallCooldown;
        final int detachWobble;
        final int slimySurfaceAnimStartAge;
        final int slimySurfaceAnimTicks;
        final int slimyGroundBounceCount;
        final int slimyGroundBounceMax;

        AttachSnapshot(ParticleBlood p) {
            this.posX = p.posX;
            this.posY = p.posY;
            this.posZ = p.posZ;
            this.prevX = p.prevPosX;
            this.prevY = p.prevPosY;
            this.prevZ = p.prevPosZ;
            AABB bb = p.getBoundingBox();
            this.width = (float)(bb.maxX - bb.minX);
            this.height = (float)(bb.maxY - bb.minY);
            this.motionX = p.motionX;
            this.motionY = p.motionY;
            this.motionZ = p.motionZ;
            this.gravity = p.getGravity();
            this.canCollide = p.getCanCollide();
            this.onGround = p.isOnGroundFlag();
            this.maxAge = p.getMaxAge();
            this.scale = p.getScale();
            this.drip = p.dripAmount;
            this.noAirFlutter = p.noAirFlutter;
            this.ceilingEnabled = p.ceilingDripEnabled;
            this.ceilingConsumed = p.ceilingDripConsumed;
            this.ceilingNext = p.ceilingNextDripAge;
            this.ceilingStart = p.ceilingDripStartAge;
            this.ceilingBuild = p.ceilingDripBuildTicks;
            this.ceilingTarget = p.ceilingDripTargetLen;
            this.fallingActive = p.fallingDripActive;
            this.fallingStart = p.fallingDripStartAge;
            this.fallingShrinkTicks = p.fallingDripShrinkTicks;
            this.fallingStartLen = p.fallingDripStartLen;
            this.fallingLen = p.fallingDripLen;
            this.wallCooldown = p.wallAttachCooldownTicks;
            this.detachWobble = p.detachWobbleTicks;
            this.slimySurfaceAnimStartAge = p.slimySurfaceAnimStartAge;
            this.slimySurfaceAnimTicks = p.slimySurfaceAnimTicks;
            this.slimyGroundBounceCount = p.slimyGroundBounceCount;
            this.slimyGroundBounceMax = p.slimyGroundBounceMax;
            this.slimyBounceAnimStartAge = p.slimyBounceAnimStartAge;
            this.slimyBounceAnimTicks = p.slimyBounceAnimTicks;
            this.slimyBounceAnimStrength = p.slimyBounceAnimStrength;
        }

        void restore(ParticleBlood p) {
            p.setSizeSafe(this.width, this.height);
            p.setPosition(this.posX, this.posY, this.posZ);
            p.prevPosX = this.prevX;
            p.prevPosY = this.prevY;
            p.prevPosZ = this.prevZ;
            p.motionX = this.motionX;
            p.motionY = this.motionY;
            p.motionZ = this.motionZ;
            p.setGravity(this.gravity);
            p.setCanCollide(this.canCollide);
            p.setOnGroundFlag(this.onGround);
            p.setMaxAge(this.maxAge);
            p.setScale(this.scale);
            p.dripAmount = this.drip;
            p.noAirFlutter = this.noAirFlutter;
            p.ceilingDripEnabled = this.ceilingEnabled;
            p.ceilingDripConsumed = this.ceilingConsumed;
            p.ceilingNextDripAge = this.ceilingNext;
            p.ceilingDripStartAge = this.ceilingStart;
            p.ceilingDripBuildTicks = this.ceilingBuild;
            p.ceilingDripTargetLen = this.ceilingTarget;
            p.fallingDripActive = this.fallingActive;
            p.fallingDripStartAge = this.fallingStart;
            p.fallingDripShrinkTicks = this.fallingShrinkTicks;
            p.fallingDripStartLen = this.fallingStartLen;
            p.fallingDripLen = this.fallingLen;
            p.detachWobbleTicks = this.detachWobble;
            p.slimySurfaceAnimStartAge = this.slimySurfaceAnimStartAge;
            p.slimySurfaceAnimTicks = this.slimySurfaceAnimTicks;
            p.slimyGroundBounceCount = this.slimyGroundBounceCount;
            p.slimyGroundBounceMax = this.slimyGroundBounceMax;
            p.slimyBounceAnimStartAge = this.slimyBounceAnimStartAge;
            p.slimyBounceAnimTicks = this.slimyBounceAnimTicks;
            p.slimyBounceAnimStrength = this.slimyBounceAnimStrength;
        }
    }
}

