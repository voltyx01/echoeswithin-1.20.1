package com.voltyx.efw.gore.particle.common.motion;

import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaCommon;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaLava;
import com.voltyx.efw.gore.particle.common.motion.BloodMotionHeavy;
import com.voltyx.efw.gore.particle.state.BloodHeavy;
import com.voltyx.efw.gore.particle.state.liquid.BloodFluidSurfaceCache;
import com.voltyx.efw.gore.particle.state.liquid.BloodLiquidUtil;
import com.voltyx.efw.gore.particle.state.liquid.lava.BloodLava;
import com.voltyx.efw.gore.particle.state.liquid.lava.BloodLavaCache;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
public class BloodMotionLava {
    public static void tick(BloodLava p) {
        boolean wasOnSurface = p.isOnSurface();
        boolean heavy = p.isHeavyInLava();
        p.copyPosToPrev();
        p.setPrevSurfaceRot(p.getSurfaceRot());
        p.setPrevParticleAngle(p.getParticleAngle());
        if (p.incrementAgeAndShouldExpire() || p.getParticleWorld() == null) {
            p.expireAndUntrack();
            return;
        }
        if (p.getBoilCooldownTicks() > 0) {
            p.setBoilCooldownTicks(p.getBoilCooldownTicks() - 1);
        }
        p.tickPopLockoutDown();
        p.trackInBloodPassIfNeeded();
        BloodMotionLava.advanceRotation(p);
        if (!BloodAlphaLava.updateLavaAlpha(p)) {
            return;
        }
        BloodLavaCache cache = p.getCache();
        cache.tickAndUpdate(p.getPosX(), p.getPosY(), p.getPosZ());
        if (!cache.hasSurface()) {
            BloodMotionLava.expireSurface(p);
            return;
        }
        BloodFluidSurfaceCache.SurfacePlane plane = cache.planeAt(p.getPosX(), p.getPosZ());
        if (plane == null) {
            BloodMotionLava.expireSurface(p);
            return;
        }
        if (!cache.isCachedAboveOpen()) {
            BloodMotionLava.tickCovered(p);
            return;
        }
        float heat = BloodMotionLava.heat01(p);
        if (BloodMotionLava.guardSubmerged(p, plane)) {
            return;
        }
        if (p.isOnSurface()) {
            BloodMotionLava.tickSurface(p, cache, plane, heat, wasOnSurface, heavy);
        } else {
            BloodMotionLava.tickAir(p, cache, plane, heat);
        }
    }

    private static void expireSurface(BloodLava p) {
        p.setOnSurface(false);
        p.untrackIfNeeded();
        p.resetBoilBurst();
        p.expireAndUntrack();
    }

    private static void tickCovered(BloodLava p) {
        p.setOnSurface(false);
        p.untrackIfNeeded();
        p.resetBoilBurst();
        p.setMotionX(p.getMotionX() * 0.8);
        p.setMotionZ(p.getMotionZ() * 0.8);
        p.setMotionY(0.0);
        BloodMotionLava.moveSolid(p, p.getMotionX(), 0.0, p.getMotionZ());
    }

    private static boolean guardSubmerged(BloodLava p, BloodFluidSurfaceCache.SurfacePlane plane) {
        double ySurfNow = plane.yAt(p.getPosX(), p.getPosZ());
        double yContactNow = ySurfNow - p.getCollHalfY();
        double depth = yContactNow - p.getPosY();
        if (!p.isOnSurface() && depth > 0.12) {
            p.setSubmergedTicks(p.getSubmergedTicks() + 1);
            if (depth > 0.4 || p.getSubmergedTicks() >= 3) {
                p.expireAndUntrack();
                return true;
            }
            p.setOnSurface(false);
            p.untrackIfNeeded();
            p.resetBoilBurst();
            p.setMotionX(p.getMotionX() * 0.55);
            p.setMotionZ(p.getMotionZ() * 0.55);
            p.setMotionY(0.0);
            BloodMotionLava.moveSolid(p, p.getMotionX(), 0.0, p.getMotionZ());
            return true;
        }
        p.setSubmergedTicks(0);
        return false;
    }

    private static void tickSurface(BloodLava p, BloodLavaCache cache, BloodFluidSurfaceCache.SurfacePlane plane, float heat, boolean wasOnSurface, boolean heavy) {
        p.clearSurfaceExitAlphaBlend();
        if (p.getSurfaceGrowStartAge() < 0) {
            p.setSurfaceGrowStartAge(p.getAge());
            float surfMul = BloodHeavy.lavaSurfaceScaleMul(heavy);
            float surfBase = Math.max(0.001f, p.getBaseScale() * surfMul);
            p.setSurfaceScale(surfBase);
            p.clearImpact();
            p.resetBoilBurst();
        }
        if (heavy) {
            p.resetBoilBurst();
            p.setBoilCooldownTicks(0);
        }
        if (!wasOnSurface) {
            p.setMotionX(p.getMotionX() * 0.2);
            p.setMotionZ(p.getMotionZ() * 0.2);
        }
        BloodMotionLava.evaporateScale(p);
        if (p.getSurfaceScale() <= 1.0E-4f) {
            p.expireAndUntrack();
            return;
        }
        BloodFluidSurfaceCache.SurfacePlane planeNow = cache.planeAt(p.getPosX(), p.getPosZ());
        if (planeNow == null) {
            planeNow = plane;
        }
        if (planeNow == null) {
            BloodMotionLava.expireSurface(p);
            return;
        }
        if (!heavy) {
            if (BloodMotionLava.boilCatapult(p, planeNow, heat)) {
                return;
            }
            if (BloodMotionLava.boilPop(p, planeNow, heat)) {
                return;
            }
            if (BloodMotionLava.surfaceJump(p, planeNow)) {
                return;
            }
            BloodMotionLava.startBoil(p, heat);
        }
        BloodMotionLava.surfaceFlow(p, planeNow, heat);
        double maxH = heavy ? 0.0035 : 0.007;
        BloodMotionLava.clampHorizontal(p, maxH);
        BloodMotionLava.moveSolid(p, p.getMotionX(), 0.0, p.getMotionZ());
        cache.tickAndUpdate(p.getPosX(), p.getPosY(), p.getPosZ());
        if (!cache.hasSurface()) {
            BloodMotionLava.expireSurface(p);
            return;
        }
        BloodFluidSurfaceCache.SurfacePlane plane2 = cache.planeAt(p.getPosX(), p.getPosZ());
        if (plane2 == null) {
            plane2 = planeNow;
        }
        double ySurf = plane2.yAt(p.getPosX(), p.getPosZ());
        double yPushBase = 4.2E-4 + p.getSurfaceOffset();
        if (heavy) {
            p.setMotionY(0.0);
            p.setParticlePos(p.getPosX(), ySurf + yPushBase, p.getPosZ());
            return;
        }
        double yContact = ySurf - p.getCollHalfY();
        double desiredY = yContact - 0.0015 + BloodMotionLava.surfaceBob(p);
        double vy = p.getMotionY();
        double err = desiredY - p.getPosY();
        vy += err * 0.58;
        double newY = p.getPosY() + (vy *= 0.6);
        double minY = desiredY - 0.01;
        double liftCap = 0.015;
        if (p.isBoiling()) {
            liftCap = 0.095 * (0.4 + 0.6 * (double)heat);
        }
        double maxY = desiredY + liftCap;
        if (newY < minY) {
            newY = minY;
            if (vy < 0.0) {
                vy = 0.0;
            }
        }
        if (newY > maxY) {
            newY = maxY;
            if (vy > 0.0) {
                vy = -vy * 0.22;
            }
        }
        p.setMotionY(vy);
        p.setParticlePos(p.getPosX(), newY, p.getPosZ());
    }

    private static void tickAir(BloodLava p, BloodLavaCache cache, BloodFluidSurfaceCache.SurfacePlane plane, float heat) {
        double landEps;
        BloodMotionLava.airTurbulence(p);
        p.setMotionX(p.getMotionX() * 0.9);
        p.setMotionZ(p.getMotionZ() * 0.9);
        p.setMotionY(p.getMotionY() * 0.985 - 0.085);
        double x0 = p.getPosX();
        double y0 = p.getPosY();
        double z0 = p.getPosZ();
        double dx = p.getMotionX();
        double dy = p.getMotionY();
        double dz = p.getMotionZ();
        double nx = x0 + dx;
        double ny = y0 + dy;
        double nz = z0 + dz;
        boolean heavy = p.isHeavyInLava();
        double yPushBase = 4.2E-4 + p.getSurfaceOffset();
        BlockPos top2 = cache.getCachedTop();
        if (top2 != null) {
            int nbx = Mth.floor((double)nx);
            int nbz = Mth.floor((double)nz);
            if (nbx != ((net.minecraft.core.Vec3i) top2).getX() || nbz != ((net.minecraft.core.Vec3i) top2).getZ()) {
                cache.forceScanAt(nx, y0, nz);
            }
        }
        if (!cache.hasSurface()) {
            BloodMotionLava.expireSurface(p);
            return;
        }
        if (!cache.isCachedAboveOpen()) {
            BloodMotionLava.tickCovered(p);
            return;
        }
        BloodFluidSurfaceCache.SurfacePlane planeN = cache.planeAt(nx, nz);
        if (planeN == null) {
            planeN = plane;
        }
        if (planeN == null) {
            BloodMotionLava.expireSurface(p);
            return;
        }
        double ySurfN = planeN.yAt(nx, nz);
        double yLockN = heavy ? ySurfN + yPushBase : ySurfN - p.getCollHalfY();
        double d = landEps = heavy ? 1.5E-4 : 0.006;
        if (ny <= yLockN + landEps) {
            if (heavy) {
                if (BloodMotionHeavy.landLava(p, cache, plane, planeN, x0, y0, z0, dx, dy, dz, nx, ny, nz)) {
                    return;
                }
                p.setParticlePos(nx, yLockN, nz);
                BloodMotionHeavy.settleLava(p, yLockN);
                return;
            }
            double lockYN = yLockN - 0.0015;
            p.setParticlePos(nx, lockYN, nz);
            BloodMotionLava.enterSurface(p, lockYN);
            return;
        }
        BloodMotionLava.moveSolid(p, dx, dy, dz);
    }

    private static void enterSurface(BloodLava p, double lockY) {
        boolean heavy = p.isHeavyInLava();
        p.setOnSurface(true);
        p.trackIfNeeded();
        p.clearSurfaceExitAlphaBlend();
        p.setSurfaceGrowStartAge(p.getAge());
        float surfMul = BloodHeavy.lavaSurfaceScaleMul(heavy);
        float surfBase = Math.max(0.001f, p.getBaseScale() * surfMul);
        p.setSurfaceScale(surfBase);
        if (!heavy) {
            double dirZ;
            double dirX;
            double mz;
            double mx = p.getMotionX();
            double hs = mx * mx + (mz = p.getMotionZ()) * mz;
            if (hs > 1.0E-12) {
                double inv = 1.0 / Math.sqrt(hs);
                dirX = mx * inv;
                dirZ = mz * inv;
            } else {
                double a = p.getRng().nextDouble() * Math.PI * 2.0;
                dirX = Math.cos(a);
                dirZ = Math.sin(a);
            }
            double def = (p.getRng().nextDouble() - 0.5) * 2.0 * 0.9;
            double c = Math.cos(def);
            double s = Math.sin(def);
            double dx = dirX * c - dirZ * s;
            double dz = dirX * s + dirZ * c;
            float spread = (float)(0.0042 + p.getRng().nextDouble() * 0.0026);
            p.setImpact((float)dx, (float)dz, spread);
            p.setMotionX(p.getMotionX() * 0.2);
            p.setMotionZ(p.getMotionZ() * 0.2);
        } else {
            p.clearImpact();
            p.setMotionX(0.0);
            p.setMotionZ(0.0);
        }
        p.setMotionY(0.0);
        p.resetBoilBurst();
        p.setParticlePos(p.getPosX(), lockY, p.getPosZ());
    }

    private static void startBoil(BloodLava p, float heat) {
        if (p.isHeavyInLava()) {
            return;
        }
        if (p.isBoiling()) {
            return;
        }
        if (p.getBoilCooldownTicks() > 0) {
            return;
        }
        if (heat < 0.12f) {
            return;
        }
        float px = (float)p.getPosX();
        float pz = (float)p.getPosZ();
        float t = (float)p.getAge() * 0.35f;
        float gate = 0.5f + 0.5f * BloodLiquidUtil.noiseFbm(px * 0.9f + t, pz * 0.9f - t * 0.8f, p.getNoiseSeed() + 9123);
        if (gate < 0.35f) {
            return;
        }
        float h2 = heat * heat;
        float chance = 0.085f * h2 * (0.35f + 0.65f * gate);
        if (p.getRng().nextFloat() >= chance) {
            return;
        }
        int total = 2 + p.getRng().nextInt(Math.max(1, 5));
        double a = p.getRng().nextDouble() * Math.PI * 2.0;
        float dx = (float)Math.cos(a);
        float dz = (float)Math.sin(a);
        p.startBoilBurst(total, dx, dz);
        int cd = 5 + p.getRng().nextInt(Math.max(1, 12));
        p.setBoilCooldownTicks(cd);
    }

    private static boolean boilCatapult(BloodLava p, BloodFluidSurfaceCache.SurfacePlane plane, float heat) {
        double lenSq;
        if (p.isHeavyInLava()) {
            return false;
        }
        if (p.getPopLockoutTicks() > 0) {
            return false;
        }
        if (heat < 0.45f) {
            return false;
        }
        float px = (float)p.getPosX();
        float pz = (float)p.getPosZ();
        float t = (float)p.getAge() * 0.55f;
        float gate = 0.5f + 0.5f * BloodLiquidUtil.noiseFbm(px * 1.35f + t, pz * 1.35f - t * 0.9f, p.getNoiseSeed() + 22001);
        if (gate < 0.74f) {
            return false;
        }
        float h3 = heat * heat * heat;
        float chance = 0.006f * h3 * (0.4f + 0.6f * gate);
        if (p.getRng().nextFloat() >= chance) {
            return false;
        }
        p.setPopLockoutTicks(28);
        BloodAlphaLava.startLavaExitFade(p);
        p.setOnSurface(false);
        p.untrackIfNeeded();
        p.resetBoilBurst();
        p.setSurfaceGrowStartAge(-1);
        p.clearImpact();
        p.setParticlePos(p.getPosX(), p.getPosY() + 0.095, p.getPosZ());
        double a = p.getRng().nextDouble() * Math.PI * 2.0;
        double dirX = Math.cos(a);
        double dirZ = Math.sin(a);
        double downX = plane.nx / Math.max(1.0E-6, plane.ny);
        double downZ = plane.nz / Math.max(1.0E-6, plane.ny);
        double dLenSq = downX * downX + downZ * downZ;
        if (dLenSq > 1.0E-12) {
            double inv = 1.0 / Math.sqrt(dLenSq);
            dirX = dirX * 0.65 + (downX *= inv) * 0.35;
            dirZ = dirZ * 0.65 + (downZ *= inv) * 0.35;
        }
        if ((lenSq = dirX * dirX + dirZ * dirZ) > 1.0E-12) {
            double inv = 1.0 / Math.sqrt(lenSq);
            dirX *= inv;
            dirZ *= inv;
        }
        double h = 0.03 + p.getRng().nextDouble() * 0.05500000000000001;
        double vy = 0.38 + p.getRng().nextDouble() * 0.24;
        p.setMotionX(p.getMotionX() + dirX * (h *= 0.7 + 0.3 * (double)heat));
        p.setMotionZ(p.getMotionZ() + dirZ * h);
        p.setMotionY(vy *= 0.7 + 0.3 * (double)heat);
        BloodMotionLava.moveSolid(p, p.getMotionX(), p.getMotionY(), p.getMotionZ());
        return true;
    }

    private static boolean boilPop(BloodLava p, BloodFluidSurfaceCache.SurfacePlane plane, float heat) {
        if (p.isHeavyInLava()) {
            return false;
        }
        if (p.getPopLockoutTicks() > 0) {
            return false;
        }
        if (heat < 0.3f) {
            return false;
        }
        float px = (float)p.getPosX();
        float pz = (float)p.getPosZ();
        float t = (float)p.getAge() * 0.42f;
        float gate = 0.5f + 0.5f * BloodLiquidUtil.noiseFbm(px * 1.1f - t, pz * 1.1f + t * 0.7f, p.getNoiseSeed() + 13337);
        if (gate < 0.62f) {
            return false;
        }
        float h2 = heat * heat;
        float chance = 0.014f * h2 * (0.4f + 0.6f * gate);
        if (p.getRng().nextFloat() >= chance) {
            return false;
        }
        p.setPopLockoutTicks(14);
        BloodAlphaLava.startLavaExitFade(p);
        p.setOnSurface(false);
        p.untrackIfNeeded();
        p.resetBoilBurst();
        p.setSurfaceGrowStartAge(-1);
        p.clearImpact();
        p.setParticlePos(p.getPosX(), p.getPosY() + 0.07, p.getPosZ());
        double vy = 0.16 + p.getRng().nextDouble() * 0.13999999999999999;
        double jx = (p.getRng().nextDouble() - 0.5) * 2.0 * 0.014;
        double jz = (p.getRng().nextDouble() - 0.5) * 2.0 * 0.014;
        p.setMotionY(vy *= 0.7 + 0.3 * (double)heat);
        p.setMotionX(p.getMotionX() + (jx += -plane.nx * 0.0045));
        p.setMotionZ(p.getMotionZ() + (jz += -plane.nz * 0.0045));
        BloodMotionLava.moveSolid(p, p.getMotionX(), p.getMotionY(), p.getMotionZ());
        return true;
    }

    private static boolean surfaceJump(BloodLava p, BloodFluidSurfaceCache.SurfacePlane plane) {
        if (p.isHeavyInLava()) {
            return false;
        }
        int interval = Math.max(1, 5);
        int offset = (p.getNoiseSeed() & Integer.MAX_VALUE) % interval;
        if ((p.getAge() + offset) % interval != 0) {
            return false;
        }
        if (p.getRng().nextFloat() >= 0.02f) {
            return false;
        }
        BloodAlphaLava.startLavaExitFade(p);
        p.setOnSurface(false);
        p.untrackIfNeeded();
        p.resetBoilBurst();
        p.setSurfaceGrowStartAge(-1);
        p.clearImpact();
        p.setParticlePos(p.getPosX(), p.getPosY() + 0.06, p.getPosZ());
        double vyMin = 0.14;
        double vyMax = Math.max(vyMin, 0.26);
        double vy = vyMin + p.getRng().nextDouble() * (vyMax - vyMin);
        double hMin = 0.01;
        double hMax = Math.max(hMin, 0.028);
        double h = hMin + p.getRng().nextDouble() * (hMax - hMin);
        double a = p.getRng().nextDouble() * Math.PI * 2.0;
        double dirX = Math.cos(a);
        double dirZ = Math.sin(a);
        double sx = -plane.nx * 0.0045;
        double sz = -plane.nz * 0.0045;
        p.setMotionX(p.getMotionX() + dirX * h + sx);
        p.setMotionZ(p.getMotionZ() + dirZ * h + sz);
        p.setMotionY(vy);
        BloodMotionLava.moveSolid(p, p.getMotionX(), p.getMotionY(), p.getMotionZ());
        return true;
    }

    private static void surfaceFlow(BloodLava p, BloodFluidSurfaceCache.SurfacePlane plane, float heat) {
        double len;
        int since;
        boolean heavy = p.isHeavyInLava();
        float age = p.getAge();
        int seed = p.getNoiseSeed();
        float px = (float)p.getPosX();
        float pz = (float)p.getPosZ();
        float clot = BloodMotionLava.fleshClot(p, heat, px, pz, age);
        float baseT = age * 0.22f;
        float t = heavy ? baseT * 0.55f : baseT;
        float sx = px * 1.35f;
        float sz = pz * 1.35f;
        float fx = BloodLiquidUtil.noiseFbm(sx + t * 0.9f, sz - t * 0.55f, seed);
        float fz = BloodLiquidUtil.noiseFbm(sx - t * 0.65f, sz + t * 1.05f, seed + 1337);
        float spN = 0.5f + 0.5f * BloodLiquidUtil.noiseFbm(sx + 19.0f, sz - t * 0.4f, seed + 42);
        double baseSpeed = 0.0038 * (double)p.getFlowSpeedMul();
        double varSpeed = 0.0026 * (double)p.getFlowSpeedMul();
        double speed = baseSpeed + varSpeed * (double)spN;
        if (heavy) {
            speed *= 0.45;
        }
        double vxTarget = (double)fx * (speed *= 1.0 - 0.18 * (double)clot);
        double vzTarget = (double)fz * speed;
        if (Math.abs(plane.ny) > 1.0E-6) {
            double downhillX = plane.nx / plane.ny;
            double downhillZ = plane.nz / plane.ny;
            vxTarget += downhillX * 5.5E-4;
            vzTarget += downhillZ * 5.5E-4;
        }
        if ((since = p.getAge() - p.getSurfaceGrowStartAge()) >= 0 && since < 8 && p.getImpactSpeed() > 0.0f) {
            double k = 1.0 - (double)since / 8.0;
            k *= k;
            vxTarget += (double)p.getImpactDirX() * (double)p.getImpactSpeed() * k;
            vzTarget += (double)p.getImpactDirZ() * (double)p.getImpactSpeed() * k;
            if (since == 7) {
                p.clearImpact();
            }
        }
        double steer = 0.3;
        if ((steer *= 1.0 - 0.35 * (double)clot) < 0.06) {
            steer = 0.06;
        }
        p.setMotionX(p.getMotionX() + (vxTarget - p.getMotionX()) * steer);
        p.setMotionZ(p.getMotionZ() + (vzTarget - p.getMotionZ()) * steer);
        if (clot > 0.001f && (len = Math.sqrt(vxTarget * vxTarget + vzTarget * vzTarget)) > 1.0E-12) {
            double inv = 1.0 / len;
            double ux = vxTarget * inv;
            double uz = vzTarget * inv;
            double curl = 2.2E-4 * (double)clot * (0.35 + 0.65 * (double)spN);
            p.setMotionX(p.getMotionX() + -uz * curl);
            p.setMotionZ(p.getMotionZ() + ux * curl);
        }
        float jt = age * 4.8f;
        float jx = BloodLiquidUtil.noiseValue(sx * 3.6f + jt, sz * 3.6f - jt * 0.85f, seed + 900);
        float jz = BloodLiquidUtil.noiseValue(sx * 3.6f - jt * 0.7f, sz * 3.6f + jt, seed + 901);
        double jitterMul = 1.0;
        jitterMul *= 1.0 + 1.15 * (double)clot * (0.35 + 0.65 * (double)heat);
        if (p.isBoiling()) {
            float left = p.getBoilBurstTicks();
            float tot = Math.max(1, p.getBoilBurstTotal());
            float phase = left / tot;
            float k = phase * phase;
            double hk = (0.0048 + p.getRng().nextDouble() * 0.0032) * (double)k * (0.55 + 0.45 * (double)heat);
            double pxh = -p.getBoilDirZ();
            double pzh = p.getBoilDirX();
            double perp = (p.getRng().nextDouble() - 0.5) * 2.0 * 0.45;
            p.setMotionX(p.getMotionX() + (double)p.getBoilDirX() * hk + pxh * hk * perp);
            p.setMotionZ(p.getMotionZ() + (double)p.getBoilDirZ() * hk + pzh * hk * perp);
            boolean firstTick = p.getBoilBurstTicks() == p.getBoilBurstTotal();
            double up = 0.014 + p.getRng().nextDouble() * 0.024;
            up *= 0.7 + 0.3 * (double)heat;
            if (!firstTick) {
                up *= 0.55 * (double)k;
            }
            p.setMotionY(p.getMotionY() + up);
            jitterMul *= 2.2;
            p.tickBoilBurstDown();
        }
        double ja = 2.2E-4 * (double)p.getTurbMul() * jitterMul;
        if (heavy) {
            ja *= 0.35;
        }
        p.setMotionX(p.getMotionX() + (double)jx * ja);
        p.setMotionZ(p.getMotionZ() + (double)jz * ja);
        double visc = p.getSurfaceVisc();
        visc *= 1.0 - 0.12 * (double)clot;
        visc = BloodLiquidUtil.clamp(visc, 0.55, 0.99);
        if (p.isBoiling()) {
            float boil01 = p.getBoilVisual01();
            visc += (0.96 - visc) * (0.55 * (double)boil01);
        }
        if (heavy) {
            visc = BloodLiquidUtil.clamp(visc * 0.9, 0.55, 0.99);
        }
        p.setMotionX(p.getMotionX() * visc);
        p.setMotionZ(p.getMotionZ() * visc);
        BloodMotionLava.yieldDrag(p, clot);
    }

    private static float fleshClot(BloodLava p, float heat, float px, float pz, float age) {
        float x = px * 0.95f;
        float z = pz * 0.95f;
        float n = 0.5f + 0.5f * BloodLiquidUtil.noiseFbm(x + age * 0.035f, z - age * 0.028f, p.getNoiseSeed() + 5001);
        float gate = 0.55f;
        float g = (n - gate) / Math.max(1.0E-6f, 1.0f - gate);
        g = BloodLiquidUtil.clamp01(g);
        g = BloodLiquidUtil.smoothstep01(g);
        float burn = BloodLiquidUtil.clamp01(1.0f - heat);
        float wet = BloodLiquidUtil.clamp01(1.0f - 0.6f * burn);
        return BloodLiquidUtil.clamp01(g * wet);
    }

    private static void yieldDrag(BloodLava p, float clot) {
        double vz;
        double vx = p.getMotionX();
        double magSq = vx * vx + (vz = p.getMotionZ()) * vz;
        if (magSq < 1.0E-18) {
            p.setMotionX(0.0);
            p.setMotionZ(0.0);
            return;
        }
        double mag = Math.sqrt(magSq);
        double yield = 1.0E-4 + 3.2E-4 * (double)clot;
        float boil01 = p.getBoilVisual01();
        if (boil01 > 0.0f) {
            yield *= 1.0 - 0.7 * (double)boil01;
        }
        if (mag <= yield) {
            p.setMotionX(0.0);
            p.setMotionZ(0.0);
            return;
        }
        double newMag = mag - yield;
        double m = newMag / mag;
        p.setMotionX(vx * m);
        p.setMotionZ(vz * m);
    }

    private static double surfaceBob(BloodLava p) {
        if (p.isHeavyInLava()) {
            return 0.0;
        }
        float age = p.getAge();
        int seed = p.getNoiseSeed();
        float px = (float)p.getPosX();
        float pz = (float)p.getPosZ();
        float t = age * 0.75f;
        float n = BloodLiquidUtil.noiseFbm(px * 0.95f + t, pz * 0.95f - t * 1.12f, seed + 777);
        return (double)n * 0.0012;
    }

    private static void airTurbulence(BloodLava p) {
        float age = p.getAge();
        float t = age * 0.45f;
        float x = (float)p.getPosX() * 0.85f;
        float z = (float)p.getPosZ() * 0.85f;
        int seed = p.getNoiseSeed();
        float nx = BloodLiquidUtil.noiseFbm(x + t, z - t * 0.7f, seed + 11);
        float nz = BloodLiquidUtil.noiseFbm(x - t * 0.6f, z + t, seed + 29);
        double a = 2.6E-4 * (double)p.getTurbMul();
        p.setMotionX(p.getMotionX() + (double)nx * a);
        p.setMotionZ(p.getMotionZ() + (double)nz * a);
    }

    private static void advanceRotation(BloodLava p) {
        if (p.getSurfaceRotSpeed() == 0.0f) {
            return;
        }
        float r = p.getSurfaceRot() + p.getSurfaceRotSpeed();
        p.setSurfaceRot(r);
        p.setParticleAngle(r);
    }

    private static float shrink01(BloodLava p) {
        return BloodAlphaCommon.lifeRemain(p.getAge(), p.getMaxAge(), p.getSurfaceGrowStartAge());
    }

    private static void evaporateScale(BloodLava p) {
        float shrink01 = BloodMotionLava.shrink01(p);
        if (p.isHeavyInLava()) {
            shrink01 = (float)Math.pow(BloodLiquidUtil.clamp01(shrink01), 0.8f);
        }
        boolean heavy = p.isHeavyInLava();
        float surfMul = BloodHeavy.lavaSurfaceScaleMul(heavy);
        float baseSurf = Math.max(0.001f, p.getBaseScale() * surfMul);
        p.setSurfaceScale(Math.max(0.0f, baseSurf * shrink01));
    }

    private static void clampHorizontal(BloodLava p, double maxH) {
        double maxSq;
        double hs = p.getMotionX() * p.getMotionX() + p.getMotionZ() * p.getMotionZ();
        if (hs > (maxSq = maxH * maxH)) {
            double m = maxH / Math.sqrt(hs);
            p.setMotionX(p.getMotionX() * m);
            p.setMotionZ(p.getMotionZ() * m);
        }
    }

    private static boolean moveSolid(BloodLava p, double dx, double dy, double dz) {
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

    private static float heat01(BloodLava p) {
        float burn = BloodMotionLava.burn01(p);
        return BloodLiquidUtil.clamp01(1.0f - burn);
    }

    private static float burn01(BloodLava p) {
        int start = p.getLavaStartAge();
        int end = Math.max(start + 1, p.getMaxAge());
        float ageF = p.getAge();
        float t = (ageF - (float)start) / (float)(end - start);
        t = BloodLiquidUtil.clamp01(t);
        float d = BloodLiquidUtil.clamp01(0.08f);
        if (d > 1.0E-6f && t > d) {
            t = (t - d) / Math.max(1.0E-6f, 1.0f - d);
        } else if (t <= d) {
            t = 0.0f;
        }
        return BloodLiquidUtil.smoothstep01(BloodLiquidUtil.clamp01(t));
    }
}

