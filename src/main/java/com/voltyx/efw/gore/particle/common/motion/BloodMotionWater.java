package com.voltyx.efw.gore.particle.common.motion;

import com.voltyx.efw.gore.particle.ClientSprites;
import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.BloodTuning;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaWater;
import com.voltyx.efw.gore.particle.common.motion.BloodMotionHeavy;
import com.voltyx.efw.gore.particle.state.liquid.BloodLiquidUtil;
import com.voltyx.efw.gore.particle.state.liquid.water.BloodWater;
import com.voltyx.efw.gore.particle.state.liquid.water.BloodWaterCache;
import net.minecraft.world.level.block.LiquidBlock;
import com.voltyx.efw.gore.particle.common.GoreMaterial;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import com.voltyx.efw.gore.particle.LegacyParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
public class BloodMotionWater {
    private static final double FLOW_TARGET_SPEED_IN_WATER = 0.025;
    private static final double FLOW_TARGET_SPEED_ON_SURFACE = 0.03;
    private static final double MAX_H_SPEED_IN_WATER = 0.14;
    private static final double MAX_H_SPEED_ON_SURFACE_MOVING = 0.13;
    private static final double ENTER_SURFACE_BRAKE_MOVING = 0.99;
    private static final double ENTER_SURFACE_BRAKE_STILL = 0.97;
    private static final double FLOW_STEER = 0.15;
    private static final double FLOW_LANE_SIDE_SPEED_BASE = 0.00135;
    private static final double FLOW_LANE_SIDE_SPEED_SWAY = 5.5E-4;
    private static final double FLOW_LANE_SIDE_STEER = 0.18;
    private static final float FLOW_LANE_SWAY_FREQ = 0.05f;
    private static final double EXIT_MIN_ALONG_SPEED = 0.145;
    private static final double EXIT_EXTRA_ALONG = 0.055;
    private static final int EXIT_COAST_TICKS_MIN = 26;
    private static final int EXIT_COAST_TICKS_MAX = 44;
    private static final double EXIT_KICK_MUL_MIN = 0.92;
    private static final double EXIT_KICK_MUL_MAX = 1.1;
    private static final double EXIT_KICK_ANGLE_MAX_RAD = 0.07;
    private static final double STILL_SURFACE_SPEED_CAP_BASE = 0.18;
    private static final double STILL_CAP_MUL_MIN = 0.92;
    private static final double STILL_CAP_MUL_MAX = 1.08;
    private static final double ICE_DRAG_SPEED_THRESHOLD = 0.018;
    private static final double ICE_THRESH_MUL_MIN = 0.85;
    private static final double ICE_THRESH_MUL_MAX = 1.18;
    private static final double GLIDE_SPEED_FADE_LO_MUL = 0.4;
    private static final double GLIDE_SPEED_FADE_HI_MUL = 1.8;
    private static final float STILL_WOBBLE_FREQ = 0.25f;
    private static final double STILL_WOBBLE_MAX_RAD = 0.038;
    private static final double STILL_WOBBLE_SPEED_REF = 0.085;
    private static final double STILL_WOBBLE_SIDE_ACCEL = 0.00135;
    private static final float ICE_DRAG_NOISE_FREQ_BASE = 0.12f;
    private static final double DRAG_ICE_BASE_MIN = 0.986;
    private static final double DRAG_ICE_BASE_MAX = 0.9952;
    private static final double DRAG_ICE_TIME_JITTER = 0.014;
    private static final double DRAG_ICE_CLAMP_MIN = 0.978;
    private static final double DRAG_ICE_CLAMP_MAX = 0.9968;
    private static final float STILL_DRAG_NOISE_FREQ_BASE = 0.07f;
    private static final double DRAG_STILL_BASE_MIN = 0.82;
    private static final double DRAG_STILL_BASE_MAX = 0.9;
    private static final double DRAG_STILL_TIME_JITTER = 0.05;
    private static final double DRAG_STILL_CLAMP_MIN = 0.79;
    private static final double DRAG_STILL_CLAMP_MAX = 0.91;
    private static final float STILL_DRIFT_NOISE_FREQ = 0.09f;
    private static final double STILL_DRIFT_MUL_MIN = 1.6;
    private static final double STILL_DRIFT_MUL_MAX = 3.2;
    private static final double STILL_DRIFT_TIME_JITTER = 0.3;
    private static final int LIGHT_SURFACE_PROBE_UP = 4;

    public static void tick(BloodWater p) {
        boolean sink;
        Vec3 flow;
        boolean wasOnSurface = p.isOnSurface();
        boolean wasOnFloor = p.isOnFloor();
        p.copyPosToPrev();
        p.setPrevSurfaceRot(p.getSurfaceRot());
        p.setPrevParticleAngle(p.getParticleAngle());
        if (p.incrementAgeAndShouldExpire()) {
            p.expireAndUntrack();
            return;
        }
        Level world = p.getParticleWorld();
        if (world == null) {
            p.expireAndUntrack();
            return;
        }
        if (!BloodMotionWater.inWater(p)) {
            if (BloodMotionWater.fixWetSolid(p)) {
                return;
            }
            BloodMotionWater.toBlood(p);
            return;
        }
        BloodMotionWater.spin(p);
        p.incrementWaterTicks();
        if (!BloodAlphaWater.updateWaterAlpha(p)) {
            return;
        }
        BloodWaterCache cache = p.getCache();
        if (p.isHeavyInWater()) {
            cache.tickAndUpdate(p.getPosX(), p.getPosY(), p.getPosZ());
            BloodMotionHeavy.tickWater(p, cache, wasOnFloor);
            return;
        }
        BlockPos flowPos = BloodMotionWater.flowPos(p, world);
        boolean moving = flowPos != null && BloodMotionWater.isFlow(world, flowPos);
        Vec3 vec3d = flow = moving ? BloodMotionWater.flowVec(world, flowPos) : Vec3.ZERO;
        if (moving && p.getExitCoastTicks() > 0) {
            p.setExitCoastTicks(0);
        }
        boolean bl = sink = p.getWaterTicks() <= 10;
        if (!BloodMotionWater.needSurface(p, world, wasOnSurface)) {
            BloodMotionWater.clearDecals(p);
            cache.invalidate();
            BloodMotionWater.tickBody(p, sink, flow);
            return;
        }
        cache.tickAndUpdate(p.getPosX(), p.getPosY(), p.getPosZ());
        if (!cache.hasSurface()) {
            BloodMotionWater.clearDecals(p);
            BloodMotionWater.tickBody(p, sink, flow);
            return;
        }
        BloodWaterCache.SurfaceSample surf0 = cache.sampleAt(p.getPosX(), p.getPosZ());
        if (surf0 == null) {
            BloodMotionWater.clearDecals(p);
            BloodMotionWater.tickBody(p, sink, flow);
            return;
        }
        double surfY0 = surf0.y;
        BlockPos top0 = cache.getCachedTop();
        double blockY0 = top0 != null ? (double)((net.minecraft.core.Vec3i) top0).getY() : (double)Mth.floor((double)surfY0);
        double minY0 = blockY0 + p.getCollHalfY() + 0.0015;
        double contactY0 = surfY0 - p.getCollHalfY();
        if (contactY0 < minY0) {
            contactY0 = minY0;
        }
        if (sink) {
            boolean floor;
            double cap;
            BloodMotionWater.clearDecals(p);
            p.setMotionX(p.getMotionX() * 0.86);
            p.setMotionZ(p.getMotionZ() * 0.86);
            p.setMotionY(p.getMotionY() * 0.9);
            BloodMotionWater.flowDrive(p, flow, 0.025, 0.14);
            p.setMotionY(p.getMotionY() - 0.0042);
            if (p.getMotionY() < -0.045) {
                p.setMotionY(-0.045);
            }
            if ((cap = contactY0 - 0.0075) < minY0) {
                cap = minY0;
            }
            if (p.getPosY() + p.getMotionY() > cap) {
                p.setMotionY(cap - p.getPosY());
            }
            if (floor = BloodMotionWater.moveSolid(p, p.getMotionX(), p.getMotionY(), p.getMotionZ())) {
                p.setWaterTicks(11);
            }
            return;
        }
        if (p.getPosY() < contactY0 - 0.006) {
            double cap;
            BloodMotionWater.clearDecals(p);
            p.setMotionX(p.getMotionX() * 0.86);
            p.setMotionZ(p.getMotionZ() * 0.86);
            p.setMotionY(p.getMotionY() * 0.9);
            BloodMotionWater.flowDrive(p, flow, 0.025, 0.14);
            p.setMotionY(p.getMotionY() + 0.01);
            if (p.getMotionY() > 0.1) {
                p.setMotionY(0.1);
            }
            if ((cap = contactY0 - 0.0015) < minY0) {
                cap = minY0;
            }
            if (p.getPosY() + p.getMotionY() > cap) {
                p.setMotionY(cap - p.getPosY());
            }
            BloodMotionWater.moveSolid(p, p.getMotionX(), p.getMotionY(), p.getMotionZ());
            return;
        }
        if (cache.isCachedAboveOpen()) {
            double dy;
            BloodWaterCache.SurfaceSample surf1;
            double capH;
            double vz;
            double vx;
            double sp;
            double glide1;
            int coast;
            BlockPos dst;
            double drag;
            boolean force;
            p.setOnSurface(true);
            if (p.getSurfaceGrowStartAge() < 0) {
                p.setSurfaceGrowStartAge(p.getAge());
                p.setSurfaceScale(p.getBaseScale());
            }
            if (!wasOnSurface) {
                double brake = moving ? 0.99 : 0.97;
                p.setMotionX(p.getMotionX() * brake);
                p.setMotionZ(p.getMotionZ() * brake);
            }
            boolean still = !moving;
            double capStill = BloodMotionWater.stillCap(p);
            double threshIce = 0.018 * BloodMotionWater.iceMul(p);
            int maxCoast = BloodMotionWater.coastMax(p);
            double coast01 = maxCoast > 0 ? BloodMotionWater.clamp((double)p.getExitCoastTicks() / (double)maxCoast, 0.0, 1.0) : 0.0;
            double coastEase = coast01 > 0.0 ? Math.sqrt(coast01) : 0.0;
            double preX = p.getMotionX();
            double preZ = p.getMotionZ();
            double preSq = preX * preX + preZ * preZ;
            double preSp = Math.sqrt(Math.max(0.0, preSq));
            double speed01 = BloodMotionWater.clamp(preSp / 0.085, 0.0, 1.0);
            double preGlide = still ? BloodMotionWater.glide(preSp, threshIce) : 0.0;
            double glidePre = still ? Math.max(coastEase, preGlide) : 0.0;
            boolean bl2 = force = moving || glidePre > 0.001 || preSq > 1.0E-7;
            if (force && !moving) {
                double drift = BloodMotionWater.stillDrift(p, speed01, glidePre);
                BloodMotionWater.surfaceDrift(p, drift);
            }
            p.setMotionY(0.0);
            double vx0 = p.getMotionX();
            double vz0 = p.getMotionZ();
            double hsSq0 = vx0 * vx0 + vz0 * vz0;
            double sp0 = Math.sqrt(Math.max(0.0, hsSq0));
            double glide0 = still ? BloodMotionWater.glide(sp0, threshIce) : 0.0;
            double glide01 = still ? Math.max(coastEase, glide0) : 0.0;
            glide01 = BloodMotionWater.clamp(glide01, 0.0, 1.0);
            if (moving) {
                drag = 0.86;
            } else {
                double ice = BloodMotionWater.iceDrag(p, sp0, glide01);
                double stillDrag = BloodMotionWater.stillDrag(p, sp0);
                drag = BloodMotionWater.lerp(stillDrag, ice, glide01);
            }
            p.setMotionX(vx0 * drag);
            p.setMotionZ(vz0 * drag);
            if (moving) {
                BloodMotionWater.flowDrive(p, flow, 0.03, 0.13);
                BloodMotionWater.flowLane(p, flow);
            }
            boolean exit = false;
            double testX = p.getPosX() + p.getMotionX();
            double testZ = p.getPosZ() + p.getMotionZ();
            if (moving && p.getExitCoastTicks() <= 0 && (dst = BloodMotionWater.flowPosAt(world, testX, p.getPosY(), testZ)) != null && !BloodMotionWater.isFlow(world, dst)) {
                int coast2 = BloodMotionWater.coastMax(p);
                p.setExitCoastTicks(coast2);
                double mul = BloodMotionWater.kickMul(p);
                Vec3 dir = BloodMotionWater.exitDir(p, flow);
                double ang = BloodMotionWater.kickAngle(p);
                dir = BloodMotionWater.rotateXZ(dir, ang);
                BloodMotionWater.exitKick(p, dir, 0.145 * mul, 0.055 * mul, capStill);
                exit = true;
            }
            if (!moving && (coast = p.getExitCoastTicks()) > 0) {
                if ((coast -= BloodMotionWater.coastStep(p)) < 0) {
                    coast = 0;
                }
                p.setExitCoastTicks(coast);
            }
            if (!moving && (glide1 = Math.max(coastEase, BloodMotionWater.glide(sp = Math.sqrt(Math.max(0.0, (vx = p.getMotionX()) * vx + (vz = p.getMotionZ()) * vz)), threshIce))) > 0.001) {
                BloodMotionWater.stillWobble(p, glide1);
            }
            double d = capH = moving ? 0.13 : capStill;
            if (exit) {
                capH = capStill;
            }
            BloodMotionWater.clampH(p, capH);
            double wantX = p.getMotionX();
            double wantZ = p.getMotionZ();
            BloodMotionWater.moveSolid(p, wantX, 0.0, wantZ);
            double surfX = p.getPosX();
            double surfZ = p.getPosZ();
            BlockPos top = cache.getCachedTop();
            if (top != null) {
                int bx = Mth.floor((double)surfX);
                int bz = Mth.floor((double)surfZ);
                if (bx != ((net.minecraft.core.Vec3i) top).getX() || bz != ((net.minecraft.core.Vec3i) top).getZ()) {
                    cache.forceScanAt(surfX, p.getPosY(), surfZ);
                }
            }
            double surfY1 = (surf1 = cache.sampleAt(surfX, surfZ)) != null ? surf1.y : surfY0;
            BlockPos top1 = cache.getCachedTop();
            double blockY1 = top1 != null ? (double)((net.minecraft.core.Vec3i) top1).getY() : (double)Mth.floor((double)surfY1);
            double minY1 = blockY1 + p.getCollHalfY() + 0.0015;
            double lockY = surfY1 - p.getCollHalfY() - 0.0015;
            if (lockY < minY1) {
                lockY = minY1;
            }
            if (Math.abs(dy = lockY - p.getPosY()) > 1.0E-10) {
                BloodMotionWater.moveSolid(p, 0.0, dy, 0.0);
            }
            BloodMotionWater.scaleSurface(p);
            return;
        }
        BloodMotionWater.clearDecals(p);
        double clampedY = Math.min(p.getPosY(), surfY0);
        if (clampedY != p.getPosY()) {
            p.setParticlePos(p.getPosX(), clampedY, p.getPosZ());
        }
        p.setMotionX(p.getMotionX() * 0.86);
        p.setMotionZ(p.getMotionZ() * 0.86);
        p.setMotionY(0.0);
        BloodMotionWater.flowDrive(p, flow, 0.025, 0.14);
        BloodMotionWater.moveSolid(p, p.getMotionX(), 0.0, p.getMotionZ());
    }

    private static void clearDecals(BloodWater p) {
        if (p.isOnSurface()) {
            p.setOnSurface(false);
        }
        if (p.isOnFloor()) {
            p.setOnFloor(false);
        }
    }

    private static boolean needSurface(BloodWater p, Level w, boolean wasOnSurface) {
        if (wasOnSurface || p.isOnSurface()) {
            return true;
        }
        if (p.getExitCoastTicks() > 0) {
            return true;
        }
        if (p.getWaterTicks() <= 10) {
            return false;
        }
        return BloodMotionWater.nearSurface(w, p.getPosX(), p.getPosY() + p.getCollHalfY(), p.getPosZ(), 4);
    }

    private static boolean nearSurface(Level w, double x, double y, double z, int maxUp) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        pos.set(Mth.floor((double)x), Mth.floor((double)y), Mth.floor((double)z));
        for (int i = 0; i <= maxUp; ++i) {
            if (!w.isLoaded((BlockPos)pos)) {
                return true;
            }
            BlockState st = BloodMotionWater.state(w, (BlockPos)pos);
            if (st == null || GoreMaterial.of(st) != GoreMaterial.WATER) {
                return true;
            }
            pos.move(Direction.UP);
        }
        return false;
    }

    private static double glide(double speed, double iceThresh) {
        if (iceThresh <= 1.0E-9) {
            return 0.0;
        }
        double hi = iceThresh * 1.8;
        double lo = iceThresh * 0.4;
        if (hi <= lo + 1.0E-9) {
            return speed > iceThresh ? 1.0 : 0.0;
        }
        double t = (speed - lo) / (hi - lo);
        t = BloodMotionWater.clamp(t, 0.0, 1.0);
        return BloodMotionWater.smooth(t);
    }

    private static double smooth(double t) {
        t = BloodMotionWater.clamp(t, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static void flowDrive(BloodWater p, Vec3 flow, double minSpeed, double maxSpeed) {
        double vz;
        if (flow == null) {
            return;
        }
        double dx = flow.x;
        double dz = flow.z;
        double lenSq = dx * dx + dz * dz;
        if (lenSq <= 1.0E-12) {
            return;
        }
        double inv = 1.0 / Math.sqrt(lenSq);
        dx *= inv;
        dz *= inv;
        double vx = p.getMotionX();
        double along = vx * dx + (vz = p.getMotionZ()) * dz;
        if (along < minSpeed) {
            double add = minSpeed - along;
            vx += dx * add;
            vz += dz * add;
        }
        along = vx * dx + vz * dz;
        double sideX = vx - along * dx;
        double sideZ = vz - along * dz;
        p.setMotionX(vx -= sideX * 0.15);
        p.setMotionZ(vz -= sideZ * 0.15);
        if (maxSpeed > 0.0) {
            BloodMotionWater.clampH(p, maxSpeed);
        }
    }

    private static void flowLane(BloodWater p, Vec3 flow) {
        double vz;
        if (flow == null) {
            return;
        }
        double dirX = flow.x;
        double dirZ = flow.z;
        double lenSq = dirX * dirX + dirZ * dirZ;
        if (lenSq <= 1.0E-12) {
            return;
        }
        double sideX = -dirZ;
        double sideZ = dirX;
        double vx = p.getMotionX();
        double along = vx * dirX + (vz = p.getMotionZ()) * dirZ;
        if (along <= 1.0E-6) {
            return;
        }
        double side = vx * sideX + vz * sideZ;
        double sign = BloodMotionWater.rand01(p, 1401) < 0.5 ? -1.0 : 1.0;
        float phase = (float)(BloodMotionWater.rand01(p, 1417) * (Math.PI * 2));
        float t = ((float)p.getAge() + p.getDriftSeed() * 200.0f) * 0.05f + phase;
        double sway = Mth.sin((float)t);
        double along01 = BloodMotionWater.clamp(along / 0.03, 0.0, 1.0);
        double want = sign * (0.00135 + 5.5E-4 * sway) * (0.55 + 0.45 * along01);
        double add = (want - side) * 0.18;
        p.setMotionX(vx + sideX * add);
        p.setMotionZ(vz + sideZ * add);
    }

    private static double iceDrag(BloodWater p, double speed, double glide01) {
        double base = BloodMotionWater.lerp(0.986, 0.9952, BloodMotionWater.rand01(p, 61));
        float freqMul = (float)(0.8 + 0.6 * BloodMotionWater.rand01(p, 71));
        float t = ((float)p.getAge() + p.getDriftSeed() * 200.0f) * (0.12f * freqMul);
        int seed = BloodMotionWater.hash(p, 41027);
        float n1 = BloodLiquidUtil.noiseFbm(t, 7.1f + p.getDriftSeed() * 4.7f, seed);
        float n2 = BloodLiquidUtil.noiseFbm(t * 0.41f + 13.0f, 3.9f + p.getDriftSeed() * 8.2f, seed + 1013);
        float n = 0.62f * n1 + 0.38f * n2;
        double speed01 = BloodMotionWater.clamp(speed / 0.085, 0.0, 1.0);
        double amp = 0.014 * (0.3 + 0.7 * speed01) * (0.2 + 0.8 * glide01);
        double bias = BloodMotionWater.lerp(-0.0045, 0.0045, BloodMotionWater.rand01(p, 83));
        double drag = base + (double)n * amp + bias;
        return BloodMotionWater.clamp(drag, 0.978, 0.9968);
    }

    private static double stillDrag(BloodWater p, double speed) {
        double base = BloodMotionWater.lerp(0.82, 0.9, BloodMotionWater.rand01(p, 101));
        float freqMul = (float)(0.8 + 0.6 * BloodMotionWater.rand01(p, 111));
        float t = ((float)p.getAge() + p.getDriftSeed() * 200.0f) * (0.07f * freqMul);
        int seed = BloodMotionWater.hash(p, 52091);
        float n = BloodLiquidUtil.noiseFbm(t, 2.5f + p.getDriftSeed() * 9.0f, seed);
        double speed01 = BloodMotionWater.clamp(speed / 0.085, 0.0, 1.0);
        double amp = 0.05 * (0.2 + 0.8 * speed01);
        double bias = BloodMotionWater.lerp(-0.01, 0.01, BloodMotionWater.rand01(p, 131));
        double drag = base + (double)n * amp + bias;
        return BloodMotionWater.clamp(drag, 0.79, 0.91);
    }

    private static void stillWobble(BloodWater p, double glide01) {
        double vz;
        double vx = p.getMotionX();
        double spSq = vx * vx + (vz = p.getMotionZ()) * vz;
        if (spSq <= 1.0E-12) {
            return;
        }
        double sp = Math.sqrt(spSq);
        if (sp <= 1.0E-6) {
            return;
        }
        double speed01 = BloodMotionWater.clamp(sp / 0.085, 0.0, 1.0);
        double wobMul = BloodMotionWater.lerp(0.9, 1.15, BloodMotionWater.rand01(p, 911));
        float t = ((float)p.getAge() + p.getDriftSeed() * 200.0f) * 0.25f;
        int seed = BloodMotionWater.hash(p, 12013);
        float nFast = BloodLiquidUtil.noiseFbm(t, 1.7f + p.getDriftSeed() * 3.1f, seed);
        float nSlow = BloodLiquidUtil.noiseFbm(t * 0.33f + 9.7f, 5.9f + p.getDriftSeed() * 2.2f, seed + 777);
        float nSide = BloodLiquidUtil.noiseFbm(t * 0.71f + 19.7f, 4.3f + p.getDriftSeed() * 2.0f, seed + 1013);
        double strength = wobMul * glide01 * speed01;
        double blend = 0.62 * (double)nFast + 0.38 * (double)nSlow;
        double theta = blend * 0.038 * strength;
        double ct = Math.cos(theta);
        double st = Math.sin(theta);
        double rx = vx * ct - vz * st;
        double rz = vx * st + vz * ct;
        double invSp = 1.0 / sp;
        double perpX = -rz * invSp;
        double perpZ = rx * invSp;
        double side = (double)nSide * 0.00135 * strength;
        p.setMotionX(rx + perpX * side);
        p.setMotionZ(rz + perpZ * side);
    }

    private static double stillDrift(BloodWater p, double speed01, double glide01) {
        double base = BloodMotionWater.lerp(1.6, 3.2, BloodMotionWater.rand01(p, 201));
        float t = ((float)p.getAge() + p.getDriftSeed() * 200.0f) * 0.09f;
        int seed = BloodMotionWater.hash(p, 33011);
        float n = BloodLiquidUtil.noiseFbm(t, 6.2f + p.getDriftSeed() * 3.3f, seed);
        double jitter = 1.0 + (double)n * 0.3;
        double k = (0.4 + 0.6 * speed01) * (0.25 + 0.75 * glide01);
        return Math.max(0.0, base * jitter * k);
    }

    private static void exitKick(BloodWater p, Vec3 dir, double minSpeed, double extra, double cap) {
        double vz;
        double dx = dir.x;
        double dz = dir.z;
        double lenSq = dx * dx + dz * dz;
        if (lenSq <= 1.0E-12) {
            return;
        }
        double inv = 1.0 / Math.sqrt(lenSq);
        dx *= inv;
        dz *= inv;
        double vx = p.getMotionX();
        double along = vx * dx + (vz = p.getMotionZ()) * dz;
        double need = minSpeed - along;
        if (need < 0.0) {
            need = 0.0;
        }
        p.setMotionX(vx + dx * (need += extra));
        p.setMotionZ(vz + dz * need);
        if (cap > 0.0) {
            BloodMotionWater.clampH(p, cap);
        }
    }

    private static Vec3 exitDir(BloodWater p, Vec3 want) {
        double vz;
        double pz;
        double px;
        if (want != null && (px = want.x) * px + (pz = want.z) * pz > 1.0E-10) {
            return new Vec3(px, 0.0, pz);
        }
        double vx = p.getMotionX();
        if (vx * vx + (vz = p.getMotionZ()) * vz > 1.0E-10) {
            return new Vec3(vx, 0.0, vz);
        }
        double ang = (double)p.getGroundRot() + (double)p.getDriftSeed() * (Math.PI * 2);
        return new Vec3(Math.cos(ang), 0.0, Math.sin(ang));
    }

    private static Vec3 rotateXZ(Vec3 dir, double ang) {
        double x = dir.x;
        double z = dir.z;
        double c = Math.cos(ang);
        double s = Math.sin(ang);
        return new Vec3(x * c - z * s, 0.0, x * s + z * c);
    }

    private static double stillCap(BloodWater p) {
        return 0.18 * BloodMotionWater.lerp(0.92, 1.08, BloodMotionWater.rand01(p, 17));
    }

    private static int coastMax(BloodWater p) {
        int ticks = (int)Math.round(BloodMotionWater.lerp(26.0, 44.0, BloodMotionWater.rand01(p, 29)));
        return Math.max(ticks, 1);
    }

    private static double kickMul(BloodWater p) {
        return BloodMotionWater.lerp(0.92, 1.1, BloodMotionWater.rand01(p, 41));
    }

    private static double iceMul(BloodWater p) {
        return BloodMotionWater.lerp(0.85, 1.18, BloodMotionWater.rand01(p, 53));
    }

    private static double kickAngle(BloodWater p) {
        double r = BloodMotionWater.rand01(p, 67) * 2.0 - 1.0;
        float t = ((float)p.getAge() + p.getDriftSeed() * 200.0f) * 0.05f;
        int seed = BloodMotionWater.hash(p, 77001);
        float n = BloodLiquidUtil.noiseFbm(t, 3.3f + p.getDriftSeed() * 5.1f, seed);
        double a = r + 0.25 * (double)n;
        a = BloodMotionWater.clamp(a, -1.0, 1.0);
        return a * 0.07;
    }

    private static int coastStep(BloodWater p) {
        double bias = BloodMotionWater.lerp(-0.1, 0.1, BloodMotionWater.rand01(p, 303));
        float t = ((float)p.getAge() + p.getDriftSeed() * 200.0f) * 0.08f;
        int seed = BloodMotionWater.hash(p, 88001);
        float n = BloodLiquidUtil.noiseFbm(t, 1.9f + p.getDriftSeed() * 7.7f, seed);
        double v = (double)n + bias;
        if (v > 0.8) {
            return 2;
        }
        if (v < -0.8) {
            return 0;
        }
        return 1;
    }

    private static double rand01(BloodWater p, int salt) {
        return BloodMotionWater.hash01(BloodMotionWater.hash(p, salt));
    }

    private static int hash(BloodWater p, int salt) {
        int a = (int)(p.getDriftSeed() * 1000000.0f);
        int b = (int)(p.getGroundRot() * 10000.0f);
        int h = a * 374761393 + b * 668265263 + salt * 1442695041;
        h = (h ^ h >> 13) * 1274126177;
        return h ^ h >> 16;
    }

    private static double hash01(int h) {
        int x = h & Integer.MAX_VALUE;
        return (double)x / 2.147483647E9;
    }

    private static boolean isFlow(Level w, BlockPos pos) {
        BlockState st = BloodMotionWater.state(w, pos);
        if (st == null || GoreMaterial.of(st) != GoreMaterial.WATER) {
            return false;
        }
        return BloodMotionWater.liquidLevel(st) != 0;
    }

    private static int liquidLevel(BlockState st) {
        if (st == null) {
            return 0;
        }
        try {
            if (st.getBlock() instanceof LiquidBlock) {
                Integer lv = (Integer)st.getValue((Property)LiquidBlock.LEVEL);
                return lv != null ? lv : 0;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return 0;
    }

    private static BlockPos flowPos(BloodWater p, Level w) {
        return p == null ? null : BloodMotionWater.flowPosAt(w, p.getPosX(), p.getPosY(), p.getPosZ());
    }

    private static BlockPos flowPosAt(Level w, double x, double y, double z) {
        BlockState st;
        BlockState st2;
        if (w == null) {
            return null;
        }
        BlockPos a = BlockPos.containing(x, y, z);
        if (w.isLoaded(a) && (st2 = BloodMotionWater.state(w, a)) != null && GoreMaterial.of(st2) == GoreMaterial.WATER) {
            return a;
        }
        BlockPos b = BlockPos.containing(x, y - 0.02, z);
        if (w.isLoaded(b) && (st = BloodMotionWater.state(w, b)) != null && GoreMaterial.of(st) == GoreMaterial.WATER) {
            return b;
        }
        return null;
    }

    private static int waterDepth(BlockState st) {
        if (st == null) {
            return -1;
        }
        if (GoreMaterial.of(st) != GoreMaterial.WATER) {
            return -1;
        }
        try {
            Integer lv = (Integer)st.getValue((Property)LiquidBlock.LEVEL);
            int depth = lv != null ? lv : 0;
            return depth >= 8 ? 0 : depth;
        }
        catch (Throwable t) {
            return -1;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static Vec3 flowVec(Level w, BlockPos pos) {
        BlockState st = BloodMotionWater.state(w, pos);
        if (st == null || GoreMaterial.of(st) != GoreMaterial.WATER) {
            return Vec3.ZERO;
        }
        double dx = 0.0;
        double dz = 0.0;
        int depth = BloodMotionWater.waterDepth(st);
        if (depth < 0) {
            return Vec3.ZERO;
        }
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        try {
            for (Direction face : Direction.Plane.HORIZONTAL) {
                mp.set((Vec3i)pos).move(face);
                BlockState side = BloodMotionWater.state(w, (BlockPos)mp);
                int sideDepth = BloodMotionWater.waterDepth(side);
                if (sideDepth < 0) {
                    if (side == null || GoreMaterial.of(side).blocksMovement()) continue;
                    mp.move(Direction.DOWN);
                    BlockState below = BloodMotionWater.state(w, (BlockPos)mp);
                    int belowDepth = BloodMotionWater.waterDepth(below);
                    if (belowDepth < 0) continue;
                    int k = belowDepth - (depth - 8);
                    dx += (double)face.getStepX() * (double)k;
                    dz += (double)face.getStepZ() * (double)k;
                    continue;
                }
                int diff = sideDepth - depth;
                dx += (double)face.getStepX() * (double)diff;
                dz += (double)face.getStepZ() * (double)diff;
            }
        }
        finally {
        }
        double lenSq = dx * dx + dz * dz;
        if (lenSq <= 1.0E-12) {
            return Vec3.ZERO;
        }
        double inv = 1.0 / Math.sqrt(lenSq);
        return new Vec3(dx * inv, 0.0, dz * inv);
    }

    private static void spin(BloodWater p) {
        if (p.getSurfaceRotSpeed() == 0.0f) {
            return;
        }
        float rot = p.getSurfaceRot() + p.getSurfaceRotSpeed();
        p.setSurfaceRot(rot);
        p.setParticleAngle(rot);
    }

    private static void scaleSurface(BloodWater p) {
        if (p.getSurfaceGrowStartAge() < 0) {
            p.setSurfaceScale(p.getBaseScale());
            return;
        }
        int start = p.getSurfaceGrowStartAge();
        int end = Math.max(start + 1, p.getMaxAge());
        float t = (float)(p.getAge() - start) / (float)(end - start);
        t = BloodLiquidUtil.clamp01(t);
        float s = p.getBaseScale() + (p.getSurfaceTargetScale() - p.getBaseScale()) * t;
        p.setSurfaceScale(s);
    }

    private static void tickBody(BloodWater p, boolean sink, Vec3 flow) {
        p.setMotionX(p.getMotionX() * 0.86);
        p.setMotionZ(p.getMotionZ() * 0.86);
        p.setMotionY(p.getMotionY() * 0.9);
        BloodMotionWater.flowDrive(p, flow, 0.025, 0.14);
        if (sink) {
            p.setMotionY(p.getMotionY() - 0.0042);
            if (p.getMotionY() < -0.045) {
                p.setMotionY(-0.045);
            }
        } else {
            p.setMotionY(p.getMotionY() + 0.01);
            if (p.getMotionY() > 0.1) {
                p.setMotionY(0.1);
            }
            BloodMotionWater.capRise(p);
        }
        boolean floor = BloodMotionWater.moveSolid(p, p.getMotionX(), p.getMotionY(), p.getMotionZ());
        if (sink && floor) {
            p.setWaterTicks(11);
        }
    }

    private static void clampH(BloodWater p, double maxH) {
        double maxSq;
        if (maxH <= 0.0) {
            return;
        }
        double hs = p.getMotionX() * p.getMotionX() + p.getMotionZ() * p.getMotionZ();
        if (hs > (maxSq = maxH * maxH)) {
            double m = maxH / Math.sqrt(hs);
            p.setMotionX(p.getMotionX() * m);
            p.setMotionZ(p.getMotionZ() * m);
        }
    }

    private static void surfaceDrift(BloodWater p, double mul) {
        if (mul <= 1.0E-9) {
            return;
        }
        double t = ((double)p.getAge() + (double)(p.getDriftSeed() * 200.0f)) * 0.18 + (double)p.getGroundRot();
        double amp = 2.2E-4 * mul * (0.75 + 0.5 * (double)p.getDriftSeed());
        p.setMotionX(p.getMotionX() + Math.cos(t) * amp);
        p.setMotionZ(p.getMotionZ() + Math.sin(t * 1.37 + 0.9) * (amp * 0.85));
        if ((p.getAge() & 0xF) == 0) {
            double j = 1.0E-4 * mul;
            p.setMotionX(p.getMotionX() + (p.getRng().nextDouble() - 0.5) * j);
            p.setMotionZ(p.getMotionZ() + (p.getRng().nextDouble() - 0.5) * j);
        }
    }

    private static boolean moveSolid(BloodWater p, double dx, double dy, double dz) {
        boolean hitZ;
        double ox = p.getPosX();
        double oy = p.getPosY();
        double oz = p.getPosZ();
        p.moveParticle(dx, dy, dz);
        double ax = p.getPosX() - ox;
        double ay = p.getPosY() - oy;
        double az = p.getPosZ() - oz;
        boolean hitX = Math.abs(ax - dx) > 1.0E-7;
        boolean hitY = Math.abs(ay - dy) > 1.0E-7;
        boolean bl = hitZ = Math.abs(az - dz) > 1.0E-7;
        if (hitX) {
            p.setMotionX(p.getMotionX() * 0.15);
        }
        if (hitZ) {
            p.setMotionZ(p.getMotionZ() * 0.15);
        }
        if (hitY) {
            p.setMotionY(0.0);
        }
        return hitY && dy < 0.0;
    }

    private static BlockState state(Level w, BlockPos pos) {
        try {
            return w != null ? w.getBlockState(pos) : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static double halfSize(BloodWater p) {
        return 0.1 * (double)p.getParticleScale();
    }

    private static boolean waterBlock(BloodWater p, BlockPos pos) {
        Level w = p.getParticleWorld();
        if (w == null) {
            return false;
        }
        if (!w.isLoaded(pos)) {
            return false;
        }
        BlockState st = BloodMotionWater.state(w, pos);
        return st != null && GoreMaterial.of(st) == GoreMaterial.WATER;
    }

    private static boolean fixWetSolid(BloodWater p) {
        boolean wet;
        Level w = p.getParticleWorld();
        if (w == null) {
            return false;
        }
        BlockPos bp = BlockPos.containing(p.getPosX(), p.getPosY(), p.getPosZ());
        if (!w.isLoaded(bp)) {
            return false;
        }
        BlockState st = BloodMotionWater.state(w, bp);
        if (st == null) {
            return false;
        }
        GoreMaterial mat = GoreMaterial.of(st);
        if (mat == GoreMaterial.WATER) {
            return false;
        }
        if (!mat.isSolid()) {
            return false;
        }
        boolean bl = wet = BloodMotionWater.waterBlock(p, bp.above()) || BloodMotionWater.waterBlock(p, bp.below()) || BloodMotionWater.waterBlock(p, bp.north()) || BloodMotionWater.waterBlock(p, bp.south()) || BloodMotionWater.waterBlock(p, bp.west()) || BloodMotionWater.waterBlock(p, bp.east());
        if (!wet) {
            return false;
        }
        double push = BloodMotionWater.halfSize(p) + 0.0025;
        if (BloodMotionWater.waterBlock(p, bp.above())) {
            p.setParticlePos(p.getPosX(), (double)((net.minecraft.core.Vec3i) bp).getY() + 1.0 + push, p.getPosZ());
            p.setMotionY(0.0);
        } else if (BloodMotionWater.waterBlock(p, bp.north())) {
            p.setParticlePos(p.getPosX(), p.getPosY(), (double)((net.minecraft.core.Vec3i) bp).getZ() - push);
            p.setMotionZ(0.0);
        } else if (BloodMotionWater.waterBlock(p, bp.south())) {
            p.setParticlePos(p.getPosX(), p.getPosY(), (double)((net.minecraft.core.Vec3i) bp).getZ() + 1.0 + push);
            p.setMotionZ(0.0);
        } else if (BloodMotionWater.waterBlock(p, bp.west())) {
            p.setParticlePos((double)((net.minecraft.core.Vec3i) bp).getX() - push, p.getPosY(), p.getPosZ());
            p.setMotionX(0.0);
        } else if (BloodMotionWater.waterBlock(p, bp.east())) {
            p.setParticlePos((double)((net.minecraft.core.Vec3i) bp).getX() + 1.0 + push, p.getPosY(), p.getPosZ());
            p.setMotionX(0.0);
        } else if (BloodMotionWater.waterBlock(p, bp.below())) {
            p.setParticlePos(p.getPosX(), (double)((net.minecraft.core.Vec3i) bp).getY() - push, p.getPosZ());
            p.setMotionY(0.0);
        } else {
            return false;
        }
        BloodMotionWater.clearDecals(p);
        if (p.getWaterTicks() <= 10) {
            p.setWaterTicks(11);
        }
        p.getCache().invalidate();
        p.copyPosToPrev();
        return true;
    }

    private static void capRise(BloodWater p) {
        BlockState upSt;
        if (p.getMotionY() <= 0.0) {
            return;
        }
        Level w = p.getParticleWorld();
        if (w == null) {
            return;
        }
        BlockPos bp = BlockPos.containing(p.getPosX(), p.getPosY(), p.getPosZ());
        if (!w.isLoaded(bp)) {
            return;
        }
        BlockState st = BloodMotionWater.state(w, bp);
        if (st == null || GoreMaterial.of(st) != GoreMaterial.WATER) {
            return;
        }
        BlockPos up = bp.above();
        if (w.isLoaded(up) && (upSt = BloodMotionWater.state(w, up)) != null && GoreMaterial.of(upSt) == GoreMaterial.WATER) {
            return;
        }
        double top = (double)((net.minecraft.core.Vec3i) bp).getY() + 1.0 - 0.001;
        double maxY = top - p.getCollHalfY();
        double next = p.getPosY() + p.getMotionY();
        if (next > maxY) {
            p.setMotionY(maxY - p.getPosY());
        }
    }

    private static boolean inWater(BloodWater p) {
        Level w = p.getParticleWorld();
        if (w == null) {
            return false;
        }
        if (BloodMotionWater.isWater(p, p.getPosX(), p.getPosY(), p.getPosZ())) {
            return true;
        }
        return BloodMotionWater.isWater(p, p.getPosX(), p.getPosY() - 0.02, p.getPosZ());
    }

    private static boolean isWater(BloodWater p, double x, double y, double z) {
        BlockState st;
        Level w = p.getParticleWorld();
        if (w == null) {
            return false;
        }
        BlockPos bp = BlockPos.containing(x, y, z);
        if (!w.isLoaded(bp)) {
            return false;
        }
        try {
            st = w.getBlockState(bp);
        }
        catch (Throwable t) {
            return false;
        }
        return GoreMaterial.of(st) == GoreMaterial.WATER;
    }

    private static void toBlood(BloodWater p) {
        Minecraft mc = Minecraft.getInstance();
        Level w = p.getParticleWorld();
        if (mc.particleEngine == null || w == null) {
            p.expireAndUntrack();
            return;
        }
        int variant = BloodMotionWater.variant(p.getSprite());
        int left = Math.max(1, p.getMaxAge() - p.getAge());
        double mx = p.getMotionX();
        double mz = p.getMotionZ();
        double my = p.getMotionY();
        if (my > -0.01) {
            my = -0.01;
        }
        ParticleBlood pb = new ParticleBlood(w, p.getPosX(), p.getPosY(), p.getPosZ(), mx, my, mz, variant, p.getFluidWeight());
        pb.tintR = p.getParticleRed();
        pb.tintG = p.getParticleGreen();
        pb.tintB = p.getParticleBlue();
        pb.setMaxAge(left);
        pb.setScale(p.getLandScale());
        float cs = BloodTuning.collisionSizeForScale(pb.getScale());
        pb.setSizeSafe(cs, cs);
        pb.setAlpha(p.getParticleAlpha());
        pb.setPositionSafe(p.getPosX(), p.getPosY(), p.getPosZ());
        mc.particleEngine.add(pb);
        p.expireAndUntrack();
    }

    private static int variant(TextureAtlasSprite sprite) {
        if (sprite == null) {
            return 0;
        }
        try {
            for (int i = 0; i < 4; ++i) {
                TextureAtlasSprite s = ClientSprites.getBloodSprite(i);
                if (s != sprite) continue;
                return i;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return 0;
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) {
            return lo;
        }
        return Math.min(v, hi);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}

