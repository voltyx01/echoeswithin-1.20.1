package com.voltyx.efw.gore.particle.common.motion;

import com.voltyx.efw.gore.particle.common.GoreUtil;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.motion.BloodMotion;
import com.voltyx.efw.gore.particle.common.motion.BloodMotionHeavy;
import com.voltyx.efw.gore.particle.common.motion.BloodMotionHot;
import com.voltyx.efw.gore.particle.common.motion.BloodMotionMagic;
import com.voltyx.efw.gore.particle.common.motion.BloodMotionSlimy;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceAttach;
import com.voltyx.efw.gore.particle.state.BloodMagic;
import com.voltyx.efw.gore.particle.state.BloodSlimy;
import java.util.ArrayList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
public class BloodMotionParticle {
    private static final int IDLE_TICKS_TO_FIX = 2;
    private static final double IDLE_MOVE_SQ = 1.0E-12;
    private static final double IDLE_SPEED_SQ_AIR = 1.0E-12;
    private static final double IDLE_SPEED_SQ_GROUND = 1.0E-8;
    private static final double KICK_PUSH_MIN = 0.07;
    private static final double KICK_PUSH_MAX = 0.14;
    private static final double KICK_JITTER = 0.02;
    private static final double KICK_Y = -0.07;
    private static final double KICK_NUDGE = 0.015;
    private static final double RAY_EXT = 0.01;

    public static void move(ParticleBlood p, double x, double y, double z) {
        boolean colZ;
        if (p.isStuck) {
            return;
        }
        double oldX = p.posX;
        double oldY = p.posY;
        double oldZ = p.posZ;
        Vec3 start = new Vec3(oldX, oldY, oldZ);
        double reqX = x;
        double reqY = y;
        double reqZ = z;
        Vec3 end = start.add(reqX, reqY, reqZ);
        BlockHitResult best = BloodMotionParticle.findHit(p, start, end);
        if (best != null && best.getType() == HitResult.Type.BLOCK && best.getDirection() != null) {
            boolean deferSlimyWallAttach = BloodSlimy.isSlimy(p) && best.getDirection().getAxis().isHorizontal() && Math.max(Math.abs(reqX), Math.abs(reqZ)) > 0.01;
            Vec3 hit = best.getLocation();
            if (!deferSlimyWallAttach && hit != null && BloodSurfaceAttach.tryAttach(p, best.getBlockPos(), best.getDirection(), start, end, hit)) {
                return;
            }
        }
        p.vanillaMove(reqX, reqY, reqZ);
        double dx = p.posX - oldX;
        double dy = p.posY - oldY;
        double dz = p.posZ - oldZ;
        double bx = Math.abs(reqX - dx);
        double by = Math.abs(reqY - dy);
        double bz = Math.abs(reqZ - dz);
        double eps = 1.0E-7;
        boolean colX = bx > 1.0E-7;
        boolean colY = by > 1.0E-7;
        boolean bl = colZ = bz > 1.0E-7;
        if (!(colX || colY || colZ)) {
            return;
        }
        if (BloodMotionHeavy.handleCollision(p, reqX, reqY, reqZ, colX, colY, colZ)) {
            return;
        }
        ArrayList<Direction> faces = new ArrayList<Direction>(3);
        if (colX) {
            faces.add(reqX > 0.0 ? Direction.WEST : Direction.EAST);
        }
        if (colZ) {
            faces.add(reqZ > 0.0 ? Direction.NORTH : Direction.SOUTH);
        }
        if (colY) {
            faces.add(reqY < 0.0 ? Direction.UP : Direction.DOWN);
        }
        for (Direction face : faces) {
            Vec3 at;
            BlockHitResult hit;
            boolean deferSlimyWallAttach = BloodSlimy.isSlimy(p) && face.getAxis().isHorizontal() && Math.max(Math.abs(reqX), Math.abs(reqZ)) > 0.01;
            if (deferSlimyWallAttach || (hit = BloodMotionParticle.rayFace(p, face, 0.8)) == null || hit.getType() != HitResult.Type.BLOCK || hit.getDirection() == null) continue;
            Vec3 vec3d = at = hit.getLocation() != null ? hit.getLocation() : new Vec3(p.posX, p.posY, p.posZ);
            if (!BloodSurfaceAttach.tryAttach(p, hit.getBlockPos(), hit.getDirection(), start, new Vec3(p.posX, p.posY, p.posZ), at)) continue;
            return;
        }
        if (BloodSlimy.isSlimy(p)) {
            BloodMotionSlimy.collide(p, reqX, reqY, reqZ, colX, colY, colZ);
        }
    }

    public static void prime(ParticleBlood p) {
        BloodMotionSlimy.prime(p);
        BloodMotionMagic.prime(p);
    }

    public static void tick(ParticleBlood p) {
        if (p == null) {
            return;
        }
        if (BloodMagic.isMagic(p)) {
            p.setCanCollide(true);
            BloodMotionMagic.tick(p);
            BloodMotionHot.tickBurst(p);
            BloodMotionHot.tickLift(p);
            return;
        }
        p.setCanCollide(true);
        BloodMotionParticle.tickIdle(p);
        BloodMotionHot.tickBurst(p);
    }

    static void tickIdle(ParticleBlood p) {
        boolean idle;
        BloodMotionParticle.tickWobble(p);
        BloodMotionParticle.tickFall(p);
        double speedSq = p.motionX * p.motionX + p.motionY * p.motionY + p.motionZ * p.motionZ;
        double dx = p.posX - p.prevPosX;
        double dy = p.posY - p.prevPosY;
        double dz = p.posZ - p.prevPosZ;
        double movedSq = dx * dx + dy * dy + dz * dz;
        double speedLimit = p.isOnGroundFlag() ? 1.0E-8 : 1.0E-12;
        boolean bl = idle = speedSq < speedLimit && movedSq < 1.0E-12;
        if (!idle) {
            p.idleTicks = 0;
            return;
        }
        ++p.idleTicks;
        if (p.idleTicks < 2) {
            return;
        }
        if (BloodMotionParticle.tryAttach(p)) {
            p.idleTicks = 0;
            return;
        }
        BloodMotion.BlockContact down = BloodMotionParticle.rayDown(p, 0.06, 0.9);
        if (BloodSlimy.isSlimy(p)) {
            Direction side = down != null ? BloodMotionParticle.pickPush(p, down.pos) : null;
            BloodMotionSlimy.idleKick(p, side);
        } else if (down != null) {
            Direction side = BloodMotionParticle.pickPush(p, down.pos);
            BloodMotionParticle.kick(p, side);
        } else {
            BloodMotionParticle.kick(p, null);
        }
        p.idleTicks = 0;
    }

    private static BlockHitResult rayFace(ParticleBlood p, Direction face, double dist) {
        if (face == null) {
            return null;
        }
        Vec3 start = new Vec3(p.posX, p.posY, p.posZ);
        Vec3 end = start.add((double)(-face.getStepX()) * dist, (double)(-face.getStepY()) * dist, (double)(-face.getStepZ()) * dist);
        return GoreUtil.rayTrace(p.getParticleWorld(), start, end, false, true, true);
    }

    private static BlockHitResult findHit(ParticleBlood p, Vec3 start, Vec3 end) {
        Vec3 d = end.subtract(start);
        double len = d.length();
        Vec3 endExt = end;
        if (len > 1.0E-9) {
            endExt = end.add(d.x / len * 0.01, d.y / len * 0.01, d.z / len * 0.01);
        }
        AABB bb = p.getBoundingBox();
        double hx = (bb.maxX - bb.minX) * 0.5;
        double hy = (bb.maxY - bb.minY) * 0.5;
        double hz = (bb.maxZ - bb.minZ) * 0.5;
        Vec3[] offs = new Vec3[]{new Vec3(0.0, 0.0, 0.0), new Vec3(hx, 0.0, hz), new Vec3(hx, 0.0, -hz), new Vec3(-hx, 0.0, hz), new Vec3(-hx, 0.0, -hz), new Vec3(0.0, hy * 0.5, 0.0), new Vec3(0.0, -hy * 0.5, 0.0)};
        BlockHitResult best = null;
        double bestSq = Double.POSITIVE_INFINITY;
        for (Vec3 off : offs) {
            double distSq;
            Vec3 s = start.add(off);
            Vec3 e = endExt.add(off);
            BlockHitResult hit = GoreUtil.rayTrace(p.getParticleWorld(), s, e, false, true, true);
            if (hit == null || hit.getType() != HitResult.Type.BLOCK || hit.getLocation() == null || !((distSq = hit.getLocation().distanceToSqr(s)) < bestSq)) continue;
            bestSq = distSq;
            best = hit;
        }
        return best;
    }

    private static void tickWobble(ParticleBlood p) {
        if (p.detachWobbleTicks <= 0) {
            return;
        }
        if (p.isOnGroundFlag()) {
            p.detachWobbleTicks = 0;
            return;
        }
        double k = Util.clamp((double)p.detachWobbleTicks / 20.0, 0.0, 1.0);
        double t = ((double)p.getAge() + (double)(p.dripSeed * 20.0f)) * 0.35 + (double)p.groundRot;
        double amp = 0.00135 * k;
        p.motionX += Math.cos(t) * amp;
        p.motionZ += Math.sin(t) * amp;
        double t2 = t * 1.7 + 1.0;
        p.motionX += Math.cos(t2) * (amp * 0.3);
        p.motionZ += Math.sin(t2) * (amp * 0.3);
        p.motionX *= 0.975;
        p.motionZ *= 0.975;
        double hClamp = BloodMotionHeavy.wobbleClamp(p);
        BloodMotion.clampHorizontal(p, hClamp);
        --p.detachWobbleTicks;
    }

    private static void tickFall(ParticleBlood p) {
        if (p == null) {
            return;
        }
        if (p.isOnGroundFlag()) {
            return;
        }
        if (p.motionY >= 0.0) {
            return;
        }
        if (BloodMotionHeavy.airFall(p)) {
            return;
        }
        if (BloodMotionSlimy.tickAir(p)) {
            return;
        }
        if (p.noAirFlutter) {
            return;
        }
    }

    private static BloodMotion.BlockContact rayDown(ParticleBlood p, double up, double down) {
        Vec3 start = new Vec3(p.posX, p.posY + up, p.posZ);
        Vec3 end = new Vec3(p.posX, p.posY - down, p.posZ);
        BlockHitResult hit = GoreUtil.rayTrace(p.getParticleWorld(), start, end, false, true, true);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || hit.getDirection() == null) {
            return null;
        }
        Vec3 at = hit.getLocation() != null ? hit.getLocation() : end;
        return new BloodMotion.BlockContact(hit.getBlockPos(), hit.getDirection(), at);
    }

    private static Direction pickPush(ParticleBlood p, BlockPos pos) {
        if (pos == null) {
            return null;
        }
        double cx = (double)((net.minecraft.core.Vec3i) pos).getX() + 0.5;
        double cz = (double)((net.minecraft.core.Vec3i) pos).getZ() + 0.5;
        double vx = p.posX - cx;
        double vz = p.posZ - cz;
        if (Math.abs(vx) < 1.0E-6 && Math.abs(vz) < 1.0E-6) {
            return null;
        }
        if (Math.abs(vx) >= Math.abs(vz)) {
            return vx >= 0.0 ? Direction.EAST : Direction.WEST;
        }
        return vz >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static void kick(ParticleBlood p, Direction side) {
        double vx = 0.0;
        double vz = 0.0;
        if (side != null) {
            vx = side.getStepX();
            vz = side.getStepZ();
        }
        if (Math.abs(vx) < 1.0E-6 && Math.abs(vz) < 1.0E-6) {
            vx = p.getRand().nextDouble() - 0.5;
            double len = Math.sqrt(vx * vx + (vz = p.getRand().nextDouble() - 0.5) * vz);
            if (len < 1.0E-6) {
                vx = 1.0;
                vz = 0.0;
                len = 1.0;
            }
            vx /= len;
            vz /= len;
        }
        double push = 0.07 + p.getRand().nextDouble() * 0.07;
        p.motionX = vx * push + (p.getRand().nextDouble() - 0.5) * 0.02;
        p.motionZ = vz * push + (p.getRand().nextDouble() - 0.5) * 0.02;
        p.motionY = -0.07;
        p.setOnGroundFlag(false);
        p.setPositionSafe(p.posX + vx * 0.015, p.posY, p.posZ + vz * 0.015);
        p.prevPosX = p.posX;
        p.prevPosY = p.posY;
        p.prevPosZ = p.posZ;
    }

    private static boolean tryAttach(ParticleBlood p) {
        Vec3[] ends;
        Vec3 start = new Vec3(p.posX, p.posY + 0.02, p.posZ);
        for (Vec3 end : ends = new Vec3[]{start.add(0.0, -0.65, 0.0), start.add(0.0, 0.35, 0.0), start.add(0.45, 0.0, 0.0), start.add(-0.45, 0.0, 0.0), start.add(0.0, 0.0, 0.45), start.add(0.0, 0.0, -0.45)}) {
            Vec3 at;
            BlockHitResult hit = GoreUtil.rayTrace(p.getParticleWorld(), start, end, false, true, true);
            if (hit == null || hit.getType() != HitResult.Type.BLOCK || hit.getDirection() == null) continue;
            Vec3 vec3d = at = hit.getLocation() != null ? hit.getLocation() : end;
            if (!BloodSurfaceAttach.tryAttach(p, hit.getBlockPos(), hit.getDirection(), start, end, at)) continue;
            return true;
        }
        return false;
    }
}

