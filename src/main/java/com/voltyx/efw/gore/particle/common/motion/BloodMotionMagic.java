package com.voltyx.efw.gore.particle.common.motion;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.motion.BloodMotion;
import com.voltyx.efw.gore.particle.state.BloodMagic;
import net.minecraft.world.phys.Vec3;
public class BloodMotionMagic {
    public static void prime(ParticleBlood p) {
        if (p == null) {
            return;
        }
        if (!BloodMagic.isMagic(p)) {
            return;
        }
        p.setCanCollide(true);
        p.setGravity(0.0f);
        double mx = p.motionX;
        double my = p.motionY;
        double mz = p.motionZ;
        double sp = Math.sqrt(mx * mx + my * my + mz * mz);
        BloodMagic.magicRt(p, sp);
    }

    public static void tick(ParticleBlood p) {
        if (p == null) {
            return;
        }
        if (!BloodMagic.isMagic(p)) {
            return;
        }
        if (p.isStuck) {
            return;
        }
        p.setCanCollide(true);
        p.setGravity(0.0f);
        p.idleTicks = 0;
        p.detachWobbleTicks = 0;
        p.noAirFlutter = true;
        double mx = p.motionX;
        double my = p.motionY;
        double mz = p.motionZ;
        double sp0 = Math.sqrt(mx * mx + my * my + mz * mz);
        BloodMagic.MagicRt rt = BloodMagic.magicRt(p, sp0);
        double initSp = Math.max(1.0E-9, rt.getInitSpeed());
        if (sp0 < 0.0012) {
            p.motionX = 0.0;
            p.motionY = 0.0;
            p.motionZ = 0.0;
            return;
        }
        double lifeT = (double)p.getAge() / (double)Math.max(1, p.getMaxAge());
        lifeT = Util.clamp(lifeT, 0.0, 1.0);
        double damp = 0.985 + -0.08499999999999996 * lifeT;
        damp = Util.clamp(damp, 0.0, 1.0);
        double sp = sp0 * damp;
        double inv = 1.0 / Math.max(1.0E-9, sp0);
        double dx = mx * inv;
        double dy = my * inv;
        double dz = mz * inv;
        float slow01 = BloodMagic.magicSlowProgress01(sp0, initSp);
        float slow = Util.smoothstep01(slow01);
        double steerRamp = Util.smoothstep01(Util.clamp01(((float)p.getAge() - 1.0f) / 8.0f));
        Vec3 launchAxis = BloodMotion.normalizeOr(new Vec3((double)rt.getAxisX(), (double)rt.getAxisY(), (double)rt.getAxisZ()), new Vec3(0.0, 1.0, 0.0));
        double axisLiftPerTick = 0.058 + 0.01 * (double)slow;
        double axisUpMix = Util.clamp(axisLiftPerTick * (1.0 + 7.0 * steerRamp), 0.0, 0.78);
        Vec3 axis = BloodMotion.normalizeOr(launchAxis.scale(1.0 - axisUpMix).add(0.0, axisUpMix, 0.0), launchAxis);
        Vec3 ref = Math.abs(axis.y) > 0.85 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 u = BloodMotion.normalizeOr(axis.cross(ref), new Vec3(1.0, 0.0, 0.0));
        Vec3 v = BloodMotion.normalizeOr(axis.cross(u), new Vec3(0.0, 0.0, 1.0));
        double orbitSpeed = 0.055 * (double)rt.getSwirlMul() + (double)rt.getSideSpin();
        double orbitAng = (double)rt.getOrbitPhase() + (double)p.getAge() * orbitSpeed;
        double co = Math.cos(orbitAng += 0.35 * Math.sin((double)p.getAge() * (double)rt.getJitterFreq() + (double)rt.getJitterPhase()));
        double si = Math.sin(orbitAng);
        Vec3 ring = u.scale(co).add(v.x * si, v.y * si, v.z * si);
        double cone = 0.032 * (0.3 + 0.7 * (double)slow) * (double)rt.getSideMul() * (0.25 + 0.75 * steerRamp);
        if ((cone += 0.0125 * (double)slow * Math.sin((double)p.getAge() * (double)rt.getJitterFreq() + (double)rt.getJitterPhase())) < 0.0) {
            cone = 0.0;
        }
        Vec3 desired = BloodMotion.normalizeOr(axis.add(ring.x * cone, ring.y * cone, ring.z * cone), axis);
        double follow = Util.clamp((0.08 + 0.14 * (double)slow) * steerRamp, 0.0, 0.3);
        Vec3 cur = new Vec3(dx, dy, dz);
        Vec3 blended = BloodMotion.normalizeOr(cur.scale(1.0 - follow).add(desired.x * follow, desired.y * follow, desired.z * follow), desired);
        dx = blended.x;
        dy = blended.y;
        dz = blended.z;
        p.motionX = dx * sp;
        p.motionY = dy * sp;
        p.motionZ = dz * sp;
        double sp1 = Math.sqrt(p.motionX * p.motionX + p.motionY * p.motionY + p.motionZ * p.motionZ);
        if (sp1 < 0.0012) {
            p.motionX = 0.0;
            p.motionY = 0.0;
            p.motionZ = 0.0;
        }
    }
}

