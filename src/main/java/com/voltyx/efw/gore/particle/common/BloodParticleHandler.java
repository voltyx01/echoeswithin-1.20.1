package com.voltyx.efw.gore.particle.common;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.blocksupport.DynamicPoseSupport;
import com.voltyx.efw.gore.particle.blocksupport.FenceGateSupport;
import com.voltyx.efw.gore.particle.blocksupport.PistonSupport;
import com.voltyx.efw.gore.particle.common.BloodTuning;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaCommon;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaParticle;
import com.voltyx.efw.gore.particle.common.amalgamation.BloodAmalgamationGround;
import com.voltyx.efw.gore.particle.common.amalgamation.BloodAmalgamationWall;
import com.voltyx.efw.gore.particle.common.cache.BloodCachesParticle;
import com.voltyx.efw.gore.particle.common.geometry.BloodGeometry;
import com.voltyx.efw.gore.particle.common.motion.BloodMotionHot;
import com.voltyx.efw.gore.particle.common.motion.BloodMotionParticle;
import com.voltyx.efw.gore.particle.common.motion.BloodMotionWall;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceAttach;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceCeiling;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceGround;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceWall;
import com.voltyx.efw.gore.particle.state.BloodHotBlocks;
import com.voltyx.efw.gore.particle.state.BloodMagic;
import com.voltyx.efw.gore.particle.state.BloodSlimy;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
public final class BloodParticleHandler {
    private static final int PISTON_RELOCATE_GRACE_TICKS = 4;
    private static final int RAIN_EXTRA_AGE_PER_TICK = 1;
    private static final double RAIN_SAMPLE_OUT = 0.05;
    private static final double RAIN_SAMPLE_UP = 0.02;

    public static void onUpdate(ParticleBlood p) {
        if (p == null) {
            return;
        }
        boolean magicIgnoresLiquids = BloodMagic.treatLiquidBlocksAsAir(p);
        p.syncToVanillaMotionAndPrev();
        BloodMotionParticle.prime(p);
        if (!(magicIgnoresLiquids || p.isExpiredSafe() || p.isStuck)) {
            if (p.isInsideLavaNow()) {
                p.convertToLavaParticle();
                return;
            }
            if (p.isInsideWaterNow()) {
                p.convertToWaterParticle();
                return;
            }
        }
        p.vanillaUpdate();
        if (p.isExpiredSafe()) {
            return;
        }
        if (!magicIgnoresLiquids && !p.isStuck) {
            if (p.isInsideLavaNow()) {
                p.convertToLavaParticle();
                return;
            }
            if (p.isInsideWaterNow()) {
                p.convertToWaterParticle();
                return;
            }
        }
        if (BloodParticleHandler.removeIfCoveredByOpaqueBlock(p)) {
            return;
        }
        BloodParticleHandler.applyRainAgeAdvanceIfNeeded(p);
        if (p.getAge() >= p.getMaxAge()) {
            BloodAlphaParticle.updateParticleAlpha(p);
            if (p.isExpiredSafe()) {
                return;
            }
        }
        if (p.wallAttachCooldownTicks > 0) {
            --p.wallAttachCooldownTicks;
        }
        if (p.hotBounceCooldownTicks > 0) {
            --p.hotBounceCooldownTicks;
        }
        BloodHotBlocks.tickCounters(p);
        if (p.isStuck) {
            BloodParticleHandler.tickStuck(p);
        } else {
            BloodMotionParticle.tick(p);
            BloodParticleHandler.tickFallingDrip(p);
            BloodSlimy.applyScaleForCurrentState(p);
            BloodParticleHandler.rebuildFreeDecalIfNeeded(p);
        }
        if (BloodParticleHandler.removeIfCoveredByOpaqueBlock(p)) {
            return;
        }
        BloodAlphaParticle.updateParticleAlpha(p);
        if (p.isExpiredSafe()) {
            return;
        }
        if (p.isStuck) {
            BloodSurfaceWall.postAlpha(p);
            BloodSurfaceCeiling.postAlpha(p);
        }
        p.syncToVanillaMotionAndPrev();
    }

    private static boolean removeIfCoveredByOpaqueBlock(ParticleBlood p) {
        BlockState state;
        if (p == null || p.isExpiredSafe()) {
            return false;
        }
        Level w = p.getParticleWorld();
        if (w == null) {
            return false;
        }
        Vec3 sample = BloodParticleHandler.coverSamplePos(p);
        if (sample == null) {
            return false;
        }
        BlockPos bp = BlockPos.containing(sample.x, sample.y, sample.z);
        if (!w.isLoaded(bp)) {
            return false;
        }
        try {
            state = w.getBlockState(bp);
        }
        catch (Throwable t) {
            return false;
        }
        if (!BloodParticleHandler.isOpaqueSolidCover(state)) {
            return false;
        }
        BloodAlphaCommon.expireNow(p);
        return true;
    }

    private static Vec3 coverSamplePos(ParticleBlood p) {
        if (p == null) {
            return null;
        }
        if (p.isStuck && p.stuckFace != null) {
            Vec3 onPlane = BloodSurfaceAttach.anchorPoint(p);
            return onPlane.add((double)p.stuckFace.getStepX() * 0.01, (double)p.stuckFace.getStepY() * 0.01, (double)p.stuckFace.getStepZ() * 0.01);
        }
        AABB bb = p.getBoundingBox();
        if (bb != null) {
            return new Vec3((bb.minX + bb.maxX) * 0.5, (bb.minY + bb.maxY) * 0.5, (bb.minZ + bb.maxZ) * 0.5);
        }
        return new Vec3(p.posX, p.posY, p.posZ);
    }

    private static boolean isOpaqueSolidCover(@Nullable BlockState state) {
        if (state == null) {
            return false;
        }
        try {
            if (!GoreMaterial.of(state).blocksMovement()) {
                return false;
            }
            if (!state.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
                return false;
            }
            return state.canOcclude();
        }
        catch (Throwable t) {
            return false;
        }
    }

    private static void applyRainAgeAdvanceIfNeeded(ParticleBlood p) {
        if (p == null) {
            return;
        }
        if (BloodMagic.isMagic(p)) {
            return;
        }
        if (!ParticleBlood.isLightLikeFluid(p.fluidWeight)) {
            return;
        }
        if (!BloodParticleHandler.isUnderRainNow(p)) {
            return;
        }
        p.addAgeTicks(1);
    }

    private static boolean isUnderRainNow(ParticleBlood p) {
        Level w = p.getParticleWorld();
        if (w == null || !w.isRaining()) {
            return false;
        }
        Vec3 sample = BloodParticleHandler.rainSamplePos(p);
        BlockPos rainPos = BlockPos.containing(sample.x, sample.y + 0.02, sample.z);
        if (!w.isLoaded(rainPos)) {
            return false;
        }
        try {
            return w.isRainingAt(rainPos);
        }
        catch (Throwable t) {
            return false;
        }
    }

    private static Vec3 rainSamplePos(ParticleBlood p) {
        if (p.isStuck && p.stuckFace != null) {
            Vec3 onPlane = BloodSurfaceAttach.anchorPoint(p);
            return onPlane.add((double)p.stuckFace.getStepX() * 0.05, (double)p.stuckFace.getStepY() * 0.05, (double)p.stuckFace.getStepZ() * 0.05);
        }
        return new Vec3(p.posX, p.posY, p.posZ);
    }

    private static void tickStuck(ParticleBlood p) {
        if (BloodMagic.isMagic(p)) {
            BloodSurfaceAttach.detach(p);
            return;
        }
        if (BloodMotionHot.tickGround(p)) {
            return;
        }
        p.motionX = 0.0;
        p.motionY = 0.0;
        p.motionZ = 0.0;
        p.setGravity(0.0f);
        if (p.getParticleWorld() == null || p.stuckPos == null || p.stuckFace == null) {
            BloodSurfaceAttach.detach(p);
            return;
        }
        if (!p.getParticleWorld().isLoaded(p.stuckPos)) {
            BloodAlphaCommon.expireNow(p);
            return;
        }
        BloodParticleHandler.updatePistonRelocateWindow(p);
        if (p.getParticleWorld().isEmptyBlock(p.stuckPos)) {
            if (!BloodParticleHandler.tryRelocatePistonMovedHost(p)) {
                BloodSurfaceAttach.detach(p);
                return;
            }
            if (!p.getParticleWorld().isLoaded(p.stuckPos) || p.getParticleWorld().isEmptyBlock(p.stuckPos)) {
                BloodSurfaceAttach.detach(p);
                return;
            }
        }
        BlockState prevBase = p.cache.host.base;
        BlockState nowEff = BloodSurfaceAttach.baseState(p);
        if (nowEff == null) {
            BloodSurfaceAttach.detach(p);
            return;
        }
        if (prevBase != null && !BloodSurfaceAttach.sameHost(prevBase, nowEff)) {
            if (!BloodParticleHandler.tryRelocatePistonMovedHost(p)) {
                BloodSurfaceAttach.detach(p);
                return;
            }
            nowEff = BloodSurfaceAttach.baseState(p);
            if (nowEff == null) {
                BloodSurfaceAttach.detach(p);
                return;
            }
        }
        p.cache.host.base = nowEff;
        if (prevBase != nowEff) {
            BloodCachesParticle.invalidateShape(p);
            BloodCachesParticle.invalidateView(p);
        }
        if (BloodTuning.isWallFace(p.stuckFace)) {
            BloodMotionWall.slide(p);
            if (!p.isStuck) {
                return;
            }
        }
        FenceGateSupport.tickGate(p);
        if (!FenceGateSupport.isGateState(p.cache.host.base)) {
            DynamicPoseSupport.tick(p);
            if (p.cache.host.piece == null) {
                BloodSurfaceAttach.detach(p);
                return;
            }
        }
        Vec3 onPlane = BloodSurfaceAttach.anchorPoint(p);
        if (p.stuckFace == Direction.UP && BloodAmalgamationGround.enabled() && BloodAmalgamationGround.tryMerge(p, onPlane)) {
            return;
        }
        BloodSurfaceWall.startWaterFade(p, onPlane);
        if (BloodTuning.isWallFace(p.stuckFace) && BloodAmalgamationWall.enabled() && BloodAmalgamationWall.tryMerge(p, onPlane)) {
            return;
        }
        BloodSurfaceAttach.moveStuck(p, onPlane);
        if (p.cache.host.poseSnapPrev) {
            p.prevPosX = p.posX;
            p.prevPosY = p.posY;
            p.prevPosZ = p.posZ;
            p.cache.host.poseSnapPrev = false;
        }
        p.stuckPlane = Util.planeCoord(p.stuckFace, onPlane);
        int oldSlimyGrowthWindow = BloodSlimy.pushRapidGrowthWindow(p);
        if (BloodTuning.isWallFace(p.stuckFace)) {
            BloodSurfaceWall.update(p);
            BloodSurfaceWall.updateDetach(p);
        } else {
            BloodSurfaceGround.update(p);
            if (p.stuckFace == Direction.DOWN) {
                BloodSurfaceCeiling.update(p);
            } else {
                p.dripAmount = 0.0f;
            }
        }
        BloodSlimy.restoreRapidGrowthWindow(p, oldSlimyGrowthWindow);
        BloodSlimy.applyScaleForCurrentState(p);
        if (BloodMotionHot.tickGround(p)) {
            return;
        }
        boolean geomDirty = BloodGeometry.needsGeometryRebuild(p);
        boolean polyDirty = BloodCachesParticle.needsShape(p);
        if (geomDirty || polyDirty) {
            BloodGeometry.rebuildDecalPolys(p);
            p.cache.shape.scale = p.getScale();
            p.cache.shape.drip = p.dripAmount;
            BloodGeometry.cacheRenderPos(p);
            p.cache.shape.amalgam = p.amalgamVisualMass;
            if ((p.cache.shape.polys == null || p.cache.shape.polys.isEmpty()) && p.cache.host.poseGraceTicks <= 0) {
                BloodSurfaceAttach.detach(p);
                return;
            }
        }
        if (BloodSurfaceWall.unsupported(p)) {
            return;
        }
        if (p.cache.host.poseGraceTicks > 0) {
            --p.cache.host.poseGraceTicks;
        }
        p.idleTicks = 0;
    }

    private static void tickFallingDrip(ParticleBlood p) {
        if (p == null) {
            return;
        }
        if (!p.fallingDripActive) {
            return;
        }
        int age = p.getAge();
        int elapsed = age - p.fallingDripStartAge;
        if (elapsed < 0) {
            elapsed = 0;
        }
        int dur = Math.max(1, p.fallingDripShrinkTicks);
        float t = Util.clamp01((float)elapsed / (float)dur);
        float e = Util.easeOutCubic01(t);
        p.fallingDripLen = p.fallingDripStartLen * (1.0f - e);
        if (t >= 0.999999f || p.fallingDripLen <= 1.0E-5f) {
            p.resetFallingDripRuntime();
            p.cache.shape.polys = null;
            BloodCachesParticle.invalidateShape(p);
            BloodCachesParticle.invalidateView(p);
        }
    }

    private static void rebuildFreeDecalIfNeeded(ParticleBlood p) {
        if (p == null) {
            return;
        }
        if (!p.fallingDripActive) {
            return;
        }
        boolean geomDirty = BloodGeometry.needsGeometryRebuild(p);
        boolean polyDirty = BloodCachesParticle.needsShape(p);
        if (geomDirty || polyDirty) {
            BloodGeometry.rebuildDecalPolys(p);
            p.cache.shape.scale = p.getScale();
            p.cache.shape.drip = p.dripAmount;
            BloodGeometry.cacheRenderPos(p);
        }
    }

    private static void updatePistonRelocateWindow(ParticleBlood p) {
        PistonSupport.MovingInfo mi;
        if (p.relocateGraceTicks > 0) {
            --p.relocateGraceTicks;
        }
        if ((mi = PistonSupport.getMovingInfo(p.getParticleWorld(), p.stuckPos, 1.0f, p)) != null && !mi.staticBaseNoOffset) {
            p.relocateGraceTicks = 4;
        }
    }

    private static boolean tryRelocatePistonMovedHost(ParticleBlood p) {
        if (p.relocateGraceTicks <= 0) {
            return false;
        }
        if (!BloodSurfaceAttach.tryRelocate(p)) {
            return false;
        }
        p.relocateGraceTicks = 0;
        BlockState eff = BloodSurfaceAttach.baseState(p);
        if (eff == null) {
            return false;
        }
        p.cache.host.base = eff;
        FenceGateSupport.captureOnAttach(p);
        if (!FenceGateSupport.isGateState(p.cache.host.base)) {
            DynamicPoseSupport.captureOnAttach(p);
        } else {
            p.cache.host.piece = null;
        }
        p.cache.host.poseSnapPrev = true;
        p.cache.host.poseGraceTicks = Math.max(p.cache.host.poseGraceTicks, 4);
        BloodCachesParticle.invalidateView(p);
        return true;
    }
}

