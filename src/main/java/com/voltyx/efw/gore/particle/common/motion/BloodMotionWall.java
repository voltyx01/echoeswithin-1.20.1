package com.voltyx.efw.gore.particle.common.motion;

import com.voltyx.efw.gore.particle.common.GoreUtil;

import com.voltyx.efw.gore.ModConfigurationClient;
import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.blocksupport.DynamicPoseSupport;
import com.voltyx.efw.gore.particle.blocksupport.FenceGateSupport;
import com.voltyx.efw.gore.particle.common.BloodTuning;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.cache.BloodCachesParticle;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceAttach;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
public class BloodMotionWall {
    private static final double WALL_SLIDE_SPEED_JITTER = 0.25;
    private static final int CORNER_FADE_MAX_TICKS = 8;
    private static final double CORNER_RAY_UP = 0.02;
    private static final double CORNER_RAY_EXTRA_DOWN = 0.12;
    private static final double CORNER_EPS = 0.0025;

    public static void slide(ParticleBlood p) {
        double step;
        if (!BloodTuning.isWallFace(p.stuckFace)) {
            return;
        }
        if (p.getParticleWorld() == null || p.stuckPos == null || p.stuckLocalOnPlane == null) {
            return;
        }
        double baseSpeed = ModConfigurationClient.wall.slideSpeed;
        if (baseSpeed <= 0.0) {
            return;
        }
        double jitter = 0.25;
        double mul = 1.0;
        if (jitter > 0.0) {
            mul = 1.0 + (double)(p.dripSeed - 1.0f) * (4.0 * jitter);
            mul = Util.clamp(mul, 0.0, 3.0);
        }
        if ((step = baseSpeed * mul) <= 0.0) {
            return;
        }
        BloodMotionWall.checkCorner(p, step);
        Vec3 local = p.stuckLocalOnPlane;
        BlockPos pos = p.stuckPos;
        double ny = local.y - step;
        boolean changedBlock = false;
        while (ny < 0.0) {
            ny += 1.0;
            pos = pos.below();
            changedBlock = true;
        }
        while (ny >= 1.0) {
            ny -= 1.0;
            pos = pos.above();
            changedBlock = true;
        }
        p.stuckLocalOnPlane = new Vec3(local.x, ny, local.z);
        if (!changedBlock) {
            return;
        }
        if (!p.getParticleWorld().isLoaded(pos) || p.getParticleWorld().isEmptyBlock(pos)) {
            BloodSurfaceAttach.detach(p);
            return;
        }
        p.stuckPos = pos;
        BlockState eff = BloodSurfaceAttach.baseState(p);
        if (eff == null) {
            BloodSurfaceAttach.detach(p);
            return;
        }
        p.cache.host.base = eff;
        p.cache.host.piece = null;
        BloodCachesParticle.resetGate(p);
        BloodCachesParticle.resetSupport(p);
        FenceGateSupport.captureOnAttach(p);
        if (!FenceGateSupport.isGateState(p.cache.host.base)) {
            DynamicPoseSupport.captureOnAttach(p);
        } else {
            p.cache.host.piece = null;
        }
        BloodCachesParticle.invalidateView(p);
        BloodCachesParticle.invalidateShape(p);
    }

    private static void checkCorner(ParticleBlood p, double step) {
        double trigger;
        if (p == null || p.getParticleWorld() == null) {
            return;
        }
        if (!p.isStuck || !BloodTuning.isWallFace(p.stuckFace)) {
            return;
        }
        if (step <= 0.0) {
            return;
        }
        if (p.cache.fade.modelStartAge >= 0) {
            return;
        }
        double bottomY = Double.NaN;
        if (p.cache.shape.polys != null && !p.cache.shape.polys.isEmpty()) {
            double minY = Double.POSITIVE_INFINITY;
            for (List<Util.Vertex> poly : p.cache.shape.polys) {
                if (poly == null) continue;
                for (Util.Vertex v : poly) {
                    if (v == null || !(v.y < minY)) continue;
                    minY = v.y;
                }
            }
            if (minY != Double.POSITIVE_INFINITY) {
                double baseY = Double.isNaN(p.cache.view.y) ? p.posY : p.cache.view.y;
                double dy = p.posY - baseY;
                bottomY = minY + dy;
            }
        }
        if (Double.isNaN(bottomY)) {
            bottomY = p.posY - (double)(p.getScale() * 0.5f + p.dripAmount);
        }
        if (bottomY > p.posY) {
            bottomY = p.posY;
        }
        double needDown = step * 9.0 + 0.12;
        Vec3 start = new Vec3(p.posX, p.posY + 0.02, p.posZ);
        Vec3 end = new Vec3(p.posX, bottomY - needDown, p.posZ);
        BlockHitResult hit = GoreUtil.rayTrace(p.getParticleWorld(), start, end, false, true, true);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        if (hit.getDirection() != Direction.UP) {
            return;
        }
        if (hit.getLocation() == null) {
            return;
        }
        double dy = bottomY - hit.getLocation().y;
        if (dy < 0.0) {
            dy = 0.0;
        }
        if (dy > (trigger = step * 8.0 + 0.0025)) {
            return;
        }
        int ticks = (int)Math.ceil(dy / Math.max(1.0E-6, step));
        ticks = Util.clampInt(ticks, 1, 8);
        int left = Math.max(0, p.getMaxAge() - p.getAge());
        if (left > 0 && ticks > left) {
            ticks = left;
        }
        if (ticks < 1) {
            ticks = 1;
        }
        p.cache.fade.modelStartAge = p.getAge();
        p.cache.fade.modelTicks = ticks;
    }
}

