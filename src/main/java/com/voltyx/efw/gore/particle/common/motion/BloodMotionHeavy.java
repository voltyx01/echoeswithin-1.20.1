package com.voltyx.efw.gore.particle.common.motion;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.motion.BloodMotion;
import com.voltyx.efw.gore.particle.state.BloodHeavy;
import com.voltyx.efw.gore.particle.state.liquid.BloodFluidSurfaceCache;
import com.voltyx.efw.gore.particle.state.liquid.BloodLiquidUtil;
import com.voltyx.efw.gore.particle.state.liquid.lava.BloodLava;
import com.voltyx.efw.gore.particle.state.liquid.lava.BloodLavaCache;
import com.voltyx.efw.gore.particle.state.liquid.water.BloodWater;
import com.voltyx.efw.gore.particle.state.liquid.water.BloodWaterCache;
import net.minecraft.util.RandomSource;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
public class BloodMotionHeavy {
    public static boolean allowAttach(@Nullable ParticleBlood p, @Nullable Direction faceHint) {
        if (p == null || faceHint == null) {
            return false;
        }
        if (!BloodHeavy.isHeavy(p)) {
            return true;
        }
        if (faceHint != Direction.UP) {
            return false;
        }
        BloodMotionHeavy.initBounce(p);
        return BloodMotionHeavy.canSplat(p);
    }

    static void initBounce(@Nullable ParticleBlood p) {
        if (p == null) {
            return;
        }
        BloodMotionHeavy.initBounce(p, p.motionX, p.motionY, p.motionZ);
    }

    static void initBounce(@Nullable ParticleBlood p, double inX, double inY, double inZ) {
        int dur;
        int now;
        if (p == null) {
            return;
        }
        if (!BloodHeavy.isHeavy(p)) {
            return;
        }
        BloodHeavy.Runtime rt = p.heavy;
        if (rt == null) {
            return;
        }
        if (rt.heavyGroundBounceStartAge >= 0) {
            return;
        }
        rt.heavyGroundBounceStartAge = now = p.getAge();
        rt.heavyGroundBounceCount = 0;
        BloodMotionHeavy.clearTotter(rt);
        double down = Math.max(0.0, -inY);
        double h = Math.sqrt(inX * inX + inZ * inZ);
        float visc = Util.smoothstep01(p.viscosity01);
        float down01 = (float)Util.clamp01((down - 0.035) / 0.24);
        float h01 = (float)Util.clamp01((h - 0.008) / 0.165);
        float impact01 = Util.clamp01(0.66f * down01 + 0.34f * h01);
        float bounce01 = Util.clamp01(impact01 * (1.0f - 0.42f * visc));
        if (bounce01 <= 0.06f || down < 0.04 && h < 0.018) {
            rt.heavyGroundBounceMode = BloodHeavy.HeavyGroundBounceMode.NONE;
            rt.heavyGroundBounceMax = 0;
            rt.heavyGroundBounceEndAge = now;
            return;
        }
        boolean allowAround = h01 >= 0.12f || bounce01 >= 0.46f || down01 >= 0.55f && h01 >= 0.08f;
        rt.heavyGroundBounceMode = allowAround ? BloodHeavy.HeavyGroundBounceMode.AROUND : BloodHeavy.HeavyGroundBounceMode.SMALL;
        RandomSource r = p.getRand();
        if (rt.heavyGroundBounceMode == BloodHeavy.HeavyGroundBounceMode.AROUND) {
            dur = 11 + Math.round(15.0f * bounce01 * (1.05f - 0.22f * visc)) + BloodMotion.randBetween(r, 0, 4);
            rt.heavyGroundBounceEndAge = now + Util.clampInt(dur, 11, 28);
            int maxB = 3 + Math.round(4.0f * bounce01 * (1.0f - 0.15f * visc)) + BloodMotion.randBetween(r, 0, 2);
            rt.heavyGroundBounceMax = Util.clampInt(maxB, 3, 8);
        } else {
            dur = 8 + Math.round(10.0f * bounce01 * (1.0f - 0.28f * visc)) + BloodMotion.randBetween(r, 0, 2);
            rt.heavyGroundBounceEndAge = now + Util.clampInt(dur, 8, 18);
            int maxB = 2 + Math.round(3.0f * bounce01 * (1.0f - 0.2f * visc)) + BloodMotion.randBetween(r, 0, 1);
            rt.heavyGroundBounceMax = Util.clampInt(maxB, 2, 5);
        }
        int needAge = rt.heavyGroundBounceEndAge + 14;
        if (p.getMaxAge() < needAge) {
            p.setMaxAge(needAge);
        }
    }

    static boolean bounceActive(@Nullable ParticleBlood p) {
        if (p == null) {
            return false;
        }
        if (!BloodHeavy.isHeavy(p)) {
            return false;
        }
        BloodHeavy.Runtime rt = p.heavy;
        if (rt == null) {
            return false;
        }
        if (rt.heavyGroundBounceStartAge < 0) {
            return false;
        }
        if (rt.heavyGroundBounceMode == null || rt.heavyGroundBounceMode == BloodHeavy.HeavyGroundBounceMode.NONE) {
            return false;
        }
        if (rt.heavyGroundBounceMax <= 0) {
            return false;
        }
        if (p.getAge() >= rt.heavyGroundBounceEndAge) {
            return false;
        }
        return rt.heavyGroundBounceCount < rt.heavyGroundBounceMax;
    }

    static boolean canSplat(@Nullable ParticleBlood p) {
        return !BloodMotionHeavy.bounceActive(p) && !BloodMotionHeavy.totterActive(p);
    }

    static void noteBounce(@Nullable ParticleBlood p) {
        if (p == null) {
            return;
        }
        BloodHeavy.Runtime rt = p.heavy;
        if (rt == null) {
            return;
        }
        if (rt.heavyGroundBounceCount < Integer.MAX_VALUE) {
            ++rt.heavyGroundBounceCount;
        }
    }

    static void endBounce(@Nullable ParticleBlood p) {
        if (p == null) {
            return;
        }
        BloodHeavy.Runtime rt = p.heavy;
        if (rt == null) {
            return;
        }
        if (rt.heavyGroundBounceMax <= 0) {
            rt.heavyGroundBounceMax = 1;
        }
        rt.heavyGroundBounceCount = rt.heavyGroundBounceMax;
    }

    static boolean handleCollision(@Nullable ParticleBlood p, double reqX, double reqY, double reqZ, boolean colX, boolean colY, boolean colZ) {
        boolean hitCeiling;
        if (p == null) {
            return false;
        }
        if (!BloodHeavy.isHeavy(p)) {
            return false;
        }
        boolean hitGround = colY && reqY < 0.0;
        boolean bl = hitCeiling = colY && reqY > 0.0;
        if (hitGround) {
            BloodMotionHeavy.initBounce(p, reqX, reqY, reqZ);
            if (!BloodMotionHeavy.canSplat(p)) {
                double settleThreshold;
                double down = Math.max(0.0, -reqY);
                double h = Math.sqrt(reqX * reqX + reqZ * reqZ);
                float visc = Util.smoothstep01(p.viscosity01);
                boolean tottering = BloodMotionHeavy.totterActive(p);
                double settleMetric = down + h * (tottering ? 0.45 : 0.55);
                if (settleMetric < (settleThreshold = (tottering ? 0.01 : 0.014) + 0.012 * (double)visc)) {
                    if (tottering) {
                        BloodMotionHeavy.clearTotter(p.heavy);
                    }
                    BloodMotionHeavy.endBounce(p);
                    return false;
                }
                BloodMotionHeavy.groundBounce(p, reqX, reqY, reqZ, colX, colZ);
                return true;
            }
        }
        if (!hitGround && (colX || colZ || hitCeiling)) {
            return BloodMotionHeavy.wallBounce(p, reqX, reqY, reqZ, colX, colZ, hitCeiling);
        }
        return false;
    }

    static boolean airFall(@Nullable ParticleBlood p) {
        double hClamp;
        double damp;
        boolean smallBounceActive;
        if (p == null) {
            return false;
        }
        if (!BloodHeavy.isHeavy(p)) {
            return false;
        }
        BloodHeavy.Runtime rt = p.heavy;
        boolean bouncing = BloodMotionHeavy.bounceActive(p);
        boolean tottering = BloodMotionHeavy.totterActive(p);
        boolean aroundBounceActive = bouncing && rt != null && rt.heavyGroundBounceMode == BloodHeavy.HeavyGroundBounceMode.AROUND;
        boolean bl = smallBounceActive = bouncing && rt != null && rt.heavyGroundBounceMode == BloodHeavy.HeavyGroundBounceMode.SMALL;
        if (tottering) {
            double life01 = 0.0;
            if (rt != null && rt.heavyTotterEndAge > p.getAge()) {
                life01 = Util.clamp01((double)(rt.heavyTotterEndAge - p.getAge()) / 10.0);
            }
            double amp = rt != null ? (double)rt.heavyTotterAmp * (0.45 + 0.55 * life01) : 0.0;
            double t = (double)p.groundRot + ((double)p.getAge() + (double)(p.dripSeed * 17.0f)) * 0.82;
            p.motionX += Math.cos(t) * amp;
            p.motionZ += Math.sin(t) * amp;
            p.motionY *= 0.94;
            if (p.motionY < -0.04) {
                p.motionY = -0.04;
            }
            if (rt != null) {
                p.groundRot += rt.heavyTotterSpin * (0.55f + 0.45f * (float)life01);
                rt.heavyTotterSpin *= 0.985f;
                rt.heavyTotterAmp *= 0.992f;
            }
            p.detachWobbleTicks = Math.max(p.detachWobbleTicks, 2);
            damp = 0.972;
            hClamp = 0.065;
        } else if (aroundBounceActive) {
            damp = 0.97;
            hClamp = 0.095;
        } else if (smallBounceActive) {
            damp = 0.96;
            hClamp = 0.065;
        } else {
            damp = 0.985;
            hClamp = 0.1;
        }
        p.motionX *= damp;
        p.motionZ *= damp;
        if (!bouncing && !tottering && p.motionY > 0.0) {
            p.motionY *= 0.92;
        }
        BloodMotion.clampHorizontal(p, hClamp);
        return true;
    }

    static double wobbleClamp(@Nullable ParticleBlood p) {
        return BloodHeavy.isHeavy(p) ? 0.11 : 0.05;
    }

    static boolean landLava(@Nonnull BloodLava p, @Nonnull BloodLavaCache cache, @Nullable BloodFluidSurfaceCache.SurfacePlane planeAtStart, @Nonnull BloodFluidSurfaceCache.SurfacePlane planeAtEnd, double x0, double y0, double z0, double dx, double dy, double dz, double nx, double ny, double nz) {
        double tHit;
        if (!p.isHeavyInLava()) {
            return false;
        }
        double yPushBase = 4.2E-4 + p.getSurfaceOffset();
        double ySurfN = planeAtEnd.yAt(nx, nz);
        double yLockN = ySurfN + yPushBase;
        if (ny > yLockN + 1.5E-4) {
            return false;
        }
        BloodFluidSurfaceCache.SurfacePlane plane0 = planeAtStart != null ? planeAtStart : planeAtEnd;
        double ySurf0 = plane0.yAt(x0, z0);
        double yLock0 = ySurf0 + yPushBase;
        double f0 = y0 - yLock0;
        double f1 = ny - yLockN;
        if (f0 <= 0.0) {
            tHit = 0.0;
        } else if (f1 > 0.0) {
            tHit = 1.0;
        } else {
            double lo = 0.0;
            double hi = 1.0;
            for (int i = 0; i < 4; ++i) {
                double yLockM;
                double fm;
                double mid = (lo + hi) * 0.5;
                double mx = x0 + dx * mid;
                double my = y0 + dy * mid;
                double mz = z0 + dz * mid;
                cache.forceScanAt(mx, my, mz);
                BloodFluidSurfaceCache.SurfacePlane pm = cache.planeAt(mx, mz);
                if (pm == null) {
                    pm = planeAtEnd;
                }
                if ((fm = my - (yLockM = pm.yAt(mx, mz) + yPushBase)) > 0.0) {
                    lo = mid;
                    continue;
                }
                hi = mid;
            }
            tHit = hi;
        }
        if (tHit < 0.0) {
            tHit = 0.0;
        }
        if (tHit > 1.0) {
            tHit = 1.0;
        }
        double hx = x0 + dx * tHit;
        double hy = y0 + dy * tHit;
        double hz = z0 + dz * tHit;
        cache.forceScanAt(hx, hy, hz);
        BloodFluidSurfaceCache.SurfacePlane planeH = cache.planeAt(hx, hz);
        if (planeH == null) {
            planeH = planeAtEnd;
        }
        double ySurfH = planeH.yAt(hx, hz);
        double lockYH = ySurfH + yPushBase;
        float tStore = (float)tHit;
        if (tStore >= 1.0f) {
            tStore = 0.9995f;
        }
        p.setHeavyLandingInfo(x0, y0, z0, dx, dy, dz, tStore);
        p.setParticlePos(hx, lockYH, hz);
        BloodMotionHeavy.settleLava(p, lockYH);
        return true;
    }

    static void settleLava(@Nonnull BloodLava p, double lockY) {
        p.setOnSurface(true);
        p.trackIfNeeded();
        p.setSurfaceGrowStartAge(p.getAge());
        float surfMul = BloodHeavy.lavaSurfaceScaleMul(true);
        float surfBase = Math.max(0.001f, p.getBaseScale() * surfMul);
        p.setSurfaceScale(surfBase);
        p.clearImpact();
        p.setMotionX(0.0);
        p.setMotionZ(0.0);
        p.setMotionY(0.0);
        p.resetBoilBurst();
        p.setParticlePos(p.getPosX(), lockY, p.getPosZ());
    }

    static void tickWater(@Nonnull BloodWater p, @Nullable BloodWaterCache cache, boolean wasOnFloor) {
        boolean hitFloor;
        p.setOnSurface(false);
        BloodWaterCache.SurfaceSample surf = cache != null && cache.hasSurface() ? cache.sampleAt(p.getPosX(), p.getPosZ()) : null;
        double yContact = surf != null ? surf.y - p.getCollHalfY() : Double.POSITIVE_INFINITY;
        p.setMotionX(p.getMotionX() * 0.86);
        p.setMotionZ(p.getMotionZ() * 0.86);
        p.setMotionY(p.getMotionY() * 0.9);
        p.setMotionY(p.getMotionY() - 0.01);
        if (p.getMotionY() < -0.1) {
            p.setMotionY(-0.1);
        }
        if (surf != null) {
            double cap = yContact - 0.0075;
            if (p.getPosY() + p.getMotionY() > cap) {
                p.setMotionY(cap - p.getPosY());
            }
        }
        if (hitFloor = BloodMotionHeavy.moveWater(p, p.getMotionX(), p.getMotionY(), p.getMotionZ())) {
            p.setOnFloor(true);
            if (!wasOnFloor) {
                p.setSurfaceGrowStartAge(p.getAge());
                p.setSurfaceScale(p.getBaseScale());
            }
            BloodMotionHeavy.waterScale(p);
            p.setMotionY(0.0);
            p.setMotionX(p.getMotionX() * 0.7);
            p.setMotionZ(p.getMotionZ() * 0.7);
            p.trackIfNeeded();
        } else {
            p.setOnFloor(false);
            if (wasOnFloor) {
                p.setSurfaceGrowStartAge(-1);
                p.setSurfaceScale(p.getBaseScale());
            }
            p.untrackIfNeeded();
        }
    }

    private static void groundBounce(ParticleBlood p, double reqX, double reqY, double reqZ, boolean colX, boolean colZ) {
        boolean microHop;
        boolean wantsTotter;
        double maxUp;
        RandomSource r = p.getRand();
        BloodHeavy.Runtime rt = p.heavy;
        boolean tottering = BloodMotionHeavy.totterActive(p);
        if (tottering && rt != null) {
            double spin;
            double slip = Math.sqrt(reqX * reqX + reqZ * reqZ);
            double up = 0.01 + r.nextDouble() * 0.01 + slip * 0.06;
            if (up > 0.028) {
                up = 0.028;
            }
            if (Math.abs(spin = (double)rt.heavyTotterSpin) < (double)1.0E-4f) {
                spin = (r.nextBoolean() ? 1.0 : -1.0) * 0.18;
            }
            double ang = (double)p.groundRot + spin * (2.0 + r.nextDouble() * 0.6);
            double lateral = 0.01 + (double)rt.heavyTotterAmp * 2.6 + slip * 0.35;
            lateral = Util.clamp(lateral, 0.01, 0.03);
            double mx = Math.cos(ang) * lateral;
            double mz = Math.sin(ang) * lateral;
            if (colX) {
                mx *= 0.75;
            }
            if (colZ) {
                mz *= 0.75;
            }
            p.groundRot += (float)spin;
            rt.heavyTotterSpin = (float)(-spin * (0.82 + r.nextDouble() * 0.08));
            rt.heavyTotterAmp *= 0.86f;
            if (rt.heavyTotterAmp < 0.0012f || p.getAge() + 1 >= rt.heavyTotterEndAge) {
                BloodMotionHeavy.clearTotter(rt);
            }
            p.motionX = mx;
            p.motionY = up;
            p.motionZ = mz;
            p.setOnGroundFlag(false);
            p.idleTicks = 0;
            p.noAirFlutter = false;
            p.detachWobbleTicks = Math.max(p.detachWobbleTicks, 5);
            BloodMotion.clampHorizontal(p, 0.032);
            p.syncToVanillaMotionOnly();
            return;
        }
        BloodHeavy.HeavyGroundBounceMode mode = rt != null && rt.heavyGroundBounceMode != null ? rt.heavyGroundBounceMode : BloodHeavy.HeavyGroundBounceMode.SMALL;
        float visc = Util.smoothstep01(p.viscosity01);
        double sticky01 = 0.24 + 0.76 * (double)visc;
        double down = Math.max(0.0, -reqY);
        double hIn = Math.sqrt(reqX * reqX + reqZ * reqZ);
        double totalIn = Math.sqrt(reqX * reqX + reqY * reqY + reqZ * reqZ);
        int bounceIndex = rt != null ? rt.heavyGroundBounceCount : 0;
        int maxB = Math.max(1, rt != null ? rt.heavyGroundBounceMax : 1);
        double age01 = 0.0;
        if (rt != null && rt.heavyGroundBounceStartAge >= 0 && rt.heavyGroundBounceEndAge > rt.heavyGroundBounceStartAge) {
            age01 = Util.clamp01((double)(p.getAge() - rt.heavyGroundBounceStartAge) / (double)(rt.heavyGroundBounceEndAge - rt.heavyGroundBounceStartAge));
        }
        double count01 = Util.clamp01((double)bounceIndex / (double)maxB);
        double settle01 = Math.max(count01, age01 * 0.9);
        double spring01 = 1.0 - settle01;
        double steep01 = Util.clamp01(down / Math.max(1.0E-6, down + hIn));
        double graze01 = 1.0 - steep01;
        double energy01 = Util.clamp01(totalIn / 0.22);
        double restitution = mode == BloodHeavy.HeavyGroundBounceMode.AROUND ? 0.24 : 0.17;
        restitution += spring01 * (mode == BloodHeavy.HeavyGroundBounceMode.AROUND ? 0.14 : 0.1);
        restitution += energy01 * 0.04;
        restitution *= 1.0 - 0.36 * sticky01;
        restitution *= 0.88 + 0.22 * steep01;
        restitution = Util.clamp(restitution, 0.06, mode == BloodHeavy.HeavyGroundBounceMode.AROUND ? 0.34 : 0.25);
        double up = down * restitution;
        double minUp = (mode == BloodHeavy.HeavyGroundBounceMode.AROUND ? 0.014 : 0.01) + spring01 * 0.012;
        double d = maxUp = mode == BloodHeavy.HeavyGroundBounceMode.AROUND ? 0.135 : 0.088;
        if (hIn < 0.01 && down < 0.06) {
            minUp *= 0.8;
        }
        if (up < minUp) {
            up = minUp;
        }
        if (up > maxUp) {
            up = maxUp;
        }
        double tangentialMul = mode == BloodHeavy.HeavyGroundBounceMode.AROUND ? 0.82 : 0.68;
        tangentialMul += graze01 * 0.12;
        tangentialMul -= sticky01 * 0.15;
        tangentialMul -= settle01 * (mode == BloodHeavy.HeavyGroundBounceMode.AROUND ? 0.12 : 0.18);
        tangentialMul = Util.clamp(tangentialMul, 0.28, 0.92);
        double mx = reqX * tangentialMul;
        double mz = reqZ * tangentialMul;
        double addedSkid = down * (mode == BloodHeavy.HeavyGroundBounceMode.AROUND ? 0.12 : 0.08);
        addedSkid *= (1.0 - sticky01) * (0.45 + 0.55 * spring01);
        double yawMax = mode == BloodHeavy.HeavyGroundBounceMode.AROUND ? 0.65 : 0.28;
        yawMax *= 0.35 + 0.65 * spring01;
        if (graze01 > 0.45) {
            yawMax *= 0.75;
        }
        if (hIn > 1.0E-6) {
            double outSpeed = Math.sqrt(mx * mx + mz * mz);
            double baseAng = Math.atan2(mz, mx);
            double ang = baseAng + (r.nextDouble() - 0.5) * 2.0 * yawMax;
            mx = Math.cos(ang) * (outSpeed += addedSkid * (0.45 + 0.55 * (1.0 - graze01)));
            mz = Math.sin(ang) * outSpeed;
        } else {
            double ang = (double)p.groundRot + (r.nextDouble() - 0.5) * 1.05;
            double outSpeed = 0.008 + addedSkid;
            mx = Math.cos(ang) * outSpeed;
            mz = Math.sin(ang) * outSpeed;
        }
        if (colX) {
            mx *= 0.75;
        }
        if (colZ) {
            mz *= 0.75;
        }
        double micro = (0.0012 + 0.003 * spring01) * (1.0 - 0.4 * sticky01);
        double outH = Math.sqrt((mx += (r.nextDouble() - 0.5) * micro) * mx + (mz += (r.nextDouble() - 0.5) * micro) * mz);
        double spinKick = (0.16 + 0.42 * (1.0 - sticky01) + 0.24 * graze01) * (r.nextBoolean() ? 1.0 : -1.0);
        p.groundRot += (float)spinKick;
        double maxH = (mode == BloodHeavy.HeavyGroundBounceMode.AROUND ? 0.1 : 0.06) * (0.55 + 0.45 * spring01);
        maxH += graze01 * 0.018;
        maxH = Util.clamp(maxH, 0.02, mode == BloodHeavy.HeavyGroundBounceMode.AROUND ? 0.115 : 0.07);
        boolean nearEnd = bounceIndex + 1 >= Math.max(1, maxB - 1);
        boolean bl = wantsTotter = nearEnd && outH > 0.01 && up < 0.04;
        if (wantsTotter) {
            BloodMotionHeavy.startTotter(p, outH, spring01, sticky01, graze01);
        }
        p.motionX = mx;
        p.motionY = up;
        p.motionZ = mz;
        p.setOnGroundFlag(false);
        p.idleTicks = 0;
        p.noAirFlutter = false;
        p.detachWobbleTicks = Math.max(p.detachWobbleTicks, (mode == BloodHeavy.HeavyGroundBounceMode.AROUND ? 7 : 4) + (int)Math.round(5.0 * spring01));
        BloodMotion.clampHorizontal(p, maxH);
        p.syncToVanillaMotionOnly();
        boolean bl2 = microHop = down < 0.028 && outH < 0.012 && up < 0.014 || bounceIndex + 1 >= maxB && up < 0.01 && outH < 0.01;
        if (microHop) {
            BloodMotionHeavy.endBounce(p);
        } else {
            BloodMotionHeavy.noteBounce(p);
        }
    }

    private static boolean totterActive(@Nullable ParticleBlood p) {
        if (p == null) {
            return false;
        }
        if (!BloodHeavy.isHeavy(p)) {
            return false;
        }
        BloodHeavy.Runtime rt = p.heavy;
        return rt != null && rt.heavyTotterEndAge >= 0 && p.getAge() < rt.heavyTotterEndAge;
    }

    private static void clearTotter(@Nullable BloodHeavy.Runtime rt) {
        if (rt == null) {
            return;
        }
        rt.heavyTotterEndAge = -1;
        rt.heavyTotterSpin = 0.0f;
        rt.heavyTotterAmp = 0.0f;
    }

    private static void startTotter(@Nonnull ParticleBlood p, double outH, double spring01, double sticky01, double graze01) {
        BloodHeavy.Runtime rt = p.heavy;
        if (rt == null) {
            return;
        }
        RandomSource r = p.getRand();
        float chance = (float)Util.clamp01(0.18 + outH * 5.0 + (1.0 - sticky01) * 0.28 + graze01 * 0.16);
        if (r.nextFloat() > chance) {
            return;
        }
        int dur = 4 + BloodMotion.randBetween(r, 0, 3) + (int)Math.round(4.0 * spring01);
        int endAge = p.getAge() + Util.clampInt(dur, 4, 11);
        if (endAge > rt.heavyTotterEndAge) {
            rt.heavyTotterEndAge = endAge;
        }
        rt.heavyTotterSpin = (float)((0.14 + outH * 2.2 + graze01 * 0.12) * (r.nextBoolean() ? 1.0 : -1.0));
        rt.heavyTotterAmp = (float)Util.clamp(0.0022 + outH * 0.12 + (1.0 - sticky01) * 0.0025, 0.0022, 0.014);
        int needAge = rt.heavyTotterEndAge + 4;
        if (p.getMaxAge() < needAge) {
            p.setMaxAge(needAge);
        }
    }

    private static boolean wallBounce(ParticleBlood p, double reqX, double reqY, double reqZ, boolean colX, boolean colZ, boolean hitCeiling) {
        RandomSource r = p.getRand();
        float visc = Util.smoothstep01(p.viscosity01);
        double sticky01 = 0.3 + 0.7 * (double)visc;
        Vec3 in = new Vec3(reqX, reqY, reqZ);
        Vec3 normal = BloodMotion.collisionNormal(reqX, reqY, reqZ, colX, colZ, hitCeiling);
        double dot = in.dot(normal);
        double toward = Math.max(0.0, -dot);
        Vec3 tangent = in.subtract(normal.scale(dot));
        Vec3 wallTangent = BloodMotion.wallTangent(normal, tangent, r);
        Vec3 wallUp = BloodMotion.normalizeOr(normal.cross(wallTangent), new Vec3(0.0, 1.0, 0.0));
        double impact01 = Math.max(0.0, Math.min(1.0, (toward - 0.006) / 0.14));
        double restitution = BloodMotion.randRange(r, 0.16, 0.3) * (1.0 - 0.24 * sticky01);
        double tangentKeep = BloodMotion.randRange(r, 0.46, 0.82) * (1.0 - 0.1 * sticky01);
        double escape = BloodMotion.randRange(r, 0.004, 0.016) * (0.7 + 0.6 * impact01);
        double sideJitter = BloodMotion.randRange(r, 0.003, 0.02) * (0.45 + 0.55 * impact01);
        double upJitter = BloodMotion.randRange(r, 0.0, 0.008) * (0.35 + 0.65 * impact01);
        double downKick = BloodMotion.randRange(r, 0.01, 0.024) * (0.85 + 0.15 * sticky01);
        Vec3 out = tangent.scale(tangentKeep).add(normal.scale(Math.max(toward * restitution, escape))).add(wallTangent.scale(BloodMotion.randSigned(r) * sideJitter)).add(wallUp.scale(BloodMotion.randSigned(r) * upJitter));
        out = new Vec3(out.x, Math.min(out.y, 0.0) - downKick, out.z);
        p.setOnGroundFlag(false);
        p.noAirFlutter = false;
        p.detachWobbleTicks = Math.max(p.detachWobbleTicks, 4 + BloodMotion.randBetween(r, 0, 4));
        p.motionX = out.x;
        p.motionY = out.y;
        p.motionZ = out.z;
        BloodMotion.clampHorizontal(p, BloodMotion.randRange(r, 0.038, 0.078) + impact01 * 0.018);
        p.syncToVanillaMotionOnly();
        double sep = BloodMotion.randRange(r, 8.0E-4, 0.0038) * (0.8 + 0.4 * impact01);
        Vec3 sepVec = normal.scale(sep).add(wallTangent.scale(BloodMotion.randSigned(r) * sep * 0.3));
        p.vanillaMove(sepVec.x, sepVec.y, sepVec.z);
        p.motionX = out.x;
        p.motionY = out.y;
        p.motionZ = out.z;
        p.syncToVanillaMotionOnly();
        return true;
    }

    private static boolean moveWater(@Nonnull BloodWater p, double dx, double dy, double dz) {
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

    private static void waterScale(@Nonnull BloodWater p) {
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
}

