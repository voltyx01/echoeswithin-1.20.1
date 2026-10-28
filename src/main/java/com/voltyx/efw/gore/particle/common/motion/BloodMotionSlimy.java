package com.voltyx.efw.gore.particle.common.motion;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.motion.BloodMotion;
import com.voltyx.efw.gore.particle.state.BloodSlimy;
import net.minecraft.util.RandomSource;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
public class BloodMotionSlimy {
    private static final float WALL_STICK_CHANCE_BASE = 0.34f;
    private static final float WALL_STICK_CHANCE_VISC_BONUS = 0.44f;
    private static final double AIR_DAMP_XZ = 0.905;
    private static final double AIR_DAMP_Y = 0.975;
    private static final double AIR_WOBBLE = 1.6E-4;
    private static final double AIR_H_CLAMP = 0.03;
    private static final double IDLE_PUSH_MIN = 0.01;
    private static final double IDLE_PUSH_MAX = 0.024;
    private static final double IDLE_NUDGE = 0.0035;
    private static final double IDLE_DROP = -0.018;
    private static final double WALL_TANGENT_JITTER = 0.01;
    private static final float GROUND_BOUNCE_PHASE_CHANCE = 0.35f;
    private static final double GROUND_BOUNCE_MIN_DOWN = 0.018;
    private static final double GROUND_BOUNCE_MIN_H = 0.006;

    public static void prime(ParticleBlood p) {
        if (!BloodSlimy.isSlimy(p) || p.isStuck) {
            return;
        }
        p.setCanCollide(true);
        p.setGravity(0.68f);
    }

    public static boolean allowAttach(ParticleBlood p, Direction face) {
        if (p == null || face == null) {
            return false;
        }
        if (!BloodSlimy.isSlimy(p)) {
            return true;
        }
        if (face == Direction.DOWN) {
            return true;
        }
        if (face == Direction.UP) {
            BloodMotionSlimy.initBounce(p);
            return !BloodMotionSlimy.hasBounce(p);
        }
        return BloodMotionSlimy.attachRoll(p, face) < BloodMotionSlimy.wallStick(p);
    }

    public static void resetBounce(ParticleBlood p) {
        if (p == null) {
            return;
        }
        p.slimyGroundBounceCount = 0;
        p.slimyGroundBounceMax = -1;
        BloodMotionSlimy.clearBounceFx(p);
    }

    public static boolean tickAir(ParticleBlood p) {
        if (!BloodSlimy.isSlimy(p)) {
            return false;
        }
        p.motionX *= 0.905;
        p.motionZ *= 0.905;
        p.motionY *= 0.975;
        double t = ((double)p.getAge() + (double)(p.dripSeed * 18.0f)) * 0.22 + (double)p.groundRot;
        p.motionX += Math.cos(t) * 1.6E-4;
        p.motionZ += Math.sin(t * 1.11 + 0.65) * 1.36E-4;
        BloodMotion.clampHorizontal(p, 0.03);
        return true;
    }

    public static void idleKick(ParticleBlood p, Direction side) {
        if (p == null) {
            return;
        }
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
        double push = 0.01 + p.getRand().nextDouble() * 0.014;
        p.motionX = vx * push;
        p.motionZ = vz * push;
        p.motionY = -0.018;
        p.noAirFlutter = false;
        p.detachWobbleTicks = 0;
        p.setOnGroundFlag(false);
        p.setPositionSafe(p.posX + vx * 0.0035, p.posY, p.posZ + vz * 0.0035);
        p.prevPosX = p.posX;
        p.prevPosY = p.posY;
        p.prevPosZ = p.posZ;
    }

    public static void collide(ParticleBlood p, double reqX, double reqY, double reqZ, boolean colX, boolean colY, boolean colZ) {
        boolean hitCeiling;
        if (!BloodSlimy.isSlimy(p)) {
            return;
        }
        boolean hitGround = colY && reqY < 0.0;
        boolean wallHit = colX || colZ;
        boolean bl = hitCeiling = colY && reqY > 0.0;
        if (hitGround && BloodMotionSlimy.bounceGround(p, reqX, reqY, reqZ)) {
            return;
        }
        if (hitCeiling && !wallHit) {
            return;
        }
        if (wallHit && BloodMotionSlimy.useWallBounce(p) && BloodMotionSlimy.bounceWall(p, reqX, reqY, reqZ, colX, colZ)) {
            return;
        }
        if (wallHit) {
            double visc = Util.smoothstep01(p.viscosity01);
            double bounceMul = 0.28 + 0.18 * (1.0 - visc);
            double downSpeed = -0.022 - 0.016 * (1.0 - visc);
            if (colX) {
                p.motionX = -reqX * bounceMul;
            }
            if (colZ) {
                p.motionZ = -reqZ * bounceMul;
            }
            if (p.motionY > downSpeed) {
                p.motionY = downSpeed - p.getRand().nextDouble() * 0.01;
            }
            if (colX && !colZ) {
                double side = 0.004 + p.getRand().nextDouble() * 0.01;
                p.motionZ = p.motionZ + (p.getRand().nextBoolean() ? 1.0 : -1.0) * side;
            } else if (colZ && !colX) {
                double side = 0.004 + p.getRand().nextDouble() * 0.01;
                p.motionX = p.motionX + (p.getRand().nextBoolean() ? 1.0 : -1.0) * side;
            } else {
                double ang = p.getRand().nextDouble() * Math.PI * 2.0;
                double side = 0.004 + p.getRand().nextDouble() * 0.01;
                p.motionX += Math.cos(ang) * side;
                p.motionZ += Math.sin(ang) * side;
            }
            p.noAirFlutter = false;
            p.detachWobbleTicks = 0;
            p.setOnGroundFlag(false);
            BloodMotion.clampHorizontal(p, 0.04 + (1.0 - visc) * 0.012);
            return;
        }
        if (colY) {
            if (reqY < 0.0) {
                p.motionY = -0.01;
                p.motionX *= 0.45;
                p.motionZ *= 0.45;
                p.setOnGroundFlag(true);
            } else {
                p.motionY = -0.014;
                p.setOnGroundFlag(false);
            }
            p.noAirFlutter = false;
            p.detachWobbleTicks = 0;
        }
    }

    private static void initBounce(ParticleBlood p) {
        int max;
        if (p.slimyGroundBounceMax >= 0) {
            return;
        }
        p.slimyGroundBounceCount = 0;
        double down = Math.max(0.0, -p.motionY);
        double h = Math.sqrt(p.motionX * p.motionX + p.motionZ * p.motionZ);
        if (down < 0.018 && h < 0.006) {
            p.slimyGroundBounceMax = 0;
            return;
        }
        float phase = BloodMotionSlimy.trait(p, 3866816905457344947L);
        if (phase >= 0.35f) {
            p.slimyGroundBounceMax = 0;
            return;
        }
        float count = BloodMotionSlimy.trait(p, 8966185382905318209L);
        p.slimyGroundBounceMax = max = 1 + Math.min(2, (int)(count * 3.0f));
        int needAge = p.getAge() + max * 10 + 12;
        if (p.getMaxAge() < needAge) {
            p.setMaxAge(needAge);
        }
    }

    private static boolean hasBounce(ParticleBlood p) {
        return p.slimyGroundBounceMax > 0 && p.slimyGroundBounceCount < p.slimyGroundBounceMax;
    }

    private static void clearBounceFx(ParticleBlood p) {
        if (p == null) {
            return;
        }
        p.slimyBounceAnimStartAge = -1;
        p.slimyBounceAnimTicks = 0;
        p.slimyBounceAnimStrength = 0.0f;
    }

    private static void startBounceFx(ParticleBlood p, double down, int idx, int max) {
        if (!BloodSlimy.isSlimy(p)) {
            return;
        }
        float impact = Util.clamp01((float)((down - 0.02) / 0.18));
        float fade = 1.0f - 0.16f * Util.clamp01((float)idx / (float)Math.max(1, max));
        p.slimyBounceAnimStartAge = p.getAge();
        p.slimyBounceAnimTicks = Math.max(6, 9 - Math.min(idx, 2));
        p.slimyBounceAnimStrength = Util.clamp01((0.7f + 0.3f * impact) * fade);
    }

    private static boolean bounceGround(ParticleBlood p, double reqX, double reqY, double reqZ) {
        boolean last;
        BloodMotionSlimy.initBounce(p);
        if (!BloodMotionSlimy.hasBounce(p)) {
            return false;
        }
        RandomSource r = p.getRand();
        float visc = Util.smoothstep01(p.viscosity01);
        double spring = 1.0 - (double)visc;
        int max = Math.max(1, p.slimyGroundBounceMax);
        int idx = Math.max(0, p.slimyGroundBounceCount);
        float prog = Util.clamp01((float)idx / (float)max);
        double down = Math.max(0.0, -reqY);
        double hIn = Math.sqrt(reqX * reqX + reqZ * reqZ);
        float down01 = Util.clamp01((float)((down - 0.018) / 0.18));
        float h01 = Util.clamp01((float)((hIn - 0.006) / 0.1));
        float impact = Util.clamp01(down01 * 0.78f + h01 * 0.22f);
        double up = 0.05 + 0.05 * (double)impact + 0.018 * spring;
        if ((up *= 1.0 - 0.18 * (double)prog) < 0.04) {
            up = 0.04;
        }
        if (up > 0.115) {
            up = 0.115;
        }
        double hFromImpact = 0.018 + 0.024 * (double)impact + 0.012 * spring;
        double hFromMove = hIn * (0.42 + 0.18 * spring);
        double hOut = Math.max(hFromImpact, hFromMove);
        if ((hOut *= 1.0 - 0.14 * (double)prog) < 0.016) {
            hOut = 0.016;
        }
        if (hOut > 0.06) {
            hOut = 0.06;
        }
        boolean bl = last = idx + 1 >= max;
        if (last) {
            up *= 0.88;
            hOut *= 0.82;
        }
        double ang = r.nextDouble() * Math.PI * 2.0;
        double mx = Math.cos(ang) * hOut;
        double mz = Math.sin(ang) * hOut;
        BloodMotionSlimy.startBounceFx(p, down, idx, max);
        p.motionX = mx += (r.nextDouble() - 0.5) * 0.004;
        p.motionY = up;
        p.motionZ = mz += (r.nextDouble() - 0.5) * 0.004;
        p.setOnGroundFlag(false);
        p.noAirFlutter = false;
        p.idleTicks = 0;
        p.detachWobbleTicks = Math.max(p.detachWobbleTicks, 6 + Math.max(0, max - idx - 1));
        BloodMotion.clampHorizontal(p, 0.06 + 0.018 * spring);
        p.syncToVanillaMotionOnly();
        double popY = 0.02 + 0.016 * spring - 0.003 * (double)idx;
        double popSide = 0.006 + 0.006 * spring;
        double popX = Math.cos(ang) * popSide;
        double popZ = Math.sin(ang) * popSide;
        p.vanillaMove(popX, popY, popZ);
        p.motionX = mx;
        p.motionY = up;
        p.motionZ = mz;
        p.syncToVanillaMotionOnly();
        p.setOnGroundFlag(false);
        p.slimyGroundBounceCount = idx + 1;
        return true;
    }

    private static boolean bounceWall(ParticleBlood p, double reqX, double reqY, double reqZ, boolean colX, boolean colZ) {
        RandomSource r = p.getRand();
        float visc = Util.smoothstep01(p.viscosity01);
        double sticky = 0.2 + 0.65 * (double)visc;
        double rebound = 1.0 - sticky;
        Vec3 in = new Vec3(reqX, reqY, reqZ);
        Vec3 normal = BloodMotion.collisionNormal(reqX, reqY, reqZ, colX, colZ, false);
        double dot = in.dot(normal);
        double toward = Math.max(0.0, -dot);
        Vec3 tangent = in.subtract(normal.scale(dot));
        Vec3 wallTangent = BloodMotion.wallTangent(normal, tangent, r);
        Vec3 wallUp = BloodMotion.normalizeOr(normal.cross(wallTangent), new Vec3(0.0, 1.0, 0.0));
        double restitution = BloodMotion.randRange(r, 0.24, 0.42) * (0.88 + 0.12 * rebound);
        double tangentKeep = BloodMotion.randRange(r, 0.68, 0.94) * (0.9 + 0.1 * rebound);
        double escape = BloodMotion.randRange(r, 0.005, 0.018);
        double sideJitter = BloodMotion.randRange(r, 0.004, 0.024) * (0.5 + 0.5 * rebound);
        double upJitter = BloodMotion.randRange(r, 0.002, 0.012) * (0.35 + 0.65 * rebound);
        double downKick = BloodMotion.randRange(r, 0.006, 0.018);
        Vec3 out = tangent.scale(tangentKeep).add(normal.scale(Math.max(toward * restitution, escape))).add(wallTangent.scale(BloodMotion.randSigned(r) * sideJitter)).add(wallUp.scale(BloodMotion.randSigned(r) * upJitter));
        if (out.y > 0.0) {
            out = new Vec3(out.x, out.y * 0.35, out.z);
        }
        out = new Vec3(out.x, Math.min(out.y, 0.0) - downKick, out.z);
        p.setOnGroundFlag(false);
        p.noAirFlutter = false;
        p.detachWobbleTicks = Math.max(p.detachWobbleTicks, 5);
        p.motionX = out.x;
        p.motionY = out.y;
        p.motionZ = out.z;
        BloodMotion.clampHorizontal(p, BloodMotion.randRange(r, 0.05, 0.088) + rebound * 0.015);
        p.syncToVanillaMotionOnly();
        double sep = BloodMotion.randRange(r, 8.0E-4, 0.0032);
        Vec3 sepVec = normal.scale(sep).add(wallTangent.scale(BloodMotion.randSigned(r) * sep * 0.4));
        p.vanillaMove(sepVec.x, 0.0, sepVec.z);
        p.motionX = out.x;
        p.motionY = out.y;
        p.motionZ = out.z;
        p.syncToVanillaMotionOnly();
        return true;
    }

    private static boolean useWallBounce(ParticleBlood p) {
        if (!BloodSlimy.isSlimy(p)) {
            return false;
        }
        float visc = Util.smoothstep01(p.viscosity01);
        float pref = 1.0f - visc;
        if (pref < 0.18f) {
            return false;
        }
        float chance = 0.12f + 0.44f * pref;
        return BloodMotionSlimy.trait(p, -4942790177534073029L) < chance;
    }

    private static float wallStick(ParticleBlood p) {
        float visc = Util.smoothstep01(p.viscosity01);
        float chance = 0.34f + 0.44f * visc;
        if (chance < 0.24f) {
            chance = 0.24f;
        }
        if (chance > 0.84f) {
            chance = 0.84f;
        }
        return chance;
    }

    private static float attachRoll(ParticleBlood p, Direction face) {
        long s = BloodMotionSlimy.seed(p);
        s ^= ((long)p.getAge() + 1L) * -7046029254386353131L;
        s ^= ((long)face.get3DDataValue() + 1L) * -4658895280553007687L;
        s = BloodMotionSlimy.mix(s);
        return (float)(s >>> 40 & 0xFFFFFFL) / 1.6777216E7f;
    }

    private static float trait(ParticleBlood p, long salt) {
        long s = BloodMotionSlimy.seed(p) ^ salt;
        s = BloodMotionSlimy.mix(s);
        return (float)(s >>> 40 & 0xFFFFFFL) / 1.6777216E7f;
    }

    private static long seed(ParticleBlood p) {
        long sx = Double.doubleToLongBits(p.spawnX);
        long sy = Double.doubleToLongBits(p.spawnY);
        long sz = Double.doubleToLongBits(p.spawnZ);
        long a = Float.floatToIntBits(p.groundRot);
        long b = Float.floatToIntBits(p.dripSeed);
        long s = -7046029254386353131L;
        s ^= sx * 31L + sy * 17L + sz * 13L;
        s ^= a * -4658895280553007687L;
        return BloodMotionSlimy.mix(s ^= b * -7723592293110705685L);
    }

    private static long mix(long z) {
        z ^= z >>> 33;
        z *= -49064778989728563L;
        z ^= z >>> 33;
        z *= -4265267296055464877L;
        z ^= z >>> 33;
        return z;
    }
}

