package com.voltyx.efw.gore.particle.render.parts;

import com.voltyx.efw.gore.particle.common.GoreUtil;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaHot;
import com.voltyx.efw.gore.particle.common.geometry.BloodGeometry;
import com.voltyx.efw.gore.particle.common.geometry.BloodGeometryFluid;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceAttach;
import com.voltyx.efw.gore.particle.render.BloodRender;
import com.voltyx.efw.gore.particle.state.BloodHeavy;
import com.voltyx.efw.gore.particle.state.BloodHotBlocks;
import com.voltyx.efw.gore.particle.state.liquid.BloodFluidSurfaceCache;
import com.voltyx.efw.gore.particle.state.liquid.BloodLiquidUtil;
import com.voltyx.efw.gore.particle.state.liquid.lava.BloodLava;
import com.voltyx.efw.gore.particle.state.liquid.lava.BloodLavaCache;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.voltyx.efw.gore.particle.common.GoreMaterial;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import com.voltyx.efw.gore.particle.render.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
public final class BloodLavaRendering {
    public static void renderParticle(BloodLava p, @Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        if (p == null || p.getParticleAlpha() <= 0.001f) {
            return;
        }
        boolean heavy = p.isHeavyInLava();
        float oldR = p.getParticleRed();
        float oldG = p.getParticleGreen();
        float oldB = p.getParticleBlue();
        float oldA = p.getParticleAlpha();
        float oldS = p.getParticleScale();
        float burn = BloodLavaRendering.burnProgress01(p, partialTicks);
        float lavaR = BloodLavaRendering.lerp(1.0f, 0.15f, burn);
        float lavaG = BloodLavaRendering.lerp(0.0f, 0.15f, burn);
        float lavaB = BloodLavaRendering.lerp(0.0f, 0.15f, burn);
        float var = Util.clamp01(p.getDriftSeed());
        float boil01 = heavy ? 0.0f : p.getBoilVisual01();
        float pulse = BloodLavaRendering.resolvePulse01(p, heavy, partialTicks);
        float clot = BloodLavaRendering.resolveClot01(p, heavy, partialTicks);
        float purple = 0.2f * (0.45f + 0.55f * var) * (0.2f + 0.8f * pulse);
        float brown = 0.12f * (0.25f + 0.75f * clot) * (0.15f + 0.85f * burn);
        float sat = 1.12f;
        lavaR = Util.clamp01(lavaR * 1.12f * (0.9f + 0.2f * pulse + 0.12f * boil01) - 0.28f * brown);
        lavaG = Util.clamp01(lavaG * 0.88f + (0.012f + 0.018f * (1.0f - var)) - 0.1f * brown);
        lavaB = Util.clamp01(lavaB * 0.92f + purple + 0.06f * brown);
        float dark = 0.18f * clot;
        lavaR *= 1.0f - 0.22f * dark;
        lavaG *= 1.0f - 0.32f * dark;
        lavaB *= 1.0f - 0.18f * dark;
        lavaR = Util.clamp01(lavaR);
        lavaG = Util.clamp01(lavaG);
        lavaB = Util.clamp01(lavaB);
        float heatMix = BloodLavaRendering.computeHeatTintMix01(burn, oldR, oldG, oldB);
        float tintR = BloodLavaRendering.applyHeatModChannel(oldR, lavaR, heatMix);
        float tintG = BloodLavaRendering.applyHeatModChannel(oldG, lavaG, heatMix);
        float tintB = BloodLavaRendering.applyHeatModChannel(oldB, lavaB, heatMix);
        float pulseAlphaMul = 1.0f;
        float pulseScaleMul = 1.0f;
        if (!heavy) {
            pulseAlphaMul = BloodLavaRendering.computeBillboardPulseAlphaMul(pulse, clot);
            pulseScaleMul = BloodLavaRendering.computeBillboardPulseScaleMul(pulse, clot);
        }
        if (BloodLavaRendering.tryRenderHeavyPreHit(p, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, oldA, oldS, pulseAlphaMul, pulseScaleMul, tintR, tintG, tintB)) {
            BloodLavaRendering.restoreState(p, oldR, oldG, oldB, oldA, oldS);
            return;
        }
        if (p.isOnSurface()) {
            BloodLavaRendering.renderSurfaceBillboard(p, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, oldA, oldS, boil01, pulseAlphaMul, pulseScaleMul, tintR, tintG, tintB);
            BloodLavaRendering.restoreState(p, oldR, oldG, oldB, oldA, oldS);
            return;
        }
        BloodLavaRendering.renderBillboardWithState(p, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, oldA * pulseAlphaMul, oldS * pulseScaleMul, tintR, tintG, tintB);
        BloodLavaRendering.restoreState(p, oldR, oldG, oldB, oldA, oldS);
    }

    public static void renderSurfaceDecal(BloodLava p, @Nonnull BufferBuilder buffer, float partialTicks) {
        double cz;
        if (p == null || !p.isOnSurface() || !p.isAlive()) {
            return;
        }
        TextureAtlasSprite sprite = p.getSprite();
        if (sprite == null) {
            return;
        }
        BloodLavaCache cache = p.getCache();
        if (cache == null || !cache.hasSurface()) {
            return;
        }
        float blend = BloodLavaRendering.getSurfaceBlend01(p, partialTicks);
        float baseAlpha = Util.clamp01(p.getParticleAlpha() * blend);
        if (baseAlpha <= 0.001f) {
            return;
        }
        boolean heavy = p.isHeavyInLava();
        if (BloodHeavy.shouldSkipLavaSurfaceDecal(p, partialTicks)) {
            return;
        }
        double cx = BloodLavaRendering.resolveSurfaceCenterX(p, heavy, partialTicks);
        BloodFluidSurfaceCache.SurfacePlane plane = cache.planeAt(cx, cz = BloodLavaRendering.resolveSurfaceCenterZ(p, heavy, partialTicks));
        if (plane == null) {
            return;
        }
        BloodFluidSurfaceCache.Basis basis = BloodGeometryFluid.buildBasis(plane.nx, plane.ny, plane.nz);
        if (basis == null) {
            return;
        }
        float burn = BloodLavaRendering.burnProgress01(p, partialTicks);
        float lavaR = BloodLavaRendering.lerp(1.0f, 0.15f, burn);
        float lavaG = BloodLavaRendering.lerp(0.0f, 0.15f, burn);
        float lavaB = BloodLavaRendering.lerp(0.0f, 0.15f, burn);
        float var = Util.clamp01(p.getDriftSeed());
        float pulse = BloodLavaRendering.resolvePulse01(p, heavy, partialTicks);
        float clot = BloodLavaRendering.resolveClot01(p, heavy, partialTicks);
        float boil01 = heavy ? 0.0f : p.getBoilVisual01();
        float purple = 0.2f * (0.45f + 0.55f * var) * (0.2f + 0.8f * pulse);
        float brown = 0.12f * (0.25f + 0.75f * clot) * (0.15f + 0.85f * burn);
        float sat = 1.12f;
        lavaR = Util.clamp01(lavaR * 1.12f * (0.92f + 0.18f * pulse + 0.08f * boil01) - 0.24f * brown);
        lavaG = Util.clamp01(lavaG * 0.86f + (0.01f + 0.02f * (1.0f - var)) - 0.1f * brown);
        lavaB = Util.clamp01(lavaB * 0.92f + purple + 0.06f * brown);
        float dark = 0.2f * clot;
        lavaR *= 1.0f - 0.22f * dark;
        lavaG *= 1.0f - 0.34f * dark;
        lavaB *= 1.0f - 0.18f * dark;
        float aMul = 1.0f + (heavy ? 0.0f : 0.18f * (pulse - 0.5f)) + 0.22f * clot;
        baseAlpha = Util.clamp01(baseAlpha * Util.clamp01(aMul));
        float renderScale = p.getSurfaceScale();
        float scaleMul = heavy ? BloodHeavy.lavaHeavyDecalScaleMul(clot) : 1.0f + 0.14f * (pulse - 0.5f) + 0.12f * clot;
        renderScale = Math.max(0.001f, renderScale * Math.max(0.1f, scaleMul));
        if (!heavy) {
            float out;
            float mul = p.getInterpAmalgMul(partialTicks);
            if (mul > 1.0f) {
                renderScale *= (float)Math.sqrt(mul);
                float alphaPow = 0.2f;
                baseAlpha *= (float)Math.pow(mul, -0.2f);
            }
            if ((out = p.getInterpAmalgOut01(partialTicks)) > 0.0f) {
                float keep = 1.0f - out;
                baseAlpha *= keep;
                renderScale *= 1.0f - 0.15f * out;
            }
            if ((baseAlpha = Util.clamp01(baseAlpha)) <= 0.001f) {
                return;
            }
            if (renderScale < 0.001f) {
                renderScale = 0.001f;
            }
        }
        int brightness = p.getBrightnessForRender(partialTicks);
        int lmHi = brightness >> 16 & 0xFFFF;
        int lmLo = brightness & 0xFFFF;
        double camX = BloodLava.getCameraX();
        double camY = BloodLava.getCameraY();
        double camZ = BloodLava.getCameraZ();
        double yPushBase = 4.2E-4 + p.getSurfaceOffset();
        float rot = BloodLavaRendering.getInterpSurfaceRot(p, partialTicks);
        float tSmooth = BloodLavaRendering.surfaceLifeSmooth01(p, partialTicks);
        float baseTintR = p.getParticleRed();
        float baseTintG = p.getParticleGreen();
        float baseTintB = p.getParticleBlue();
        if (heavy) {
            float dark01 = Util.clamp01(0.65f * tSmooth + 0.35f * burn);
            float mulD = 1.0f - 0.55f * dark01;
            lavaR = Util.clamp01(lavaR * mulD);
            lavaG = Util.clamp01(lavaG * mulD);
            lavaB = Util.clamp01(lavaB * mulD);
        }
        float heatMix = BloodLavaRendering.computeHeatTintMix01(burn, baseTintR, baseTintG, baseTintB);
        float tintR = BloodLavaRendering.applyHeatModChannel(baseTintR, lavaR, heatMix);
        float tintG = BloodLavaRendering.applyHeatModChannel(baseTintG, lavaG, heatMix);
        float tintB = BloodLavaRendering.applyHeatModChannel(baseTintB, lavaB, heatMix);
        if (heavy) {
            BloodLavaRendering.renderHeavySurfaceRimAndLobe(p, buffer, plane, basis, sprite, cx, cz, rot, renderScale, var, clot, baseAlpha, tintR, tintG, tintB, yPushBase, camX, camY, camZ, lmHi, lmLo);
        }
        if (!heavy) {
            BloodLavaRendering.renderSurfaceSmear(p, buffer, plane, basis, sprite, cx, cz, renderScale, var, baseAlpha, tintR, tintG, tintB, yPushBase, camX, camY, camZ, lmHi, lmLo);
        }
        if (!heavy) {
            BloodLavaRendering.renderSurfaceHalo(p, buffer, plane, basis, sprite, cx, cz, rot, renderScale, tSmooth, clot, baseAlpha, tintR, tintG, tintB, yPushBase, camX, camY, camZ, lmHi, lmLo);
        }
        BloodLavaRendering.renderFootprintQuadTwoPass(buffer, plane, basis, cx, cz, sprite, rot, renderScale, p.isFlipU(), baseAlpha, tintR, tintG, tintB, yPushBase, camX, camY, camZ, lmHi, lmLo);
        if (heavy) {
            BloodLavaRendering.renderHeavySurfaceCore(p, buffer, plane, basis, sprite, cx, cz, rot, renderScale, var, burn, tSmooth, clot, baseAlpha, tintR, tintG, tintB, yPushBase, camX, camY, camZ, lmHi, lmLo);
        }
        if (!heavy) {
            BloodLavaRendering.renderFleshSurfaceCore(p, buffer, plane, basis, sprite, cx, cz, rot, renderScale, var, pulse, clot, baseAlpha, tintR, tintG, tintB, yPushBase, camX, camY, camZ, lmHi, lmLo);
        }
    }

    private static boolean tryRenderHeavyPreHit(BloodLava p, @Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ, float oldA, float oldS, float pulseAlphaMul, float pulseScaleMul, float tintR, float tintG, float tintB) {
        if (!p.isHeavyInLava()) {
            return false;
        }
        return BloodHeavy.withLavaHeavyPreHitSegment(p, partialTicks, () -> BloodLavaRendering.renderBillboardWithState(p, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, oldA * pulseAlphaMul, oldS * pulseScaleMul, tintR, tintG, tintB));
    }

    private static void renderSurfaceBillboard(BloodLava p, @Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ, float oldA, float oldS, float boil01, float pulseAlphaMul, float pulseScaleMul, float tintR, float tintG, float tintB) {
        float boilMul;
        float blend = BloodLavaRendering.getSurfaceBlend01(p, partialTicks);
        float dropletMul = 1.0f - blend;
        float billboardMul = Math.max(dropletMul, boilMul = p.isHeavyInLava() ? 0.0f : Util.clamp01(boil01 * 0.45f));
        if (billboardMul <= 0.001f) {
            return;
        }
        boolean fromBoil = boilMul > dropletMul;
        float alpha = oldA * billboardMul * pulseAlphaMul;
        if (fromBoil && alpha > 0.55f) {
            alpha = 0.55f;
        }
        float scaleMul = 1.0f;
        if (fromBoil) {
            scaleMul = 1.05f + boil01 * 0.22f;
        }
        BloodLavaRendering.renderBillboardWithState(p, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, alpha, oldS * scaleMul * pulseScaleMul, tintR, tintG, tintB);
    }

    private static void renderBillboardWithState(BloodLava p, @Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ, float alpha, float scale, float tintR, float tintG, float tintB) {
        p.setParticleAlpha(Util.clamp01(alpha));
        p.setParticleScale(Math.max(0.001f, scale));
        BloodLavaRendering.renderVanillaWithOverlay(p, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, tintR, tintG, tintB);
    }

    private static void renderHeavySurfaceRimAndLobe(BloodLava p, @Nonnull BufferBuilder buffer, @Nonnull BloodFluidSurfaceCache.SurfacePlane plane, @Nonnull BloodFluidSurfaceCache.Basis basis, @Nonnull TextureAtlasSprite sprite, double cx, double cz, float rot, float renderScale, float var, float clot, float baseAlpha, float tintR, float tintG, float tintB, double yPushBase, double camX, double camY, double camZ, int lmHi, int lmLo) {
        float rimScale = Math.max(0.001f, renderScale * (1.1f + 0.18f * clot));
        float rimAlpha = Util.clamp01(baseAlpha * (0.55f + 0.2f * clot));
        float rimR = Util.clamp01(tintR * 0.55f);
        float rimG = Util.clamp01(tintG * 0.5f);
        float rimB = Util.clamp01(tintB * 0.55f);
        BloodLavaRendering.renderFootprintQuadTwoPass(buffer, plane, basis, cx, cz, sprite, rot + (var - 0.5f) * 0.22f, rimScale, p.isFlipU(), rimAlpha, rimR, rimG, rimB, yPushBase - 6.0E-5, camX, camY, camZ, lmHi, lmLo);
        int noiseSeed = p.getNoiseSeed();
        float ang = (float)(noiseSeed & 0xFFF) / 4096.0f * ((float)Math.PI * 2);
        double offDist = (0.04 + 0.03 * (double)clot) * (double)renderScale;
        double ox = Math.cos(ang) * offDist;
        double oz = Math.sin(ang) * offDist;
        float lobeScale = Math.max(0.001f, renderScale * (0.62f + 0.2f * clot));
        float lobeAlpha = Util.clamp01(baseAlpha * (0.45f + 0.25f * clot));
        float lobeR = Util.clamp01(tintR * 0.65f);
        float lobeG = Util.clamp01(tintG * 0.55f);
        float lobeB = Util.clamp01(tintB * 0.65f);
        BloodLavaRendering.renderFootprintQuadTwoPass(buffer, plane, basis, cx + ox, cz + oz, sprite, rot - 0.18f + (var - 0.5f) * 0.15f, lobeScale, p.isFlipU(), lobeAlpha, lobeR, lobeG, lobeB, yPushBase - 3.0E-5, camX, camY, camZ, lmHi, lmLo);
    }

    private static void renderSurfaceSmear(BloodLava p, @Nonnull BufferBuilder buffer, @Nonnull BloodFluidSurfaceCache.SurfacePlane plane, @Nonnull BloodFluidSurfaceCache.Basis basis, @Nonnull TextureAtlasSprite sprite, double cx, double cz, float renderScale, float var, float baseAlpha, float tintR, float tintG, float tintB, double yPushBase, double camX, double camY, double camZ, int lmHi, int lmLo) {
        double mz;
        double mx = p.getMotionX();
        double motionSq = mx * mx + (mz = p.getMotionZ()) * mz;
        if (motionSq <= 2.5E-7) {
            return;
        }
        double invMotion = 1.0 / Math.sqrt(motionSq);
        double dirX = mx * invMotion;
        double dirZ = mz * invMotion;
        double speed = Math.sqrt(motionSq);
        float speed01 = Util.clamp01((float)(speed / 0.007));
        double dist = 0.11 * (double)renderScale * (0.6 + 0.4 * (double)speed01);
        double sx = cx - dirX * dist;
        double sz = cz - dirZ * dist;
        float smearRot = (float)(Mth.atan2((double)mz, (double)mx) + (double)((var - 0.5f) * 0.25f));
        float smearScale = renderScale * 1.08f;
        float smearAlpha = Util.clamp01(baseAlpha * 0.38f * (0.35f + 0.65f * speed01));
        float smearR = Util.clamp01(tintR * 0.82f);
        float smearG = Util.clamp01(tintG * 0.78f);
        float smearB = Util.clamp01(tintB * 0.86f);
        BloodLavaRendering.renderFootprintQuadTwoPass(buffer, plane, basis, sx, sz, sprite, smearRot, smearScale, p.isFlipU(), smearAlpha, smearR, smearG, smearB, yPushBase + -1.8E-4, camX, camY, camZ, lmHi, lmLo);
    }

    private static void renderSurfaceHalo(BloodLava p, @Nonnull BufferBuilder buffer, @Nonnull BloodFluidSurfaceCache.SurfacePlane plane, @Nonnull BloodFluidSurfaceCache.Basis basis, @Nonnull TextureAtlasSprite sprite, double cx, double cz, float rot, float renderScale, float tSmooth, float clot, float baseAlpha, float tintR, float tintG, float tintB, double yPushBase, double camX, double camY, double camZ, int lmHi, int lmLo) {
        float haloScaleMul = 1.25f + 0.6f * tSmooth;
        float haloScale = Math.max(0.001f, renderScale * haloScaleMul);
        float haloAlpha = Util.clamp01(baseAlpha * 0.55f);
        if (haloAlpha > 0.65f) {
            haloAlpha = 0.65f;
        }
        float mix = Util.clamp01(0.55f * (0.65f + 0.35f * tSmooth));
        float haloR = BloodLavaRendering.lerp(tintR, 1.0f, mix);
        float haloG = BloodLavaRendering.lerp(tintG, 1.0f, mix);
        float haloB = BloodLavaRendering.lerp(tintB, 1.0f, mix);
        BloodLavaRendering.renderFootprintQuadTwoPass(buffer, plane, basis, cx, cz, sprite, rot + 0.17f, haloScale, p.isFlipU(), haloAlpha *= 0.9f - 0.3f * clot, haloR, haloG, haloB, yPushBase + -2.0E-4, camX, camY, camZ, lmHi, lmLo);
    }

    private static void renderHeavySurfaceCore(BloodLava p, @Nonnull BufferBuilder buffer, @Nonnull BloodFluidSurfaceCache.SurfacePlane plane, @Nonnull BloodFluidSurfaceCache.Basis basis, @Nonnull TextureAtlasSprite sprite, double cx, double cz, float rot, float renderScale, float var, float burn, float tSmooth, float clot, float baseAlpha, float tintR, float tintG, float tintB, double yPushBase, double camX, double camY, double camZ, int lmHi, int lmLo) {
        float emberFade = Util.clamp01(1.0f - tSmooth);
        emberFade *= emberFade;
        float coreScale = Math.max(0.001f, renderScale * (0.7f - 0.1f * burn));
        float coreAlpha = Util.clamp01(baseAlpha * (0.42f + 0.18f * clot) * emberFade);
        float coreR = Util.clamp01(tintR * 1.08f + 0.05f * emberFade);
        float coreG = Util.clamp01(tintG * 0.78f);
        float coreB = Util.clamp01(tintB * 0.72f);
        BloodLavaRendering.renderFootprintQuadTwoPass(buffer, plane, basis, cx, cz, sprite, rot - 0.12f + (var - 0.5f) * 0.12f, coreScale, p.isFlipU(), coreAlpha, coreR, coreG, coreB, yPushBase + 5.0E-5, camX, camY, camZ, lmHi, lmLo);
    }

    private static void renderFleshSurfaceCore(BloodLava p, @Nonnull BufferBuilder buffer, @Nonnull BloodFluidSurfaceCache.SurfacePlane plane, @Nonnull BloodFluidSurfaceCache.Basis basis, @Nonnull TextureAtlasSprite sprite, double cx, double cz, float rot, float renderScale, float var, float pulse, float clot, float baseAlpha, float tintR, float tintG, float tintB, double yPushBase, double camX, double camY, double camZ, int lmHi, int lmLo) {
        float coreScale = Math.max(0.001f, renderScale * 0.72f * (0.92f + 0.16f * pulse));
        float coreAlpha = Util.clamp01(baseAlpha * 0.78f * (0.8f + 0.3f * clot));
        if (coreAlpha > 0.98f) {
            coreAlpha = 0.98f;
        }
        float coreR = Util.clamp01(tintR * 0.7f);
        float coreG = Util.clamp01(tintG * 0.616f);
        float coreB = Util.clamp01(tintB * 0.7f + 0.04f * clot);
        BloodLavaRendering.renderFootprintQuadTwoPass(buffer, plane, basis, cx, cz, sprite, rot - (var - 0.5f) * 0.18f, coreScale, p.isFlipU(), coreAlpha, coreR, coreG, coreB, yPushBase + 1.8E-4, camX, camY, camZ, lmHi, lmLo);
    }

    private static double resolveSurfaceCenterX(BloodLava p, boolean heavy, float partialTicks) {
        return heavy ? BloodHeavy.lavaSurfaceDecalX(p, partialTicks) : p.getPrevPosX() + (p.getPosX() - p.getPrevPosX()) * (double)partialTicks;
    }

    private static double resolveSurfaceCenterZ(BloodLava p, boolean heavy, float partialTicks) {
        return heavy ? BloodHeavy.lavaSurfaceDecalZ(p, partialTicks) : p.getPrevPosZ() + (p.getPosZ() - p.getPrevPosZ()) * (double)partialTicks;
    }

    private static float resolvePulse01(BloodLava p, boolean heavy, float partialTicks) {
        return heavy ? BloodHeavy.lavaPulseStatic01(p) : BloodLavaRendering.fleshPulse01(p, partialTicks);
    }

    private static float resolveClot01(BloodLava p, boolean heavy, float partialTicks) {
        return heavy ? BloodHeavy.lavaLumpStatic01(p) : BloodLavaRendering.fleshClot01(p, partialTicks);
    }

    private static float computeBillboardPulseAlphaMul(float pulse, float clot) {
        float mul = 1.0f + 0.18f * (pulse - 0.5f) + 0.22f * clot;
        return Util.clamp01(mul);
    }

    private static float computeBillboardPulseScaleMul(float pulse, float clot) {
        float mul = 1.0f + 0.14f * (pulse - 0.5f) + 0.1f * clot;
        if (mul < 0.1f) {
            mul = 0.1f;
        }
        return mul;
    }

    private static void restoreState(BloodLava p, float r, float g, float b, float a, float s) {
        p.setParticleColor(r, g, b);
        p.setParticleAlpha(a);
        p.setParticleScale(s);
    }

    private static float fleshPulse01(BloodLava p, float partialTicks) {
        float age = (float)p.getAge() + partialTicks;
        int seed = p.getNoiseSeed();
        float x = (float)p.getPosX() * 0.95f;
        float z = (float)p.getPosZ() * 0.95f;
        float n = BloodLiquidUtil.noiseFbm(x + age * 0.05f, z - age * 0.04f, seed + 7001);
        float amp = 0.65f + 0.35f * (0.5f + 0.5f * n);
        float w = 0.68f * (0.8f + 0.4f * amp);
        float phase = age * w + (float)(seed & 0x3FF) * 0.006135923f;
        float s = 0.5f + 0.5f * Mth.sin((float)phase);
        s = Util.clamp01((s - 0.28f) / 0.72f);
        s = Util.smoothstep01(s);
        float pulse = s * amp;
        float sharp = Math.max(0.01f, 1.8f);
        pulse = (float)Math.pow(Util.clamp01(pulse), sharp);
        return Util.clamp01(pulse);
    }

    private static float fleshClot01(BloodLava p, float partialTicks) {
        float age = (float)p.getAge() + partialTicks;
        int seed = p.getNoiseSeed();
        float x = (float)p.getPosX() * 0.95f;
        float z = (float)p.getPosZ() * 0.95f;
        float n = 0.5f + 0.5f * BloodLiquidUtil.noiseFbm(x + age * 0.03f, z + age * 0.02f, seed + 9009);
        float gate = 0.55f;
        float t = (n - 0.55f) / Math.max(1.0E-6f, 0.45f);
        t = Util.clamp01(t);
        t = Util.smoothstep01(t);
        float burn = BloodLavaRendering.burnProgress01(p, partialTicks);
        float wet = Util.clamp01(1.0f - 0.75f * burn);
        return Util.clamp01(t * wet);
    }

    private static float getSurfaceBlend01(BloodLava p, float partialTicks) {
        if (!p.isOnSurface()) {
            return 0.0f;
        }
        if (p.isHeavyInLava()) {
            return 1.0f;
        }
        if (p.getSurfaceGrowStartAge() < 0) {
            return 1.0f;
        }
        float t = ((float)p.getAge() + partialTicks - (float)p.getSurfaceGrowStartAge()) / 3.0f;
        return Util.smoothstep01(t);
    }

    private static float getInterpSurfaceRot(BloodLava p, float partialTicks) {
        return p.getPrevSurfaceRot() + (p.getSurfaceRot() - p.getPrevSurfaceRot()) * partialTicks;
    }

    private static float surfaceLifeSmooth01(BloodLava p, float partialTicks) {
        float tSurf = 0.0f;
        if (p.getSurfaceGrowStartAge() >= 0) {
            int start = p.getSurfaceGrowStartAge();
            int end = Math.max(start + 1, p.getMaxAge());
            float cur = (float)p.getAge() + partialTicks - (float)start;
            float den = end - start;
            if (den > 1.0E-6f) {
                tSurf = cur / den;
            }
        }
        tSurf = Util.clamp01(tSurf);
        return Util.smoothstep01(tSurf);
    }

    private static float burnProgress01(BloodLava p, float partialTicks) {
        int start = p.getLavaStartAge();
        int end = Math.max(start + 1, p.getMaxAge());
        float ageF = (float)p.getAge() + partialTicks;
        float t = (ageF - (float)start) / (float)(end - start);
        t = Util.clamp01(t);
        float delay = Util.clamp01(0.08f);
        if (delay > 1.0E-6f && t > delay) {
            t = (t - delay) / Math.max(1.0E-6f, 1.0f - delay);
        } else if (t <= delay) {
            t = 0.0f;
        }
        return Util.smoothstep01(Util.clamp01(t));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static void renderVanillaWithOverlay(BloodLava p, @Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ, float tintR, float tintG, float tintB) {
        float oldR = p.getParticleRed();
        float oldG = p.getParticleGreen();
        float oldB = p.getParticleBlue();
        float targetA = p.getParticleAlpha();
        float overlayMul = BloodLavaRendering.computeOverlayAlphaMul(tintR, tintG, tintB);
        float passA0 = BloodLavaRendering.solveBasePassAlphaForTarget(targetA, overlayMul);
        float passA1 = Util.clamp01(passA0 * overlayMul);
        float darkMul = BloodLavaRendering.computeAdaptiveDarkMul(tintR, tintG, tintB);
        p.setParticleColor(Util.clamp01(tintR * darkMul), Util.clamp01(tintG * darkMul), Util.clamp01(tintB * darkMul));
        p.setParticleAlpha(passA0);
        p.renderVanillaBillboard(buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
        p.setParticleColor(Util.clamp01(tintR), Util.clamp01(tintG), Util.clamp01(tintB));
        p.setParticleAlpha(passA1);
        p.renderVanillaBillboard(buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
        p.setParticleColor(oldR, oldG, oldB);
        p.setParticleAlpha(targetA);
    }

    private static float computeOverlayAlphaMul(float tintR, float tintG, float tintB) {
        float darkProtect = BloodLavaRendering.darkColorProtect01(tintR, tintG, tintB);
        float overlay = BloodLavaRendering.lerp(0.85f, 0.96f, darkProtect);
        if (overlay < 0.85f) {
            overlay = 0.85f;
        }
        if (overlay > 0.96f) {
            overlay = 0.96f;
        }
        return overlay;
    }

    private static float solveBasePassAlphaForTarget(float targetAlpha, float overlayAlphaMul) {
        targetAlpha = Util.clamp01(targetAlpha);
        float overlay = Util.clamp01(overlayAlphaMul);
        if (overlay <= 0.0f) {
            return targetAlpha;
        }
        double b = -(1.0 + (double)overlay);
        double a = overlay;
        double c = targetAlpha;
        double disc = b * b - 4.0 * a * c;
        if (disc <= 0.0) {
            return targetAlpha;
        }
        double sqrt = Math.sqrt(disc);
        double x1 = (-b - sqrt) / (2.0 * a);
        double x2 = (-b + sqrt) / (2.0 * a);
        double x = x1 >= 0.0 && x1 <= 1.0 ? x1 : x2;
        x = Util.clamp(x, 0.0, 1.0);
        return (float)x;
    }

    private static void renderFootprintQuadTwoPass(@Nonnull BufferBuilder buffer, @Nonnull BloodFluidSurfaceCache.SurfacePlane plane, @Nonnull BloodFluidSurfaceCache.Basis basis, double cx, double cz, @Nonnull TextureAtlasSprite sprite, float rotRad, float quadScale, boolean flipU, float targetAlpha, float tintR, float tintG, float tintB, double yPush, double camX, double camY, double camZ, int lmHi, int lmLo) {
        if (quadScale <= 1.0E-6f) {
            return;
        }
        float overlayMul = BloodLavaRendering.computeOverlayAlphaMul(tintR, tintG, tintB);
        float passA0 = BloodLavaRendering.solveBasePassAlphaForTarget(targetAlpha, overlayMul);
        float passA1 = Util.clamp01(passA0 * overlayMul);
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
        float darkMul = BloodLavaRendering.computeAdaptiveDarkMul(tintR, tintG, tintB);
        BloodLavaRendering.emit4(buffer, plane, basis, cx, cz, ox, oz, uu, vv, Util.clamp01(tintR * darkMul), Util.clamp01(tintG * darkMul), Util.clamp01(tintB * darkMul), passA0, yPush, camX, camY, camZ, lmHi, lmLo, cos, sin);
        BloodLavaRendering.emit4(buffer, plane, basis, cx, cz, ox, oz, uu, vv, Util.clamp01(tintR), Util.clamp01(tintG), Util.clamp01(tintB), passA1, yPush, camX, camY, camZ, lmHi, lmLo, cos, sin);
    }

    private static float darkColorProtect01(float r, float g, float b) {
        float lum = BloodLavaRendering.luma01(r, g, b);
        float t = Util.clamp01((0.3f - lum) / 0.3f);
        return Util.smoothstep01(t);
    }

    private static void emit4(@Nonnull BufferBuilder buffer, @Nonnull BloodFluidSurfaceCache.SurfacePlane plane, @Nonnull BloodFluidSurfaceCache.Basis basis, double cx, double cz, double[] ox, double[] oz, float[] uu, float[] vv, float r, float g, float b, float a, double yPush, double camX, double camY, double camZ, int lmHi, int lmLo, double cos, double sin) {
        for (int i = 0; i < 4; ++i) {
            double rx = ox[i] * cos - oz[i] * sin;
            double rz = ox[i] * sin + oz[i] * cos;
            double dx = basis.t1x * rx + basis.t2x * rz;
            double dz = basis.t1z * rx + basis.t2z * rz;
            double wx = cx + dx;
            double wz = cz + dz;
            double wy = plane.yAt(wx, wz) + yPush;
            buffer.pos(wx - camX, wy - camY, wz - camZ).tex((double)uu[i], (double)vv[i]).color(r, g, b, a).lightmap(lmHi, lmLo).endVertex();
        }
    }

    private static float luma01(float r, float g, float b) {
        return Util.clamp01(0.2126f * Util.clamp01(r) + 0.7152f * Util.clamp01(g) + 0.0722f * Util.clamp01(b));
    }

    private static float saturation01(float r, float g, float b) {
        r = Util.clamp01(r);
        g = Util.clamp01(g);
        b = Util.clamp01(b);
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        if (max <= 1.0E-6f) {
            return 0.0f;
        }
        return Util.clamp01((max - min) / max);
    }

    private static float lightSatBalance01(float r, float g, float b) {
        float lum = BloodLavaRendering.luma01(r, g, b);
        float sat = BloodLavaRendering.saturation01(r, g, b);
        float invSat = 1.0f - sat;
        return Util.clamp01(0.62f * lum + 0.38f * invSat);
    }

    private static float computeHeatTintMix01(float burn01, float baseR, float baseG, float baseB) {
        burn01 = Util.clamp01(burn01);
        float balance = BloodLavaRendering.lightSatBalance01(baseR, baseG, baseB);
        float darkProtect = BloodLavaRendering.darkColorProtect01(baseR, baseG, baseB);
        float heat = (0.18f + 0.54f * burn01) * (1.0f - 0.35f * balance) * (1.0f - 0.4f * darkProtect);
        if ((heat = Util.clamp01(heat)) < 0.08f) {
            heat = 0.08f;
        }
        if (heat > 0.85f) {
            heat = 0.85f;
        }
        return heat;
    }

    private static float applyHeatModChannel(float baseChannel, float lavaChannel, float heatMix01) {
        baseChannel = Util.clamp01(baseChannel);
        lavaChannel = Util.clamp01(lavaChannel);
        float mod = BloodLavaRendering.lerp(1.0f, lavaChannel, Util.clamp01(heatMix01));
        return Util.clamp01(baseChannel * mod);
    }

    private static float computeAdaptiveDarkMul(float tintR, float tintG, float tintB) {
        float balance = BloodLavaRendering.lightSatBalance01(tintR, tintG, tintB);
        float darkProtect = BloodLavaRendering.darkColorProtect01(tintR, tintG, tintB);
        float base = BloodLavaRendering.lerp(0.68f, 0.82f, balance);
        float mul = BloodLavaRendering.lerp(base, 0.94f, darkProtect);
        if (mul < 0.68f) {
            mul = 0.68f;
        }
        if (mul > 0.94f) {
            mul = 0.94f;
        }
        return mul;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void renderHotBillboardFromBlood(@Nullable ParticleBlood p, @Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        float fleshMul;
        if (p == null || !p.isAlive() || !BloodHotBlocks.shouldRenderHotAirBillboard(p)) {
            return;
        }
        BloodHotBlocks.HotStyle style = BloodHotBlocks.resolveStyle(p);
        boolean heavy = style == BloodHotBlocks.HotStyle.HEAVY;
        float oldR = p.getRed();
        float oldG = p.getGreen();
        float oldB = p.getBlue();
        float oldA = p.getAlpha();
        float oldS = p.getScale();
        float burn = BloodAlphaHot.hotAlpha(p, partialTicks);
        float holdMul = BloodAlphaHot.hotAlpha(p, partialTicks);
        float targetAlpha = Util.clamp01(oldA * holdMul);
        if (targetAlpha <= 0.001f) {
            return;
        }
        float burst01 = BloodHotBlocks.hotBurstVisual01(p, partialTicks);
        float var = BloodLavaRendering.hotVar01(p);
        float pulse = BloodLavaRendering.hotPulse01(p, partialTicks, style);
        float clot = BloodLavaRendering.hotClot01(p, partialTicks, style, burn);
        float lavaR = BloodLavaRendering.lerp(1.0f, 0.15f, burn);
        float lavaG = BloodLavaRendering.lerp(0.0f, 0.15f, burn);
        float lavaB = BloodLavaRendering.lerp(0.0f, 0.15f, burn);
        switch (style) {
            case SLIMY: {
                fleshMul = 1.0f;
                break;
            }
            case HEAVY: {
                fleshMul = 0.85f;
                break;
            }
            case MAGIC: {
                fleshMul = 0.55f;
                break;
            }
            default: {
                fleshMul = 0.7f;
            }
        }
        float purple = 0.2f * fleshMul * (0.45f + 0.55f * var) * (0.2f + 0.8f * pulse);
        float brown = 0.12f * fleshMul * (0.25f + 0.75f * clot) * (0.15f + 0.85f * burn);
        float sat = 1.12f;
        lavaR = Util.clamp01(lavaR * 1.12f * (0.9f + 0.2f * pulse) - 0.28f * brown);
        lavaG = Util.clamp01(lavaG * 0.88f + (0.012f + 0.018f * (1.0f - var)) - 0.1f * brown);
        lavaB = Util.clamp01(lavaB * 0.92f + purple + 0.06f * brown);
        float dark = 0.18f * clot;
        lavaR *= 1.0f - 0.22f * dark;
        lavaG *= 1.0f - 0.32f * dark;
        lavaB *= 1.0f - 0.18f * dark;
        lavaR = Util.clamp01(lavaR);
        lavaG = Util.clamp01(lavaG);
        lavaB = Util.clamp01(lavaB);
        switch (style) {
            case HEAVY: {
                lavaR = Util.clamp01(lavaR * 0.78f);
                lavaG = Util.clamp01(lavaG * 0.6f);
                lavaB = Util.clamp01(lavaB * 0.68f);
                break;
            }
            case LIGHT: {
                lavaR = Util.clamp01(BloodLavaRendering.lerp(lavaR, 1.0f, 0.2f * (1.0f - burn)));
                lavaG = Util.clamp01(lavaG + 0.08f * (1.0f - burn));
                break;
            }
            case SLIMY: {
                lavaB = Util.clamp01(lavaB + 0.04f * clot);
                break;
            }
            case MAGIC: {
                lavaR = Util.clamp01(BloodLavaRendering.lerp(lavaR, 0.72f, 0.3f));
                lavaG = Util.clamp01(BloodLavaRendering.lerp(lavaG, 0.34f, 0.3f));
                lavaB = Util.clamp01(BloodLavaRendering.lerp(lavaB, 1.0f, 0.55f));
            }
        }
        float baseTintR = p.getTintR();
        float baseTintG = p.getTintG();
        float baseTintB = p.getTintB();
        float heatMix = BloodLavaRendering.computeHeatTintMix01(burn, baseTintR, baseTintG, baseTintB);
        float tintR = BloodLavaRendering.applyHeatModChannel(baseTintR, lavaR, heatMix);
        float tintG = BloodLavaRendering.applyHeatModChannel(baseTintG, lavaG, heatMix);
        float tintB = BloodLavaRendering.applyHeatModChannel(baseTintB, lavaB, heatMix);
        float renderScale = oldS;
        if (heavy) {
            renderScale *= 0.98f + 0.04f * burst01;
        } else {
            float pulseScaleMul = BloodLavaRendering.computeBillboardPulseScaleMul(pulse, clot);
            if (pulseScaleMul < 0.92f) {
                pulseScaleMul = 0.92f;
            }
            if (pulseScaleMul > 1.14f) {
                pulseScaleMul = 1.14f;
            }
            float burstScaleMul = 1.0f + 0.1f * burst01 * (style == BloodHotBlocks.HotStyle.SLIMY ? 1.1f : (style == BloodHotBlocks.HotStyle.MAGIC ? 0.95f : 0.85f));
            renderScale *= pulseScaleMul * burstScaleMul;
        }
        float renderAlpha = targetAlpha;
        if (!heavy) {
            renderAlpha *= 0.92f + 0.08f * pulse;
            if (style == BloodHotBlocks.HotStyle.SLIMY) {
                renderAlpha *= 0.94f + 0.06f * clot;
            }
            if ((renderAlpha = Util.clamp01(renderAlpha)) > targetAlpha) {
                renderAlpha = targetAlpha;
            }
        }
        if (renderAlpha <= 0.001f || renderScale <= 0.001f) {
            return;
        }
        try {
            if (!heavy) {
                BloodLavaRendering.renderBillboardLayerFromBlood(p, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, renderAlpha, renderScale, tintR, tintG, tintB);
                return;
            }
            BloodLavaRendering.renderBillboardLayerFromBlood(p, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, renderAlpha, renderScale, tintR, tintG, tintB);
            float coreAlpha = Util.clamp01(renderAlpha * (0.45f + 0.2f * clot));
            float coreScale = renderScale * 0.74f;
            float coreR = Util.clamp01(tintR * 1.05f + 0.04f * (1.0f - burn));
            float coreG = Util.clamp01(tintG * 0.76f);
            float coreB = Util.clamp01(tintB * 0.7f);
            BloodLavaRendering.renderBillboardLayerFromBlood(p, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, coreAlpha, coreScale, coreR, coreG, coreB);
        }
        finally {
            p.setScale(oldS);
            p.setRGBA(oldR, oldG, oldB, oldA);
        }
    }

    private static void renderBillboardLayerFromBlood(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ, float alpha, float scale, float tintR, float tintG, float tintB) {
        float targetAlpha = Util.clamp01(alpha);
        if (targetAlpha <= 0.001f) {
            return;
        }
        p.setScale(Math.max(0.001f, scale));
        float overlayMul = BloodLavaRendering.computeOverlayAlphaMul(tintR, tintG, tintB);
        float passA0 = BloodLavaRendering.solveBasePassAlphaForTarget(targetAlpha, overlayMul);
        float passA1 = Util.clamp01(passA0 * overlayMul);
        float darkMul = BloodLavaRendering.computeAdaptiveDarkMul(tintR, tintG, tintB);
        p.setRGBA(Util.clamp01(tintR * darkMul), Util.clamp01(tintG * darkMul), Util.clamp01(tintB * darkMul), passA0);
        p.vanillaRenderParticle(buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
        if (passA1 > 0.001f) {
            p.setRGBA(Util.clamp01(tintR), Util.clamp01(tintG), Util.clamp01(tintB), passA1);
            p.vanillaRenderParticle(buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
        }
    }

    public static void renderHotGroundFromBlood(@Nullable ParticleBlood p, @Nonnull BufferBuilder buffer, float partialTicks) {
        float fleshMul;
        if (p == null || !p.isAlive() || !p.isGroundTop()) {
            return;
        }
        if (!BloodHotBlocks.isHotGroundTopHost(p)) {
            return;
        }
        if (BloodLavaRendering.isViewEyeInWaterForHotGround(p.getParticleWorld(), partialTicks)) {
            return;
        }
        TextureAtlasSprite sprite = p.getSprite();
        if (sprite == null) {
            return;
        }
        BloodHotBlocks.HotStyle style = BloodHotBlocks.resolveStyle(p);
        if (style == BloodHotBlocks.HotStyle.MAGIC) {
            return;
        }
        Vec3 onPlane = BloodSurfaceAttach.anchorPoint(p);
        if (onPlane == null) {
            return;
        }
        float blend = BloodLavaRendering.getHotSurfaceBlend01(p, partialTicks);
        float burn = BloodAlphaHot.hotAlpha(p, partialTicks);
        float consume = BloodHotBlocks.groundConsume01(p, partialTicks);
        float holdMul = BloodAlphaHot.hotAlpha(p, partialTicks);
        if (holdMul <= 0.001f) {
            return;
        }
        float shrink01 = style == BloodHotBlocks.HotStyle.HEAVY ? 1.0f : holdMul;
        float baseAlpha = Util.clamp01(p.getAlpha() * blend * holdMul);
        if (baseAlpha <= 0.001f) {
            return;
        }
        float renderScale = BloodLavaRendering.hotBaseSurfaceScale(p, style);
        renderScale = Math.max(0.001f, renderScale * shrink01);
        float var = BloodLavaRendering.hotVar01(p);
        float pulse = BloodLavaRendering.hotPulse01(p, partialTicks, style);
        float clot = BloodLavaRendering.hotClot01(p, partialTicks, style, consume);
        HotFlowState flow = BloodLavaRendering.computeHotFlowState(p, style, consume, clot, partialTicks);
        float lavaR = BloodLavaRendering.lerp(1.0f, 0.15f, burn);
        float lavaG = BloodLavaRendering.lerp(0.0f, 0.15f, burn);
        float lavaB = BloodLavaRendering.lerp(0.0f, 0.15f, burn);
        switch (style) {
            case SLIMY: {
                fleshMul = 1.0f;
                break;
            }
            case HEAVY: {
                fleshMul = 0.85f;
                break;
            }
            default: {
                fleshMul = 0.7f;
            }
        }
        float purple = 0.2f * fleshMul * (0.45f + 0.55f * var) * (0.2f + 0.8f * pulse);
        float brown = 0.12f * fleshMul * (0.25f + 0.75f * clot) * (0.15f + 0.85f * burn);
        float sat = 1.12f;
        lavaR = Util.clamp01(lavaR * 1.12f * (0.92f + 0.18f * pulse) - 0.24f * brown);
        lavaG = Util.clamp01(lavaG * 0.86f + (0.01f + 0.02f * (1.0f - var)) - 0.1f * brown);
        lavaB = Util.clamp01(lavaB * 0.92f + purple + 0.06f * brown);
        float dark = 0.2f * clot;
        lavaR *= 1.0f - 0.22f * dark;
        lavaG *= 1.0f - 0.34f * dark;
        lavaB *= 1.0f - 0.18f * dark;
        if (style != BloodHotBlocks.HotStyle.HEAVY) {
            float alphaCap;
            float aMul = 1.0f + 0.18f * (pulse - 0.5f) + 0.22f * clot;
            float detailFade = BloodLavaRendering.hotDetailFade01(holdMul);
            float pulsedAlpha = Util.clamp01(baseAlpha * Util.clamp01(aMul));
            if (pulsedAlpha > (alphaCap = baseAlpha * (0.94f + 0.1f * detailFade))) {
                pulsedAlpha = alphaCap;
            }
            baseAlpha = pulsedAlpha;
            float scaleMul = 1.0f + 0.14f * (pulse - 0.5f) + 0.12f * clot;
            renderScale = Math.max(0.001f, renderScale * Math.max(0.1f, scaleMul));
        }
        lavaR = Util.clamp01(lavaR);
        lavaG = Util.clamp01(lavaG);
        lavaB = Util.clamp01(lavaB);
        if (style == BloodHotBlocks.HotStyle.HEAVY) {
            float dark01 = Util.clamp01(0.65f * consume + 0.35f * burn);
            float mulD = 1.0f - 0.55f * dark01;
            lavaR = Util.clamp01(lavaR * mulD);
            lavaG = Util.clamp01(lavaG * mulD);
            lavaB = Util.clamp01(lavaB * mulD);
        }
        if (baseAlpha <= 0.001f || renderScale <= 0.001f) {
            return;
        }
        switch (style) {
            case HEAVY: {
                lavaR = Util.clamp01(lavaR * 0.82f);
                lavaG = Util.clamp01(lavaG * 0.62f);
                lavaB = Util.clamp01(lavaB * 0.68f);
                break;
            }
            case LIGHT: {
                lavaR = Util.clamp01(BloodLavaRendering.lerp(lavaR, 1.0f, 0.18f * (1.0f - burn)));
                lavaG = Util.clamp01(lavaG + 0.08f * (1.0f - burn));
                break;
            }
            case SLIMY: {
                lavaB = Util.clamp01(lavaB + 0.04f * clot);
                break;
            }
        }
        float baseTintR = p.getTintR();
        float baseTintG = p.getTintG();
        float baseTintB = p.getTintB();
        float heatMix = BloodLavaRendering.computeHeatTintMix01(burn, baseTintR, baseTintG, baseTintB);
        float tintR = BloodLavaRendering.applyHeatModChannel(baseTintR, lavaR, heatMix);
        float tintG = BloodLavaRendering.applyHeatModChannel(baseTintG, lavaG, heatMix);
        float tintB = BloodLavaRendering.applyHeatModChannel(baseTintB, lavaB, heatMix);
        float rot = p.groundRot + flow.rotAdd;
        double cx = onPlane.x + flow.shiftX;
        double cz = onPlane.z + flow.shiftZ;
        double yPushBase = 4.2E-4 + p.surfaceOffset;
        float detailFade = BloodLavaRendering.hotDetailFade01(holdMul);
        if (style == BloodHotBlocks.HotStyle.HEAVY) {
            BloodLavaRendering.renderHeavyHotSurfaceRimAndLobe(p, buffer, partialTicks, sprite, cx, onPlane.y, cz, rot, renderScale, var, clot, baseAlpha, tintR, tintG, tintB, yPushBase, detailFade);
        }
        if (style == BloodHotBlocks.HotStyle.SLIMY) {
            BloodLavaRendering.renderHotSurfaceSmear(p, buffer, partialTicks, sprite, cx, onPlane.y, cz, renderScale, var, baseAlpha, tintR, tintG, tintB, yPushBase, flow, detailFade);
        }
        if (style != BloodHotBlocks.HotStyle.HEAVY) {
            BloodLavaRendering.renderHotSurfaceHalo(p, buffer, partialTicks, sprite, cx, onPlane.y, cz, rot, renderScale, consume, clot, baseAlpha, tintR, tintG, tintB, yPushBase, detailFade);
        }
        if (holdMul < 0.38f) {
            BloodLavaRendering.renderClippedFlatFootprintQuadSinglePassFromBlood(p, buffer, partialTicks, sprite, cx, onPlane.y, cz, rot, renderScale, p.flipU, baseAlpha, tintR, tintG, tintB, yPushBase);
        } else {
            BloodLavaRendering.renderClippedFlatFootprintQuadTwoPassFromBlood(p, buffer, partialTicks, sprite, cx, onPlane.y, cz, rot, renderScale, p.flipU, baseAlpha, tintR, tintG, tintB, yPushBase);
        }
        if (style == BloodHotBlocks.HotStyle.HEAVY) {
            BloodLavaRendering.renderHeavyHotSurfaceCore(p, buffer, partialTicks, sprite, cx, onPlane.y, cz, rot, renderScale, var, burn, consume, clot, baseAlpha, tintR, tintG, tintB, yPushBase, detailFade);
        } else {
            BloodLavaRendering.renderHotFleshSurfaceCore(p, buffer, partialTicks, sprite, cx, onPlane.y, cz, rot, renderScale, var, pulse, clot, baseAlpha, tintR, tintG, tintB, yPushBase, detailFade);
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private static boolean isViewEyeInWaterForHotGround(@Nullable Level expectedWorld, float partialTicks) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Entity view = mc != null ? mc.getCameraEntity() : null;
            if (view == null) return false;
            if (view.level() == null) return false;
            if (view.level() != expectedWorld) {
                return false;
            }
            try {
                if (view.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) {
                    return true;
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            try {
                BlockState viewpointState = GoreUtil.stateAtCamera();
                if (viewpointState != null && GoreMaterial.of(viewpointState) == GoreMaterial.WATER) {
                    return true;
                }
                Vec3 eye = view.getEyePosition(partialTicks);
                BlockPos eyePos = BlockPos.containing(eye.x, eye.y - 0.02, eye.z);
                if (view.level().isLoaded(eyePos)) {
                    boolean bl;
                    BlockState eyeState = BloodLavaRendering.safeGetStateForHotView(view.level(), eyePos);
                    if (eyeState != null && GoreMaterial.of(eyeState) == GoreMaterial.WATER) {
                        bl = true;
                        return bl;
                    }
                    bl = false;
                    return bl;
                }
            }
            catch (Throwable ignored) {
                return false;
            }
        }
        finally {
            return false;
        }
    }

    @Nullable
    private static BlockState safeGetStateForHotView(@Nullable Level world, @Nonnull BlockPos pos) {
        try {
            return world != null ? world.getBlockState(pos) : null;
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static float hotBaseSurfaceScale(@Nonnull ParticleBlood p, @Nonnull BloodHotBlocks.HotStyle style) {
        float mul;
        float base = p.getScale() * Math.max(1.0f, p.groundExtendMul);
        switch (style) {
            case HEAVY: {
                mul = 1.0f;
                break;
            }
            case SLIMY: {
                mul = 1.3299999f;
                break;
            }
            default: {
                mul = 1.4f;
            }
        }
        return Math.max(0.001f, base * mul);
    }

    private static float getHotSurfaceBlend01(@Nonnull ParticleBlood p, float partialTicks) {
        if (p.hotSurfaceStartAge < 0) {
            return 1.0f;
        }
        float t = ((float)p.getAge() + partialTicks - (float)p.hotSurfaceStartAge) / 3.0f;
        return Util.smoothstep01(Util.clamp01(t));
    }

    private static float hotVar01(@Nonnull ParticleBlood p) {
        return Util.clamp01((p.dripSeed - 0.75f) / 0.5f);
    }

    private static float hotPulse01(@Nonnull ParticleBlood p, float partialTicks, @Nonnull BloodHotBlocks.HotStyle style) {
        float age = (float)p.getAge() + partialTicks;
        int seed = BloodLavaRendering.hotSeedFor(p);
        float x = (float)p.posX * 0.95f;
        float z = (float)p.posZ * 0.95f;
        float n = BloodLiquidUtil.noiseFbm(x + age * 0.05f, z - age * 0.04f, seed + 7001);
        float amp = 0.65f + 0.35f * (0.5f + 0.5f * n);
        float w = 0.68f * (0.8f + 0.4f * amp);
        float phase = age * w + (float)(seed & 0x3FF) * 0.006135923f;
        float s = 0.5f + 0.5f * Mth.sin((float)phase);
        s = Util.clamp01((s - 0.28f) / 0.72f);
        s = Util.smoothstep01(s);
        float pulse = s * amp;
        pulse = (float)Math.pow(Util.clamp01(pulse), Math.max(0.01f, 1.8f));
        switch (style) {
            case HEAVY: {
                pulse = 0.3f + 0.45f * pulse;
                break;
            }
            case LIGHT: {
                pulse *= 0.85f;
                break;
            }
        }
        return Util.clamp01(pulse);
    }

    private static float hotClot01(@Nonnull ParticleBlood p, float partialTicks, @Nonnull BloodHotBlocks.HotStyle style, float consume01) {
        float age = (float)p.getAge() + partialTicks;
        int seed = BloodLavaRendering.hotSeedFor(p);
        float x = (float)p.posX * 0.95f;
        float z = (float)p.posZ * 0.95f;
        float n = 0.5f + 0.5f * BloodLiquidUtil.noiseFbm(x + age * 0.03f, z + age * 0.02f, seed + 9009);
        float gate = 0.55f;
        float t = (n - 0.55f) / Math.max(1.0E-6f, 0.45f);
        t = Util.clamp01(t);
        t = Util.smoothstep01(t);
        float wet = Util.clamp01(1.0f - 0.75f * consume01);
        float clot = Util.clamp01(t * wet);
        switch (style) {
            case LIGHT: {
                clot *= 0.55f;
                break;
            }
            case HEAVY: {
                clot *= 0.75f;
                break;
            }
        }
        return Util.clamp01(clot);
    }

    private static HotFlowState computeHotFlowState(@Nonnull ParticleBlood p, @Nonnull BloodHotBlocks.HotStyle style, float consume01, float clot01, float partialTicks) {
        float rotStyleMul;
        double shiftStyleMul;
        float timeMul;
        float age = (float)p.getAge() + partialTicks;
        int seed = BloodLavaRendering.hotSeedFor(p);
        float px = (float)p.posX;
        float pz = (float)p.posZ;
        switch (style) {
            case HEAVY: {
                timeMul = 0.18f;
                shiftStyleMul = 0.22;
                rotStyleMul = 0.03f;
                break;
            }
            case LIGHT: {
                timeMul = 0.32f;
                shiftStyleMul = 0.38;
                rotStyleMul = 0.05f;
                break;
            }
            case SLIMY: {
                timeMul = 0.48f;
                shiftStyleMul = 0.7;
                rotStyleMul = 0.08f;
                break;
            }
            default: {
                timeMul = 0.36f;
                shiftStyleMul = 0.3;
                rotStyleMul = 0.05f;
            }
        }
        float baseT = age * 0.22f * timeMul;
        float sx = px * 1.35f;
        float sz = pz * 1.35f;
        float fx = BloodLiquidUtil.noiseFbm(sx + baseT * 0.9f, sz - baseT * 0.55f, seed);
        float fz = BloodLiquidUtil.noiseFbm(sx - baseT * 0.65f, sz + baseT * 1.05f, seed + 1337);
        float spN = 0.5f + 0.5f * BloodLiquidUtil.noiseFbm(sx + 19.0f, sz - baseT * 0.4f, seed + 42);
        double dirX = fx;
        double dirZ = fz;
        double lenSq = dirX * dirX + dirZ * dirZ;
        if (lenSq <= 1.0E-12) {
            dirX = 1.0;
            dirZ = 0.0;
        } else {
            double inv = 1.0 / Math.sqrt(lenSq);
            dirX *= inv;
            dirZ *= inv;
        }
        double shiftMul = (0.003 + 0.006 * (double)spN) * (double)Math.max(0.001f, p.getScale()) * shiftStyleMul * (1.0 - 0.7 * (double)consume01);
        if (style != BloodHotBlocks.HotStyle.HEAVY) {
            shiftMul *= 1.0 + 0.35 * (double)clot01;
        }
        double shiftX = (double)fx * shiftMul;
        double shiftZ = (double)fz * shiftMul;
        float jt = age * 4.8f * 0.45f;
        float j = BloodLiquidUtil.noiseValue(sx * 3.6f + jt, sz * 3.6f - jt * 0.85f, seed + 900);
        float rotAdd = rotStyleMul * j;
        return new HotFlowState(shiftX, shiftZ, dirX, dirZ, Util.clamp01(spN), rotAdd);
    }

    private static void renderHeavyHotSurfaceRimAndLobe(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, float partialTicks, @Nonnull TextureAtlasSprite sprite, double cx, double planeY, double cz, float rot, float renderScale, float var, float clot, float baseAlpha, float tintR, float tintG, float tintB, double yPushBase, float detailFade) {
        if (detailFade <= 0.001f) {
            return;
        }
        float rimScale = Math.max(0.001f, renderScale * (1.03f + 0.08f * clot));
        float rimAlpha = Util.clamp01(baseAlpha * (0.5f + 0.16f * clot) * detailFade);
        float rimR = Util.clamp01(tintR * 0.55f);
        float rimG = Util.clamp01(tintG * 0.5f);
        float rimB = Util.clamp01(tintB * 0.55f);
        BloodLavaRendering.renderClippedFlatFootprintQuadSinglePassFromBlood(p, buffer, partialTicks, sprite, cx, planeY, cz, rot + (var - 0.5f) * 0.12f, rimScale, p.flipU, rimAlpha, rimR, rimG, rimB, yPushBase - 5.0E-5);
        int noiseSeed = BloodLavaRendering.hotSeedFor(p);
        float ang = (float)(noiseSeed & 0xFFF) / 4096.0f * ((float)Math.PI * 2);
        double offDist = (0.018 + 0.014 * (double)clot) * (double)renderScale * (double)detailFade;
        double ox = Math.cos(ang) * offDist;
        double oz = Math.sin(ang) * offDist;
        float lobeScale = Math.max(0.001f, renderScale * (0.52f + 0.12f * clot));
        float lobeAlpha = Util.clamp01(baseAlpha * (0.34f + 0.18f * clot) * detailFade);
        float lobeR = Util.clamp01(tintR * 0.65f);
        float lobeG = Util.clamp01(tintG * 0.55f);
        float lobeB = Util.clamp01(tintB * 0.65f);
        BloodLavaRendering.renderClippedFlatFootprintQuadSinglePassFromBlood(p, buffer, partialTicks, sprite, cx + ox, planeY, cz + oz, rot - 0.1f + (var - 0.5f) * 0.08f, lobeScale, p.flipU, lobeAlpha, lobeR, lobeG, lobeB, yPushBase - 2.0E-5);
    }

    private static void renderHotSurfaceSmear(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, float partialTicks, @Nonnull TextureAtlasSprite sprite, double cx, double planeY, double cz, float renderScale, float var, float baseAlpha, float tintR, float tintG, float tintB, double yPushBase, @Nonnull HotFlowState flow, float detailFade) {
        if (detailFade <= 0.001f) {
            return;
        }
        double dirX = flow.dirX;
        double dirZ = flow.dirZ;
        double dist = 0.11 * (double)renderScale * (0.6 + 0.4 * (double)flow.speed01) * (double)detailFade;
        double sx = cx - dirX * dist;
        double sz = cz - dirZ * dist;
        float smearRot = (float)(Math.atan2(dirZ, dirX) + (double)((var - 0.5f) * 0.25f));
        float smearScale = renderScale * 1.08f;
        float smearAlpha = Util.clamp01(baseAlpha * 0.38f * (0.35f + 0.65f * flow.speed01) * detailFade);
        float smearR = Util.clamp01(tintR * 0.82f);
        float smearG = Util.clamp01(tintG * 0.78f);
        float smearB = Util.clamp01(tintB * 0.86f);
        BloodLavaRendering.renderClippedFlatFootprintQuadSinglePassFromBlood(p, buffer, partialTicks, sprite, sx, planeY, sz, smearRot, smearScale, p.flipU, smearAlpha, smearR, smearG, smearB, yPushBase + -1.8E-4);
    }

    private static void renderHotSurfaceHalo(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, float partialTicks, @Nonnull TextureAtlasSprite sprite, double cx, double planeY, double cz, float rot, float renderScale, float consume, float clot, float baseAlpha, float tintR, float tintG, float tintB, double yPushBase, float detailFade) {
        if (detailFade <= 0.001f) {
            return;
        }
        float haloScaleMul = 1.25f + 0.6f * consume;
        float haloScale = Math.max(0.001f, renderScale * haloScaleMul);
        float haloAlpha = Util.clamp01(baseAlpha * 0.55f);
        if (haloAlpha > 0.65f) {
            haloAlpha = 0.65f;
        }
        haloAlpha *= 0.9f - 0.3f * clot;
        float mix = Util.clamp01(0.55f * (0.65f + 0.35f * consume));
        float haloR = BloodLavaRendering.lerp(tintR, 1.0f, mix);
        float haloG = BloodLavaRendering.lerp(tintG, 1.0f, mix);
        float haloB = BloodLavaRendering.lerp(tintB, 1.0f, mix);
        BloodLavaRendering.renderClippedFlatFootprintQuadSinglePassFromBlood(p, buffer, partialTicks, sprite, cx, planeY, cz, rot + 0.17f, haloScale, p.flipU, haloAlpha *= detailFade, haloR, haloG, haloB, yPushBase + -2.0E-4);
    }

    private static void renderHeavyHotSurfaceCore(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, float partialTicks, @Nonnull TextureAtlasSprite sprite, double cx, double planeY, double cz, float rot, float renderScale, float var, float burn, float consume, float clot, float baseAlpha, float tintR, float tintG, float tintB, double yPushBase, float detailFade) {
        if (detailFade <= 0.001f) {
            return;
        }
        float emberFade = Util.clamp01(1.0f - consume);
        emberFade *= emberFade;
        float coreScale = Math.max(0.001f, renderScale * (0.7f - 0.1f * burn));
        float coreAlpha = Util.clamp01(baseAlpha * (0.42f + 0.18f * clot) * emberFade * detailFade);
        float coreR = Util.clamp01(tintR * 1.08f + 0.05f * emberFade);
        float coreG = Util.clamp01(tintG * 0.78f);
        float coreB = Util.clamp01(tintB * 0.72f);
        BloodLavaRendering.renderClippedFlatFootprintQuadSinglePassFromBlood(p, buffer, partialTicks, sprite, cx, planeY, cz, rot - 0.12f + (var - 0.5f) * 0.12f, coreScale, p.flipU, coreAlpha, coreR, coreG, coreB, yPushBase + 5.0E-5);
    }

    private static void renderHotFleshSurfaceCore(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, float partialTicks, @Nonnull TextureAtlasSprite sprite, double cx, double planeY, double cz, float rot, float renderScale, float var, float pulse, float clot, float baseAlpha, float tintR, float tintG, float tintB, double yPushBase, float detailFade) {
        if (detailFade <= 0.001f) {
            return;
        }
        float coreScale = Math.max(0.001f, renderScale * 0.72f * (0.92f + 0.16f * pulse));
        float coreAlpha = Util.clamp01(baseAlpha * 0.78f * (0.8f + 0.3f * clot) * detailFade);
        if (coreAlpha > 0.98f) {
            coreAlpha = 0.98f;
        }
        float coreR = Util.clamp01(tintR * 0.7f);
        float coreG = Util.clamp01(tintG * 0.616f);
        float coreB = Util.clamp01(tintB * 0.7f + 0.04f * clot);
        BloodLavaRendering.renderClippedFlatFootprintQuadSinglePassFromBlood(p, buffer, partialTicks, sprite, cx, planeY, cz, rot - (var - 0.5f) * 0.18f, coreScale, p.flipU, coreAlpha, coreR, coreG, coreB, yPushBase + 1.8E-4);
    }

    private static float hotDetailFade01(float holdMul) {
        float t = (holdMul - 0.18f) / 0.62f;
        t = Util.clamp01(t);
        t = Util.smoothstep01(t);
        return t * t;
    }

    private static void renderClippedFlatFootprintQuadSinglePassFromBlood(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, float partialTicks, @Nonnull TextureAtlasSprite sprite, double cx, double planeY, double cz, float rotRad, float quadScale, boolean flipU, float targetAlpha, float tintR, float tintG, float tintB, double yPush) {
        if (quadScale <= 1.0E-6f) {
            return;
        }
        float alpha = Util.clamp01(targetAlpha);
        if (alpha <= 0.001f) {
            return;
        }
        List<Util.Vertex> poly = BloodGeometry.buildFlatFootprintQuad(sprite, cx, planeY, cz, rotRad, quadScale, flipU, yPush);
        if (poly == null || poly.size() < 3) {
            return;
        }
        List<List<Util.Vertex>> clipped = BloodGeometry.clipCustomDecalPolys(p, poly);
        if (clipped == null || clipped.isEmpty()) {
            return;
        }
        p.setRGBA(Util.clamp01(tintR), Util.clamp01(tintG), Util.clamp01(tintB), alpha);
        BloodRender.renderCustomPolyList(p, buffer, partialTicks, clipped);
    }

    private static void renderClippedFlatFootprintQuadTwoPassFromBlood(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, float partialTicks, @Nonnull TextureAtlasSprite sprite, double cx, double planeY, double cz, float rotRad, float quadScale, boolean flipU, float targetAlpha, float tintR, float tintG, float tintB, double yPush) {
        if (quadScale <= 1.0E-6f) {
            return;
        }
        List<Util.Vertex> poly = BloodGeometry.buildFlatFootprintQuad(sprite, cx, planeY, cz, rotRad, quadScale, flipU, yPush);
        if (poly == null || poly.size() < 3) {
            return;
        }
        List<List<Util.Vertex>> clipped = BloodGeometry.clipCustomDecalPolys(p, poly);
        if (clipped == null || clipped.isEmpty()) {
            return;
        }
        float overlayMul = BloodLavaRendering.computeOverlayAlphaMul(tintR, tintG, tintB);
        float passA0 = BloodLavaRendering.solveBasePassAlphaForTarget(targetAlpha, overlayMul);
        float passA1 = Util.clamp01(passA0 * overlayMul);
        float darkMul = BloodLavaRendering.computeAdaptiveDarkMul(tintR, tintG, tintB);
        p.setRGBA(Util.clamp01(tintR * darkMul), Util.clamp01(tintG * darkMul), Util.clamp01(tintB * darkMul), passA0);
        BloodRender.renderCustomPolyList(p, buffer, partialTicks, clipped);
        if (passA1 > 0.001f) {
            p.setRGBA(Util.clamp01(tintR), Util.clamp01(tintG), Util.clamp01(tintB), passA1);
            BloodRender.renderCustomPolyList(p, buffer, partialTicks, clipped);
        }
    }

    private static int hotSeedFor(@Nonnull ParticleBlood p) {
        int s = 1;
        s = 31 * s + Float.floatToIntBits(p.dripSeed);
        s = 31 * s + Float.floatToIntBits(p.groundRot);
        s = 31 * s + Float.floatToIntBits(p.spawnScale);
        if (p.stuckPos != null) {
            s = 31 * s + p.stuckPos.hashCode();
        }
        return s;
    }

    private static final class HotFlowState {
        final double shiftX;
        final double shiftZ;
        final double dirX;
        final double dirZ;
        final float speed01;
        final float rotAdd;

        private HotFlowState(double shiftX, double shiftZ, double dirX, double dirZ, float speed01, float rotAdd) {
            this.shiftX = shiftX;
            this.shiftZ = shiftZ;
            this.dirX = dirX;
            this.dirZ = dirZ;
            this.speed01 = speed01;
            this.rotAdd = rotAdd;
        }
    }
}

