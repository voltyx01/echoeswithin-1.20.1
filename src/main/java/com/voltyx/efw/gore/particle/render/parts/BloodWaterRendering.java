package com.voltyx.efw.gore.particle.render.parts;

import com.voltyx.efw.gore.particle.LegacyParticle;

import com.voltyx.efw.gore.particle.common.GoreUtil;

import com.voltyx.efw.gore.particle.common.cache.BloodCaches;
import com.voltyx.efw.gore.particle.common.cache.BloodCachesWater;
import com.voltyx.efw.gore.particle.common.geometry.BloodGeometryFluid;
import com.voltyx.efw.gore.particle.common.geometry.BloodGeometryWater;
import com.voltyx.efw.gore.particle.render.BloodRenderType;
import com.voltyx.efw.gore.particle.state.BloodHeavy;
import com.voltyx.efw.gore.particle.state.liquid.BloodFluidSurfaceCache;
import com.voltyx.efw.gore.particle.state.liquid.BloodLiquidUtil;
import com.voltyx.efw.gore.particle.state.liquid.water.BloodWater;
import com.voltyx.efw.gore.particle.state.liquid.water.BloodWaterCache;
import com.voltyx.efw.gore.particle.state.liquid.water.BloodWaterUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.LiquidBlock;
import com.voltyx.efw.gore.particle.common.GoreMaterial;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import com.voltyx.efw.gore.particle.render.BufferBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.client.renderer.BiomeColors;
public final class BloodWaterRendering {
    private static final float ALPHA_EPS = 1.0E-6f;
    private static final double RAY_LEN_EPS_SQ = 1.0E-12;
    private static final double EDGE_PARAM_EPS = 1.0E-12;
    private static final double DDA_TIE_EPS = 1.0E-10;
    private static final int CUTOUT_BISECT_STEPS = 6;
    private static final double END_RAY_Y_BIAS = 0.001;
    private static final double WATER_HIT_PAD = 0.002;
    private static final double CUTOUT_FINE_DIST_SQ = 196.0;
    private static final int CUTOUT_VISIBLE = 0;
    private static final int CUTOUT_HIDDEN = 1;
    private static final int CUTOUT_MIXED = 2;
    private static final float DECAL_TRAIL_ALPHA_MUL = 0.85f;
    private static final boolean SURFACE_HALO_ENABLED = true;
    private static final float SURFACE_HALO_SCALE_MIN_MUL = 1.25f;
    private static final float SURFACE_HALO_SCALE_MAX_MUL = 1.85f;
    private static final float SURFACE_HALO_ALPHA_MUL = 1.35f;
    private static final float SURFACE_HALO_ALPHA_CAP = 0.9f;
    private static final double SURFACE_HALO_Y_PUSH_ADD = -2.0E-4;
    private static final double UNDERWATER_DECAL_SIDE_PUSH = 0.012;
    private static final int UNDERWATER_TINT_FALLBACK_RGB = 4159204;
    private static final float UNDERWATER_TINT_STRENGTH_BILLBOARD = 0.52f;
    private static final float UNDERWATER_TINT_STRENGTH_DECAL = 0.4f;
    private static final float UNDERWATER_KEEP_R = 0.54f;
    private static final float UNDERWATER_KEEP_G = 0.48f;
    private static final float UNDERWATER_KEEP_B = 0.48f;
    private static final float UNDERWATER_OPACITY_DENSITY_BILLBOARD = 3.25f;
    private static final float UNDERWATER_OPACITY_DENSITY_DECAL = 2.45f;
    private static final float UNDERWATER_OPACITY_CONTRAST_BILLBOARD = 0.28f;
    private static final float UNDERWATER_OPACITY_CONTRAST_DECAL = 0.18f;
    private static final float UNDERWATER_HALO_ALPHA_MUL = 0.58f;
    private static final BloodCaches.WaterFrame VIEW_FRAME = BloodCachesWater.frame();
    private static int cachedUnderwaterViewTintRgb = 4159204;

    private static void updateViewFrameState(@Nullable Entity fallback, float partialTicks) {
        Entity view = BloodWaterRendering.getViewEntitySafe(fallback);
        Level world = view != null ? view.level() : null;
        long worldTime = world != null ? world.getGameTime() : Long.MIN_VALUE;
        int partialTicksBits = Float.floatToIntBits(partialTicks);
        if (BloodWaterRendering.VIEW_FRAME.view == view && BloodWaterRendering.VIEW_FRAME.world == world && BloodWaterRendering.VIEW_FRAME.worldTime == worldTime && BloodWaterRendering.VIEW_FRAME.partialTicksBits == partialTicksBits) {
            return;
        }
        BloodWaterRendering.VIEW_FRAME.view = view;
        BloodWaterRendering.VIEW_FRAME.world = world;
        BloodWaterRendering.VIEW_FRAME.worldTime = worldTime;
        BloodWaterRendering.VIEW_FRAME.partialTicksBits = partialTicksBits;
        BloodWaterRendering.VIEW_FRAME.eyeInWater = BloodWaterRendering.isEyeInWater(view, partialTicks);
        cachedUnderwaterViewTintRgb = BloodWaterRendering.VIEW_FRAME.eyeInWater ? BloodWaterRendering.getUnderwaterViewTintRgb(view, partialTicks) : 4159204;
    }

    @Nullable
    private static Entity getCachedViewEntity(@Nullable Entity fallback, float partialTicks) {
        BloodWaterRendering.updateViewFrameState(fallback, partialTicks);
        return BloodWaterRendering.VIEW_FRAME.view;
    }

    private static boolean isViewEyeInWaterCached(@Nullable Entity fallback, float partialTicks) {
        BloodWaterRendering.updateViewFrameState(fallback, partialTicks);
        return BloodWaterRendering.VIEW_FRAME.eyeInWater;
    }

    private static int getCachedUnderwaterViewTintRgb(@Nullable Entity fallback, float partialTicks) {
        BloodWaterRendering.updateViewFrameState(fallback, partialTicks);
        return cachedUnderwaterViewTintRgb;
    }

    public static void renderParticle(BloodWater p, @Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        Entity cachedView = BloodWaterRendering.getCachedViewEntity(entityIn, partialTicks);
        Entity renderView = cachedView != null ? cachedView : entityIn;
        boolean viewUnderwater = BloodWaterRendering.isViewEyeInWaterCached(entityIn, partialTicks);
        if (viewUnderwater) {
            BloodWaterRendering.queueUnderwaterDropletIfNeeded(p, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
            return;
        }
        p.clearQueuedBillboard();
        if (p.getParticleAlpha() <= 1.0E-6f) {
            return;
        }
        if (p.isFloorDecal()) {
            BloodWaterRendering.renderFloorBillboardTransition(p, buffer, renderView, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
            p.renderFloorDecal(buffer, partialTicks);
            return;
        }
        if (p.isOnSurface()) {
            BloodWaterRendering.renderSurfaceBillboardTransition(p, buffer, renderView, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
            return;
        }
        BloodWaterRendering.renderVanillaWithOverlay(p, buffer, renderView, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
    }

    public static void renderQueuedDroplet(BloodWater p, @Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks) {
        if (!p.isAlive()) {
            return;
        }
        Level world = p.getParticleWorld();
        if (world == null || p.getSprite() == null || p.getParticleAlpha() <= 1.0E-6f) {
            return;
        }
        Entity viewEnt = BloodWaterRendering.getCachedViewEntity(entityIn, partialTicks);
        if (viewEnt == null || !BloodWaterRendering.isViewEyeInWaterCached(entityIn, partialTicks)) {
            return;
        }
        float alphaMul = BloodWaterRendering.getQueuedDropletAlphaMul(p, partialTicks);
        if (alphaMul <= 1.0E-6f) {
            return;
        }
        float targetAlpha = BloodLiquidUtil.clamp01(p.getParticleAlpha() * alphaMul);
        if (targetAlpha <= 1.0E-6f) {
            return;
        }
        if ((targetAlpha = BloodWaterRendering.applyUnderwaterBillboardOpacity(targetAlpha)) <= 1.0E-6f) {
            return;
        }
        float baseR = BloodLiquidUtil.clamp01(p.getParticleRed());
        float baseG = BloodLiquidUtil.clamp01(p.getParticleGreen());
        float baseB = BloodLiquidUtil.clamp01(p.getParticleBlue());
        int waterRgb = BloodWaterRendering.getCachedUnderwaterViewTintRgb(entityIn, partialTicks);
        float tintR = BloodWaterRendering.applyUnderwaterBillboardTintR(baseR, baseG, baseB, waterRgb);
        float tintG = BloodWaterRendering.applyUnderwaterBillboardTintG(baseR, baseG, baseB, waterRgb);
        float tintB = BloodWaterRendering.applyUnderwaterBillboardTintB(baseR, baseG, baseB, waterRgb);
        BloodWaterRendering.renderBillboardQueuedLikeVanilla(p, buffer, viewEnt, partialTicks, alphaMul, -1.0f, tintR, tintG, tintB, targetAlpha);
    }

    public static void renderSurfaceDecal(BloodWater p, @Nonnull BufferBuilder buffer, float partialTicks) {
        double cz;
        if (!p.isOnSurface()) {
            return;
        }
        Level world = p.getParticleWorld();
        if (world == null || p.getSprite() == null) {
            return;
        }
        BloodWaterCache cache = p.getCache();
        if (!cache.hasSurface()) {
            return;
        }
        Entity viewEnt = BloodWaterRendering.getCachedViewEntity(null, partialTicks);
        boolean viewUnderwater = BloodWaterRendering.isViewEyeInWaterCached(null, partialTicks);
        boolean doCutout = !viewUnderwater && viewEnt != null;
        Vec3 cutoutEye = doCutout ? viewEnt.getEyePosition(partialTicks) : null;
        Level cutoutWorld = doCutout ? world : null;
        double yPushBase = 3.5E-4 + p.getSurfaceOffset();
        double viewSidePushAdd = viewUnderwater ? -yPushBase - 0.012 : 0.0;
        double cx = BloodWaterRendering.interp(p.getPrevPosX(), p.getPosX(), partialTicks);
        BloodWaterCache.SurfaceSample center = cache.sampleAt(cx, cz = BloodWaterRendering.interp(p.getPrevPosZ(), p.getPosZ(), partialTicks));
        if (center == null) {
            return;
        }
        BloodFluidSurfaceCache.Basis basis = BloodGeometryFluid.buildBasis(center.nx, center.ny, center.nz);
        if (basis == null) {
            return;
        }
        float rot = BloodWaterRendering.getInterpSurfaceRot(p, partialTicks);
        BlockPos top = cache.getCachedTop();
        if (top == null) {
            return;
        }
        int yRef = ((net.minecraft.core.Vec3i) top).getY();
        cache.beginRenderQueries();
        float tSmooth = BloodLiquidUtil.smoothstep01(BloodWaterRendering.computeSurfaceGrowth01(p, partialTicks));
        float surfBlend = BloodWaterRendering.getSurfaceBlend01(p, partialTicks);
        float baseAlpha = BloodLiquidUtil.clamp01(p.getParticleAlpha() * surfBlend);
        if (baseAlpha <= 1.0E-6f) {
            return;
        }
        float effScale = p.getSurfaceScale();
        if (BloodHeavy.allowWaterAmalgamation(p)) {
            float out;
            float mul = p.getInterpAmalgMul(partialTicks);
            if (mul > 1.0f) {
                effScale *= (float)Math.sqrt(mul);
                float alphaPow = 0.2f;
                baseAlpha *= (float)Math.pow(mul, -0.2f);
            }
            if ((out = p.getInterpAmalgOut01(partialTicks)) > 0.0f) {
                float keep = 1.0f - out;
                baseAlpha *= keep;
                effScale *= 1.0f - 0.15f * out;
            }
            if ((baseAlpha = BloodLiquidUtil.clamp01(baseAlpha)) <= 1.0E-6f) {
                return;
            }
            effScale = Math.max(0.001f, effScale);
        }
        if ((baseAlpha = BloodWaterRendering.applyDecalTrailAlpha(baseAlpha)) <= 1.0E-6f) {
            return;
        }
        if (viewUnderwater && (baseAlpha = BloodWaterRendering.applyUnderwaterDecalOpacity(baseAlpha)) <= 1.0E-6f) {
            return;
        }
        int brightness = p.getBrightnessForRender(partialTicks);
        int lmHi = brightness >> 16 & 0xFFFF;
        int lmLo = brightness & 0xFFFF;
        double camX = BloodWater.getCameraX();
        double camY = BloodWater.getCameraY();
        double camZ = BloodWater.getCameraZ();
        float tintR = BloodLiquidUtil.clamp01(p.getParticleRed());
        float tintG = BloodLiquidUtil.clamp01(p.getParticleGreen());
        float tintB = BloodLiquidUtil.clamp01(p.getParticleBlue());
        if (viewUnderwater) {
            int waterRgb = BloodWaterRendering.getCachedUnderwaterViewTintRgb(null, partialTicks);
            float baseR = tintR;
            float baseG = tintG;
            float baseB = tintB;
            tintR = BloodWaterRendering.applyUnderwaterDecalTintR(baseR, baseG, baseB, waterRgb);
            tintG = BloodWaterRendering.applyUnderwaterDecalTintG(baseR, baseG, baseB, waterRgb);
            tintB = BloodWaterRendering.applyUnderwaterDecalTintB(baseR, baseG, baseB, waterRgb);
        }
        List<BloodWaterUtil.Vertex> footprint = BloodGeometryWater.buildFootprintQuad(cx, cz, basis, p.getSprite(), rot, effScale, p.isFlipU());
        Level detailedCutoutWorld = null;
        Vec3 detailedCutoutEye = null;
        boolean allowHalo = true;
        if (doCutout) {
            double dx = cx - cutoutEye.x;
            double cutoutYPush = yPushBase + viewSidePushAdd;
            double dy = center.y + cutoutYPush - cutoutEye.y;
            double dz = cz - cutoutEye.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > 196.0) {
                if (!BloodWaterRendering.isSurfacePointVisibleThroughWater(cutoutWorld, cutoutEye, cache, cx, cz, cutoutYPush)) {
                    return;
                }
            } else {
                int cutoutClass = BloodWaterRendering.classifyFootprintVisibility(cutoutWorld, cutoutEye, cache, footprint, cx, cz, cutoutYPush);
                if (cutoutClass == 1) {
                    return;
                }
                if (cutoutClass == 2) {
                    detailedCutoutWorld = cutoutWorld;
                    detailedCutoutEye = cutoutEye;
                    allowHalo = false;
                }
            }
        }
        if (allowHalo) {
            BloodWaterRendering.renderSurfaceHaloLayer(p, buffer, cache, yRef, cx, cz, basis, rot, effScale, tSmooth, baseAlpha, tintR, tintG, tintB, yPushBase, viewSidePushAdd, camX, camY, camZ, lmHi, lmLo, viewUnderwater);
        }
        BloodWaterRendering.renderSurfaceFootprintLayer(buffer, footprint, cache, yRef, baseAlpha, tintR, tintG, tintB, yPushBase, viewSidePushAdd, camX, camY, camZ, lmHi, lmLo, detailedCutoutWorld, detailedCutoutEye);
    }

    public static void renderFloorDecal(BloodWater p, @Nonnull BufferBuilder buffer, float partialTicks) {
        if (!p.isFloorDecal()) {
            return;
        }
        Level world = p.getParticleWorld();
        if (world == null || p.getSprite() == null || p.getParticleAlpha() <= 1.0E-6f) {
            return;
        }
        double cx = BloodWaterRendering.interp(p.getPrevPosX(), p.getPosX(), partialTicks);
        double cy = BloodWaterRendering.interp(p.getPrevPosY(), p.getPosY(), partialTicks);
        double cz = BloodWaterRendering.interp(p.getPrevPosZ(), p.getPosZ(), partialTicks);
        int yRef = Mth.floor((double)(cy - p.getCollHalfY() - 0.001));
        float rot = BloodWaterRendering.getInterpSurfaceRot(p, partialTicks);
        float baseAlpha = BloodLiquidUtil.clamp01(p.getParticleAlpha() * BloodWaterRendering.getFloorBlend01(p, partialTicks));
        if (baseAlpha <= 1.0E-6f) {
            return;
        }
        if ((baseAlpha = BloodWaterRendering.applyDecalTrailAlpha(baseAlpha)) <= 1.0E-6f) {
            return;
        }
        boolean viewUnderwater = BloodWaterRendering.isViewEyeInWaterCached(null, partialTicks);
        if (viewUnderwater && (baseAlpha = BloodWaterRendering.applyUnderwaterDecalOpacity(baseAlpha)) <= 1.0E-6f) {
            return;
        }
        int brightness = p.getBrightnessForRender(partialTicks);
        int lmHi = brightness >> 16 & 0xFFFF;
        int lmLo = brightness & 0xFFFF;
        double camX = BloodWater.getCameraX();
        double camY = BloodWater.getCameraY();
        double camZ = BloodWater.getCameraZ();
        float tintR = BloodLiquidUtil.clamp01(p.getParticleRed());
        float tintG = BloodLiquidUtil.clamp01(p.getParticleGreen());
        float tintB = BloodLiquidUtil.clamp01(p.getParticleBlue());
        if (viewUnderwater) {
            int waterRgb = BloodWaterRendering.getCachedUnderwaterViewTintRgb(null, partialTicks);
            float baseR = tintR;
            float baseG = tintG;
            float baseB = tintB;
            tintR = BloodWaterRendering.applyUnderwaterDecalTintR(baseR, baseG, baseB, waterRgb);
            tintG = BloodWaterRendering.applyUnderwaterDecalTintG(baseR, baseG, baseB, waterRgb);
            tintB = BloodWaterRendering.applyUnderwaterDecalTintB(baseR, baseG, baseB, waterRgb);
        }
        double yPushBase = 3.5E-4 + p.getSurfaceOffset();
        BloodFluidSurfaceCache.Basis flatBasis = new BloodFluidSurfaceCache.Basis(1.0, 0.0, 0.0, 0.0, 0.0, 1.0);
        List<BloodWaterUtil.Vertex> footprint = BloodGeometryWater.buildFootprintQuad(cx, cz, flatBasis, p.getSprite(), rot, p.getSurfaceScale(), p.isFlipU());
        BloodWaterRendering.renderFloorFootprintLayer(buffer, footprint, world, yRef, baseAlpha, tintR, tintG, tintB, yPushBase, camX, camY, camZ, lmHi, lmLo);
    }

    private static void queueUnderwaterDropletIfNeeded(BloodWater p, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        p.captureQueuedBillboard(rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
        if (p.getParticleAlpha() > 1.0E-6f && !p.isSurfaceDecal() && !p.isFloorDecal()) {
            BloodRenderType.queueWaterDroplet(p);
        }
    }

    private static void renderFloorBillboardTransition(BloodWater p, @Nonnull BufferBuilder buffer, @Nonnull Entity viewEnt, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        float dropletMul = 1.0f - BloodWaterRendering.getFloorBlend01(p, partialTicks);
        if (dropletMul <= 1.0E-6f) {
            return;
        }
        BloodWaterRendering.renderVanillaAlphaMul(p, buffer, viewEnt, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, dropletMul);
    }

    private static void renderSurfaceBillboardTransition(BloodWater p, @Nonnull BufferBuilder buffer, @Nonnull Entity viewEnt, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        float dropletMul = 1.0f - BloodWaterRendering.getSurfaceBlend01(p, partialTicks);
        if (dropletMul <= 1.0E-6f) {
            return;
        }
        BloodWaterRendering.renderVanillaAlphaMul(p, buffer, viewEnt, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, dropletMul);
    }

    private static float getQueuedDropletAlphaMul(BloodWater p, float partialTicks) {
        if (p.isOnSurface()) {
            return 1.0f - BloodWaterRendering.getSurfaceBlend01(p, partialTicks);
        }
        if (p.isFloorDecal()) {
            return 1.0f - BloodWaterRendering.getFloorBlend01(p, partialTicks);
        }
        return 1.0f;
    }

    private static float applyDecalTrailAlpha(float alpha) {
        return BloodLiquidUtil.clamp01(alpha * 0.85f);
    }

    private static void renderSurfaceHaloLayer(BloodWater p, @Nonnull BufferBuilder buffer, @Nonnull BloodWaterCache cache, int yRef, double cx, double cz, @Nonnull BloodFluidSurfaceCache.Basis basis, float rot, float effScale, float tSmooth, float baseAlpha, float tintR, float tintG, float tintB, double yPushBase, double viewSidePushAdd, double camX, double camY, double camZ, int lmHi, int lmLo, boolean viewUnderwater) {
        float haloScaleMul = 1.25f + 0.6f * tSmooth;
        float haloScale = Math.max(0.001f, effScale * haloScaleMul);
        float haloAlpha = BloodLiquidUtil.clamp01(baseAlpha * 1.35f);
        if (viewUnderwater) {
            haloAlpha *= 0.58f;
        }
        if (haloAlpha > 0.9f) {
            haloAlpha = 0.9f;
        }
        float haloR = BloodLiquidUtil.clamp01(tintR);
        float haloG = BloodLiquidUtil.clamp01(tintG);
        float haloB = BloodLiquidUtil.clamp01(tintB);
        List<BloodWaterUtil.Vertex> haloFootprint = BloodGeometryWater.buildFootprintQuad(cx, cz, basis, p.getSprite(), rot + 0.17f, haloScale, p.isFlipU());
        BloodWaterRendering.renderSurfaceFootprintLayer(buffer, haloFootprint, cache, yRef, haloAlpha, haloR, haloG, haloB, yPushBase, -2.0E-4 + viewSidePushAdd, camX, camY, camZ, lmHi, lmLo, null, null);
    }

    private static float getFloorBlend01(BloodWater p, float partialTicks) {
        if (!p.isFloorDecal()) {
            return 0.0f;
        }
        if (p.getSurfaceGrowStartAge() < 0) {
            return 1.0f;
        }
        float denom = Math.max(1.0f, 6.0f);
        float t = ((float)p.getAge() + partialTicks - (float)p.getSurfaceGrowStartAge()) / denom;
        return BloodLiquidUtil.smoothstep01(BloodLiquidUtil.clamp01(t));
    }

    private static float getSurfaceBlend01(BloodWater p, float partialTicks) {
        if (!p.isOnSurface()) {
            return 0.0f;
        }
        if (p.getSurfaceGrowStartAge() < 0) {
            return 1.0f;
        }
        try {
            BloodWaterCache cache = p.getCache();
            if (cache != null && cache.hasSurface()) {
                double cx = BloodWaterRendering.interp(p.getPrevPosX(), p.getPosX(), partialTicks);
                double cz = BloodWaterRendering.interp(p.getPrevPosZ(), p.getPosZ(), partialTicks);
                BloodWaterCache.SurfaceSample surf = cache.sampleAt(cx, cz);
                BlockPos top = cache.getCachedTop();
                if (surf != null && top != null) {
                    double h = surf.y - (double)((net.minecraft.core.Vec3i) top).getY();
                    h = BloodLiquidUtil.clamp(h, 0.0, 1.0);
                    double slabH = 0.5;
                    double dropletDiameter = 2.0 * p.getCollHalfY();
                    double need = Math.max(0.5, dropletDiameter + 0.02);
                    if (h < need) {
                        return 1.0f;
                    }
                }
            }
        }
        catch (Throwable cache) {
            // empty catch block
        }
        float denom = Math.max(1.0f, 6.0f);
        float t = ((float)p.getAge() + partialTicks - (float)p.getSurfaceGrowStartAge()) / denom;
        return BloodLiquidUtil.smoothstep01(BloodLiquidUtil.clamp01(t));
    }

    private static float computeSurfaceGrowth01(BloodWater p, float partialTicks) {
        if (p.getSurfaceGrowStartAge() < 0) {
            return 0.0f;
        }
        int start = p.getSurfaceGrowStartAge();
        int end = Math.max(start + 1, p.getMaxAge());
        float elapsed = (float)p.getAge() + partialTicks - (float)start;
        float denom = end - start;
        if (denom <= 1.0E-6f) {
            return 0.0f;
        }
        return BloodLiquidUtil.clamp01(elapsed / denom);
    }

    private static float getInterpSurfaceRot(BloodWater p, float partialTicks) {
        return p.getPrevSurfaceRot() + (p.getSurfaceRot() - p.getPrevSurfaceRot()) * partialTicks;
    }

    private static double interp(double prev, double current, float partialTicks) {
        return prev + (current - prev) * (double)partialTicks;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static void renderVanillaAlphaMul(BloodWater p, @Nonnull BufferBuilder buffer, @Nonnull Entity viewEnt, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ, float alphaMul) {
        if (alphaMul <= 1.0E-6f) {
            return;
        }
        float oldA = p.getParticleAlpha();
        try {
            p.setParticleAlpha(oldA * alphaMul);
            BloodWaterRendering.renderVanillaWithOverlay(p, buffer, viewEnt, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
        }
        finally {
            p.setParticleAlpha(oldA);
        }
    }

    private static float applyUnderwaterBillboardOpacity(float alpha) {
        return BloodWaterRendering.applyUnderwaterApparentOpacity(alpha, 3.25f, 0.28f);
    }

    private static float applyUnderwaterDecalOpacity(float alpha) {
        return BloodWaterRendering.applyUnderwaterApparentOpacity(alpha, 2.45f, 0.18f);
    }

    private static float applyUnderwaterApparentOpacity(float alpha, float densityMul, float contrastBoost) {
        if ((alpha = BloodLiquidUtil.clamp01(alpha)) <= 1.0E-6f) {
            return 0.0f;
        }
        double transmittance = 1.0 - (double)alpha;
        float out = BloodLiquidUtil.clamp01((float)(1.0 - Math.pow(transmittance, densityMul)));
        if (contrastBoost > 0.0f && out < 0.999f) {
            float shape = 0.35f + 0.65f * out;
            out = BloodLiquidUtil.clamp01(out + (1.0f - out) * contrastBoost * shape);
        }
        return out;
    }

    private static float rgbR01(int rgb) {
        return (float)(rgb >> 16 & 0xFF) / 255.0f;
    }

    private static float rgbG01(int rgb) {
        return (float)(rgb >> 8 & 0xFF) / 255.0f;
    }

    private static float rgbB01(int rgb) {
        return (float)(rgb & 0xFF) / 255.0f;
    }

    private static float tintChannelTowardWater(float src, float waterChannel, float preserveMul, float energy, float strength) {
        float target = Math.max(src * preserveMul, waterChannel * energy);
        return BloodLiquidUtil.clamp01(BloodLiquidUtil.lerp(src, target, strength));
    }

    private static float applyUnderwaterTintR(float r, float g, float b, int waterRgb, float strength) {
        float cr = BloodLiquidUtil.clamp01(r);
        float cg = BloodLiquidUtil.clamp01(g);
        float cb = BloodLiquidUtil.clamp01(b);
        float energy = Math.max(cr, Math.max(cg, cb));
        return BloodWaterRendering.tintChannelTowardWater(cr, BloodWaterRendering.rgbR01(waterRgb), 0.54f, energy, strength);
    }

    private static float applyUnderwaterTintG(float r, float g, float b, int waterRgb, float strength) {
        float cr = BloodLiquidUtil.clamp01(r);
        float cg = BloodLiquidUtil.clamp01(g);
        float cb = BloodLiquidUtil.clamp01(b);
        float energy = Math.max(cr, Math.max(cg, cb));
        return BloodWaterRendering.tintChannelTowardWater(cg, BloodWaterRendering.rgbG01(waterRgb), 0.48f, energy, strength);
    }

    private static float applyUnderwaterTintB(float r, float g, float b, int waterRgb, float strength) {
        float cr = BloodLiquidUtil.clamp01(r);
        float cg = BloodLiquidUtil.clamp01(g);
        float cb = BloodLiquidUtil.clamp01(b);
        float energy = Math.max(cr, Math.max(cg, cb));
        return BloodWaterRendering.tintChannelTowardWater(cb, BloodWaterRendering.rgbB01(waterRgb), 0.48f, energy, strength);
    }

    private static float applyUnderwaterBillboardTintR(float r, float g, float b, int waterRgb) {
        return BloodWaterRendering.applyUnderwaterTintR(r, g, b, waterRgb, 0.52f);
    }

    private static float applyUnderwaterBillboardTintG(float r, float g, float b, int waterRgb) {
        return BloodWaterRendering.applyUnderwaterTintG(r, g, b, waterRgb, 0.52f);
    }

    private static float applyUnderwaterBillboardTintB(float r, float g, float b, int waterRgb) {
        return BloodWaterRendering.applyUnderwaterTintB(r, g, b, waterRgb, 0.52f);
    }

    private static float applyUnderwaterDecalTintR(float r, float g, float b, int waterRgb) {
        return BloodWaterRendering.applyUnderwaterTintR(r, g, b, waterRgb, 0.4f);
    }

    private static float applyUnderwaterDecalTintG(float r, float g, float b, int waterRgb) {
        return BloodWaterRendering.applyUnderwaterTintG(r, g, b, waterRgb, 0.4f);
    }

    private static float applyUnderwaterDecalTintB(float r, float g, float b, int waterRgb) {
        return BloodWaterRendering.applyUnderwaterTintB(r, g, b, waterRgb, 0.4f);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static void renderBillboardQueuedLikeVanilla(BloodWater p, @Nonnull BufferBuilder buffer, @Nonnull Entity viewEnt, float partialTicks, float alphaMul, float scaleOverrideOrNeg1, float tintR, float tintG, float tintB, float targetAlphaOverrideOrNeg1) {
        if (alphaMul <= 1.0E-6f) {
            return;
        }
        double oldIX = BloodWater.getCameraX();
        double oldIY = BloodWater.getCameraY();
        double oldIZ = BloodWater.getCameraZ();
        float oldA = p.getParticleAlpha();
        float oldScale = p.getParticleScale();
        try {
            float finalTargetAlpha;
            float rxz;
            float rxy;
            float ryz;
            float rz;
            float rx;
            if (p.hasQueuedBillboard()) {
                BloodWater.setCamera(p.getQueuedInterpX(), p.getQueuedInterpY(), p.getQueuedInterpZ());
                rx = p.getQueuedRotX();
                rz = p.getQueuedRotZ();
                ryz = p.getQueuedRotYZ();
                rxy = p.getQueuedRotXY();
                rxz = p.getQueuedRotXZ();
            } else {
                double ix = viewEnt.xo + (viewEnt.getX() - viewEnt.xo) * (double)partialTicks;
                double iy = viewEnt.yo + (viewEnt.getY() - viewEnt.yo) * (double)partialTicks;
                double iz = viewEnt.zo + (viewEnt.getZ() - viewEnt.zo) * (double)partialTicks;
                BloodWater.setCamera(ix, iy, iz);
                rx = LegacyParticle.rotationX;
                rz = LegacyParticle.rotationZ;
                ryz = LegacyParticle.rotationYZ;
                rxy = LegacyParticle.rotationXY;
                rxz = LegacyParticle.rotationXZ;
            }
            float f = finalTargetAlpha = targetAlphaOverrideOrNeg1 >= 0.0f ? BloodLiquidUtil.clamp01(targetAlphaOverrideOrNeg1) : BloodLiquidUtil.clamp01(oldA * alphaMul);
            if (finalTargetAlpha <= 1.0E-6f) {
                return;
            }
            p.setParticleAlpha(finalTargetAlpha);
            if (scaleOverrideOrNeg1 > 0.0f) {
                p.setParticleScale(scaleOverrideOrNeg1);
            }
            BloodWaterRendering.renderVanillaWithOverlay(p, buffer, viewEnt, partialTicks, rx, rz, ryz, rxy, rxz, tintR, tintG, tintB);
        }
        finally {
            p.setParticleAlpha(oldA);
            p.setParticleScale(oldScale);
            BloodWater.setCamera(oldIX, oldIY, oldIZ);
        }
    }

    private static void renderVanillaWithOverlay(BloodWater p, @Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        BloodWaterRendering.renderVanillaWithOverlay(p, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, p.getParticleRed(), p.getParticleGreen(), p.getParticleBlue());
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static void renderVanillaWithOverlay(BloodWater p, @Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ, float tintR, float tintG, float tintB) {
        float oldR = p.getParticleRed();
        float oldG = p.getParticleGreen();
        float oldB = p.getParticleBlue();
        float targetA = p.getParticleAlpha();
        float baseR = BloodLiquidUtil.clamp01(tintR);
        float baseG = BloodLiquidUtil.clamp01(tintG);
        float baseB = BloodLiquidUtil.clamp01(tintB);
        float passA0 = BloodWaterUtil.solveBasePassAlphaForTarget(targetA);
        float passA1 = BloodLiquidUtil.clamp01(passA0 * 0.85f);
        try {
            p.setParticleColor(baseR, baseG, baseB);
            p.setParticleAlpha(passA0);
            p.renderVanillaBillboard(buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
            p.setParticleColor(baseR, baseG, baseB);
            p.setParticleAlpha(passA1);
            p.renderVanillaBillboard(buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
        }
        finally {
            p.setParticleColor(oldR, oldG, oldB);
            p.setParticleAlpha(targetA);
        }
    }

    @Nullable
    private static FootprintBounds computeFootprintBoundsXZ(@Nullable List<BloodWaterUtil.Vertex> footprint) {
        if (footprint == null || footprint.size() < 3) {
            return null;
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (BloodWaterUtil.Vertex v : footprint) {
            if (v == null) continue;
            if (v.x < minX) {
                minX = v.x;
            }
            if (v.x > maxX) {
                maxX = v.x;
            }
            if (v.z < minZ) {
                minZ = v.z;
            }
            if (!(v.z > maxZ)) continue;
            maxZ = v.z;
        }
        if (minX == Double.POSITIVE_INFINITY) {
            return null;
        }
        return new FootprintBounds(minX, maxX, minZ, maxZ);
    }

    private static long keyXZ(int x, int z) {
        return (long)x << 32 ^ (long)z & 0xFFFFFFFFL;
    }

    @Nullable
    private static FloorData getFloorDataForColumn(@Nonnull Level w, @Nonnull Map<Long, FloorData> hit, @Nonnull Set<Long> miss, int bx, int bz, int yRef) {
        AABB bb;
        long key = BloodWaterRendering.keyXZ(bx, bz);
        FloorData cached = hit.get(key);
        if (cached != null) {
            return cached;
        }
        if (miss.contains(key)) {
            return null;
        }
        BlockPos floorPos = BlockPos.containing(bx, yRef, bz);
        if (!w.isLoaded(floorPos)) {
            miss.add(key);
            return null;
        }
        BlockState state = BloodWaterRendering.safeGetState(w, floorPos);
        if (state == null) {
            miss.add(key);
            return null;
        }
        try {
            if (GoreMaterial.of(state) == GoreMaterial.WATER) {
                miss.add(key);
                return null;
            }
            if (!GoreMaterial.of(state).isSolid()) {
                miss.add(key);
                return null;
            }
            if (!state.isFaceSturdy((BlockGetter)w, floorPos, Direction.UP)) {
                miss.add(key);
                return null;
            }
        }
        catch (Throwable t) {
            miss.add(key);
            return null;
        }
        BlockPos up = floorPos.above();
        if (!w.isLoaded(up)) {
            miss.add(key);
            return null;
        }
        BlockState upState = BloodWaterRendering.safeGetState(w, up);
        if (upState == null || GoreMaterial.of(upState) != GoreMaterial.WATER) {
            miss.add(key);
            return null;
        }
        try {
            bb = GoreUtil.boundingBox(state, (BlockGetter)w, floorPos);
        }
        catch (Throwable t) {
            miss.add(key);
            return null;
        }
        if (bb == null) {
            miss.add(key);
            return null;
        }
        double topY = (double)((net.minecraft.core.Vec3i) floorPos).getY() + bb.maxY;
        FloorData data = new FloorData(floorPos, topY);
        hit.put(key, data);
        return data;
    }

    private static boolean isConnectedFloor(@Nullable FloorData a, @Nonnull FloorData b) {
        if (a == null) {
            return false;
        }
        return Math.abs(a.topY - b.topY) <= 1.0E-4;
    }

    private static void renderFloorFootprintLayer(@Nonnull BufferBuilder buffer, @Nullable List<BloodWaterUtil.Vertex> footprint, @Nonnull Level world, int yRef, float targetAlpha, float tintR, float tintG, float tintB, double yPushBase, double camX, double camY, double camZ, int lmHi, int lmLo) {
        if (footprint == null || footprint.size() < 3) {
            return;
        }
        if ((targetAlpha = BloodLiquidUtil.clamp01(targetAlpha)) <= 1.0E-6f) {
            return;
        }
        FootprintBounds bounds = BloodWaterRendering.computeFootprintBoundsXZ(footprint);
        if (bounds == null) {
            return;
        }
        int bx0 = Mth.floor((double)bounds.minX);
        int bx1 = Mth.floor((double)bounds.maxX);
        int bz0 = Mth.floor((double)bounds.minZ);
        int bz1 = Mth.floor((double)bounds.maxZ);
        HashMap<Long, FloorData> hit = new HashMap<Long, FloorData>();
        HashSet<Long> miss = new HashSet<Long>();
        for (int bx = bx0; bx <= bx1; ++bx) {
            for (int bz = bz0; bz <= bz1; ++bz) {
                double padS;
                double maxCellZ;
                double padN;
                double minCellZ;
                double padE;
                double maxCellX;
                FloorData current = BloodWaterRendering.getFloorDataForColumn(world, hit, miss, bx, bz, yRef);
                if (current == null) continue;
                FloorData west = BloodWaterRendering.getFloorDataForColumn(world, hit, miss, bx - 1, bz, yRef);
                FloorData east = BloodWaterRendering.getFloorDataForColumn(world, hit, miss, bx + 1, bz, yRef);
                FloorData north = BloodWaterRendering.getFloorDataForColumn(world, hit, miss, bx, bz - 1, yRef);
                FloorData south = BloodWaterRendering.getFloorDataForColumn(world, hit, miss, bx, bz + 1, yRef);
                double padW = BloodWaterRendering.isConnectedFloor(west, current) ? 0.0 : 1.5E-4;
                double minCellX = (double)bx + padW;
                List<BloodWaterUtil.Vertex> poly = BloodGeometryWater.clipToRectXZ(footprint, minCellX, maxCellX = (double)bx + 1.0 - (padE = BloodWaterRendering.isConnectedFloor(east, current) ? 0.0 : 1.5E-4), minCellZ = (double)bz + (padN = BloodWaterRendering.isConnectedFloor(north, current) ? 0.0 : 1.5E-4), maxCellZ = (double)bz + 1.0 - (padS = BloodWaterRendering.isConnectedFloor(south, current) ? 0.0 : 1.5E-4));
                if (poly.size() < 3) continue;
                BloodFluidSurfaceCache.Plane plane = new BloodFluidSurfaceCache.Plane(0.0, current.topY, 0.0, 0.0, 1.0, 0.0);
                BloodWaterRendering.renderPolyTwoPassTint(buffer, poly, plane, targetAlpha, tintR, tintG, tintB, yPushBase, 0.0, camX, camY, camZ, lmHi, lmLo);
            }
        }
    }

    private static boolean isConnectedSurface(@Nullable BloodFluidSurfaceCache.SurfaceData a, @Nonnull BloodFluidSurfaceCache.SurfaceData b) {
        if (a == null) {
            return false;
        }
        if (!a.aboveAir || !b.aboveAir) {
            return false;
        }
        int dy = Math.abs(((net.minecraft.core.Vec3i) a.top).getY() - ((net.minecraft.core.Vec3i) b.top).getY());
        return dy <= 1;
    }

    private static void renderSurfaceFootprintLayer(@Nonnull BufferBuilder buffer, @Nullable List<BloodWaterUtil.Vertex> footprint, @Nonnull BloodWaterCache cache, int yRef, float targetAlpha, float tintR, float tintG, float tintB, double yPushBase, double yPushAdd, double camX, double camY, double camZ, int lmHi, int lmLo, @Nullable Level cutoutWorld, @Nullable Vec3 cutoutEye) {
        if (footprint == null || footprint.size() < 3) {
            return;
        }
        if ((targetAlpha = BloodLiquidUtil.clamp01(targetAlpha)) <= 1.0E-6f) {
            return;
        }
        FootprintBounds bounds = BloodWaterRendering.computeFootprintBoundsXZ(footprint);
        if (bounds == null) {
            return;
        }
        int bx0 = Mth.floor((double)bounds.minX);
        int bx1 = Mth.floor((double)bounds.maxX);
        int bz0 = Mth.floor((double)bounds.minZ);
        int bz1 = Mth.floor((double)bounds.maxZ);
        for (int bx = bx0; bx <= bx1; ++bx) {
            for (int bz = bz0; bz <= bz1; ++bz) {
                double padS;
                double maxCellZ;
                double padN;
                double minCellZ;
                double padE;
                double maxCellX;
                BloodFluidSurfaceCache.SurfaceData current = cache.getSurfaceDataForColumn(bx, bz, yRef, yRef);
                if (current == null || !current.aboveAir) continue;
                BloodFluidSurfaceCache.SurfaceData west = cache.getSurfaceDataForColumn(bx - 1, bz, yRef, yRef);
                BloodFluidSurfaceCache.SurfaceData east = cache.getSurfaceDataForColumn(bx + 1, bz, yRef, yRef);
                BloodFluidSurfaceCache.SurfaceData north = cache.getSurfaceDataForColumn(bx, bz - 1, yRef, yRef);
                BloodFluidSurfaceCache.SurfaceData south = cache.getSurfaceDataForColumn(bx, bz + 1, yRef, yRef);
                double padW = BloodWaterRendering.isConnectedSurface(west, current) ? 0.0 : 1.5E-4;
                double minCellX = (double)bx + padW;
                List<BloodWaterUtil.Vertex> clippedCell = BloodGeometryWater.clipToRectXZ(footprint, minCellX, maxCellX = (double)bx + 1.0 - (padE = BloodWaterRendering.isConnectedSurface(east, current) ? 0.0 : 1.5E-4), minCellZ = (double)bz + (padN = BloodWaterRendering.isConnectedSurface(north, current) ? 0.0 : 1.5E-4), maxCellZ = (double)bz + 1.0 - (padS = BloodWaterRendering.isConnectedSurface(south, current) ? 0.0 : 1.5E-4));
                if (clippedCell.size() < 3) continue;
                List<BloodWaterUtil.Vertex> triPolyA = BloodGeometryWater.clipDiag(clippedCell, bx, bz, true);
                List<BloodWaterUtil.Vertex> triPolyB = BloodGeometryWater.clipDiag(clippedCell, bx, bz, false);
                int by = ((net.minecraft.core.Vec3i) current.top).getY();
                BloodFluidSurfaceCache.Plane triA = BloodGeometryFluid.buildTriPlaneA(bx, by, bz, current.hNW, current.hSW, current.hSE);
                BloodFluidSurfaceCache.Plane triB = BloodGeometryFluid.buildTriPlaneB(bx, by, bz, current.hNW, current.hNE, current.hSE);
                BloodWaterRendering.renderSurfaceTriangleCellPiece(buffer, triPolyA, triA, targetAlpha, tintR, tintG, tintB, yPushBase, yPushAdd, camX, camY, camZ, lmHi, lmLo, cutoutWorld, cutoutEye);
                BloodWaterRendering.renderSurfaceTriangleCellPiece(buffer, triPolyB, triB, targetAlpha, tintR, tintG, tintB, yPushBase, yPushAdd, camX, camY, camZ, lmHi, lmLo, cutoutWorld, cutoutEye);
            }
        }
    }

    private static void renderSurfaceTriangleCellPiece(@Nonnull BufferBuilder buffer, @Nullable List<BloodWaterUtil.Vertex> poly, @Nonnull BloodFluidSurfaceCache.Plane plane, float targetAlpha, float tintR, float tintG, float tintB, double yPushBase, double yPushAdd, double camX, double camY, double camZ, int lmHi, int lmLo, @Nullable Level cutoutWorld, @Nullable Vec3 cutoutEye) {
        if (poly == null || poly.size() < 3) {
            return;
        }
        List<BloodWaterUtil.Vertex> drawPoly = poly;
        if (cutoutWorld != null && cutoutEye != null) {
            drawPoly = BloodWaterRendering.clipPolyToVisibleThroughWater(cutoutWorld, cutoutEye, poly, plane, yPushBase + yPushAdd);
        }
        if (drawPoly.size() < 3) {
            return;
        }
        BloodWaterRendering.renderPolyTwoPassTint(buffer, drawPoly, plane, targetAlpha, tintR, tintG, tintB, yPushBase, yPushAdd, camX, camY, camZ, lmHi, lmLo);
    }

    private static boolean isSurfacePointVisibleThroughWater(@Nonnull Level world, @Nonnull Vec3 eye, @Nonnull BloodWaterCache cache, double x, double z, double yPush) {
        BloodWaterCache.SurfaceSample sample = cache.sampleAt(x, z);
        if (sample == null) {
            return true;
        }
        return !BloodWaterRendering.isAnyWaterVolumeOnRay(world, eye, new Vec3(x, sample.y + yPush + 0.001, z));
    }

    private static int classifyFootprintVisibility(@Nonnull Level world, @Nonnull Vec3 eye, @Nonnull BloodWaterCache cache, @Nonnull List<BloodWaterUtil.Vertex> footprint, double centerX, double centerZ, double yPush) {
        boolean anyVisible = false;
        boolean anyHidden = false;
        boolean centerVisible = BloodWaterRendering.isSurfacePointVisibleThroughWater(world, eye, cache, centerX, centerZ, yPush);
        anyVisible |= centerVisible;
        anyHidden |= !centerVisible;
        for (BloodWaterUtil.Vertex v : footprint) {
            if (v == null) continue;
            boolean visible = BloodWaterRendering.isSurfacePointVisibleThroughWater(world, eye, cache, v.x, v.z, yPush);
            if (!(anyVisible |= visible) || !(anyHidden |= !visible)) continue;
            return 2;
        }
        return anyHidden ? 1 : 0;
    }

    private static BloodWaterUtil.Vertex lerpVertexXZUV(@Nonnull BloodWaterUtil.Vertex a, @Nonnull BloodWaterUtil.Vertex b, double t) {
        double x = a.x + (b.x - a.x) * t;
        double z = a.z + (b.z - a.z) * t;
        float u = (float)((double)a.u + (double)(b.u - a.u) * t);
        float v = (float)((double)a.v + (double)(b.v - a.v) * t);
        return new BloodWaterUtil.Vertex(x, 0.0, z, u, v);
    }

    private static boolean isPointOccludedByWater(@Nonnull Level world, @Nonnull Vec3 eye, @Nonnull BloodFluidSurfaceCache.Plane plane, @Nonnull BloodWaterUtil.Vertex v, double yPush) {
        double wx = v.x;
        double wz = v.z;
        double wy = plane.yAt(wx, wz) + yPush + 0.001;
        return BloodWaterRendering.isAnyWaterVolumeOnRay(world, eye, new Vec3(wx, wy, wz));
    }

    private static BloodWaterUtil.Vertex findOcclusionBoundaryOnEdge(@Nonnull Level world, @Nonnull Vec3 eye, @Nonnull BloodFluidSurfaceCache.Plane plane, double yPush, @Nonnull BloodWaterUtil.Vertex a, boolean occA, @Nonnull BloodWaterUtil.Vertex b, boolean occB) {
        if (occA == occB) {
            return BloodWaterRendering.lerpVertexXZUV(a, b, 0.5);
        }
        double lo = 0.0;
        double hi = 1.0;
        boolean loOcc = occA;
        for (int i = 0; i < 6; ++i) {
            double mid = 0.5 * (lo + hi);
            BloodWaterUtil.Vertex m = BloodWaterRendering.lerpVertexXZUV(a, b, mid);
            boolean midOcc = BloodWaterRendering.isPointOccludedByWater(world, eye, plane, m, yPush);
            if (midOcc == loOcc) {
                lo = mid;
                continue;
            }
            hi = mid;
        }
        return BloodWaterRendering.lerpVertexXZUV(a, b, 0.5 * (lo + hi));
    }

    @Nonnull
    private static List<BloodWaterUtil.Vertex> clipPolyToVisibleThroughWater(@Nonnull Level world, @Nonnull Vec3 eye, @Nonnull List<BloodWaterUtil.Vertex> in, @Nonnull BloodFluidSurfaceCache.Plane plane, double yPush) {
        if (in.size() < 3) {
            return new ArrayList<BloodWaterUtil.Vertex>(0);
        }
        ArrayList<BloodWaterUtil.Vertex> out = new ArrayList<BloodWaterUtil.Vertex>(in.size() + 4);
        BloodWaterUtil.Vertex prev = in.get(in.size() - 1);
        boolean prevOccluded = BloodWaterRendering.isPointOccludedByWater(world, eye, plane, prev, yPush);
        boolean prevVisible = !prevOccluded;
        for (BloodWaterUtil.Vertex curr : in) {
            boolean currVisible;
            boolean currOccluded = BloodWaterRendering.isPointOccludedByWater(world, eye, plane, curr, yPush);
            boolean bl = currVisible = !currOccluded;
            if (currVisible) {
                if (!prevVisible) {
                    out.add(BloodWaterRendering.findOcclusionBoundaryOnEdge(world, eye, plane, yPush, prev, prevOccluded, curr, currOccluded));
                }
                out.add(curr);
            } else if (prevVisible) {
                out.add(BloodWaterRendering.findOcclusionBoundaryOnEdge(world, eye, plane, yPush, prev, prevOccluded, curr, currOccluded));
            }
            prev = curr;
            prevOccluded = currOccluded;
            prevVisible = currVisible;
        }
        return out;
    }

    private static int getUnderwaterViewTintRgb(@Nullable Entity viewEnt, float partialTicks) {
        if (viewEnt == null || viewEnt.level() == null) {
            return 4159204;
        }
        try {
            Vec3 eye = viewEnt.getEyePosition(partialTicks);
            BlockPos tintPos = BlockPos.containing(eye.x, eye.y - 0.1, eye.z);
            if (viewEnt.level().isLoaded(tintPos)) {
                return BiomeColors.getAverageWaterColor(viewEnt.level(), (BlockPos)tintPos);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return 4159204;
    }

    @Nullable
    private static BlockState safeGetState(@Nullable Level world, @Nonnull BlockPos pos) {
        try {
            return world != null ? world.getBlockState(pos) : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static boolean isEyeInWater(@Nullable Entity view, float partialTicks) {
        if (view == null) {
            return false;
        }
        Level world = view.level();
        if (world == null) {
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
            if (world.isLoaded(eyePos)) {
                BlockState eyeState = BloodWaterRendering.safeGetState(world, eyePos);
                return eyeState != null && GoreMaterial.of(eyeState) == GoreMaterial.WATER;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return false;
    }

    @Nullable
    private static Entity getViewEntitySafe(@Nullable Entity fallback) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Entity view = mc.getCameraEntity();
            return view != null ? view : fallback;
        }
        catch (Throwable t) {
            return fallback;
        }
    }

    private static boolean isAnyWaterVolumeOnRay(@Nonnull Level world, @Nonnull Vec3 start, @Nonnull Vec3 end) {
        double tDeltaZ;
        double tMaxZ;
        double tDeltaY;
        double tMaxY;
        double tDeltaX;
        double tMaxX;
        double rayX = end.x - start.x;
        double rayY = end.y - start.y;
        double rayZ = end.z - start.z;
        double lenSq = rayX * rayX + rayY * rayY + rayZ * rayZ;
        if (lenSq <= 1.0E-12) {
            return false;
        }
        int cellX = Mth.floor((double)start.x);
        int cellY = Mth.floor((double)start.y);
        int cellZ = Mth.floor((double)start.z);
        int endX = Mth.floor((double)end.x);
        int endY = Mth.floor((double)end.y);
        int endZ = Mth.floor((double)end.z);
        int stepX = Double.compare(rayX, 0.0);
        int stepY = Double.compare(rayY, 0.0);
        int stepZ = Double.compare(rayZ, 0.0);
        if (stepX != 0) {
            double nextX = stepX > 0 ? (double)cellX + 1.0 : (double)cellX;
            tMaxX = (nextX - start.x) / rayX;
            tDeltaX = 1.0 / Math.abs(rayX);
        } else {
            tMaxX = Double.POSITIVE_INFINITY;
            tDeltaX = Double.POSITIVE_INFINITY;
        }
        if (stepY != 0) {
            double nextY = stepY > 0 ? (double)cellY + 1.0 : (double)cellY;
            tMaxY = (nextY - start.y) / rayY;
            tDeltaY = 1.0 / Math.abs(rayY);
        } else {
            tMaxY = Double.POSITIVE_INFINITY;
            tDeltaY = Double.POSITIVE_INFINITY;
        }
        if (stepZ != 0) {
            double nextZ = stepZ > 0 ? (double)cellZ + 1.0 : (double)cellZ;
            tMaxZ = (nextZ - start.z) / rayZ;
            tDeltaZ = 1.0 / Math.abs(rayZ);
        } else {
            tMaxZ = Double.POSITIVE_INFINITY;
            tDeltaZ = Double.POSITIVE_INFINITY;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        double tCur = 0.0;
        int maxSteps = 1 + Math.abs(endX - cellX) + Math.abs(endY - cellY) + Math.abs(endZ - cellZ) + 8;
        for (int i = 0; i < maxSteps; ++i) {
            BlockState state;
            double tNext;
            boolean atEndCell;
            boolean bl = atEndCell = cellX == endX && cellY == endY && cellZ == endZ;
            if (atEndCell) {
                tNext = 1.0;
            } else {
                tNext = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
                if (tNext < tCur) {
                    tNext = tCur;
                }
                if (tNext > 1.0) {
                    tNext = 1.0;
                }
            }
            cursor.set(cellX, cellY, cellZ);
            if (world.isLoaded((BlockPos)cursor) && (state = BloodWaterRendering.safeGetState(world, (BlockPos)cursor)) != null && GoreMaterial.of(state) == GoreMaterial.WATER && BloodWaterRendering.segmentIntersectsWaterVolume(world, (BlockPos)cursor, start, rayX, rayY, rayZ, tCur, tNext)) {
                return true;
            }
            if (atEndCell) break;
            if (tMaxX <= tMaxZ + 1.0E-10 && tMaxX <= tMaxY + 1.0E-10) {
                tCur = tMaxX;
                cellX += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxZ <= tMaxX + 1.0E-10 && tMaxZ <= tMaxY + 1.0E-10) {
                tCur = tMaxZ;
                cellZ += stepZ;
                tMaxZ += tDeltaZ;
            } else {
                tCur = tMaxY;
                cellY += stepY;
                tMaxY += tDeltaY;
            }
            if (tCur > 1.0) break;
        }
        return false;
    }

    private static boolean segmentIntersectsWaterVolume(@Nonnull Level world, @Nonnull BlockPos cell, @Nonnull Vec3 start, double rayX, double rayY, double rayZ, double t0, double t1) {
        boolean side1;
        BlockState upState;
        if (t1 <= t0 + 1.0E-12) {
            return false;
        }
        BlockPos up = cell.above();
        if (world.isLoaded(up) && (upState = BloodWaterRendering.safeGetState(world, up)) != null && GoreMaterial.of(upState) == GoreMaterial.WATER) {
            return true;
        }
        float hNW = BloodWaterRendering.getFluidCornerHeight(world, cell, GoreMaterial.WATER);
        float hNE = BloodWaterRendering.getFluidCornerHeight(world, cell.east(), GoreMaterial.WATER);
        float hSW = BloodWaterRendering.getFluidCornerHeight(world, cell.south(), GoreMaterial.WATER);
        float hSE = BloodWaterRendering.getFluidCornerHeight(world, cell.south().east(), GoreMaterial.WATER);
        int bx = ((net.minecraft.core.Vec3i) cell).getX();
        int by = ((net.minecraft.core.Vec3i) cell).getY();
        int bz = ((net.minecraft.core.Vec3i) cell).getZ();
        double x0 = start.x + rayX * t0;
        double y0 = start.y + rayY * t0;
        double z0 = start.z + rayZ * t0;
        double x1 = start.x + rayX * t1;
        double y1 = start.y + rayY * t1;
        double z1 = start.z + rayZ * t1;
        double u0 = x0 - (double)bx;
        double v0 = z0 - (double)bz;
        double u1 = x1 - (double)bx;
        double v1 = z1 - (double)bz;
        boolean side0 = u0 <= v0 + 1.0E-9;
        boolean bl = side1 = u1 <= v1 + 1.0E-9;
        if (BloodWaterRendering.isPointBelowWaterSurface(by, u0, y0, v0, hNW, hNE, hSW, hSE)) {
            return true;
        }
        if (BloodWaterRendering.isPointBelowWaterSurface(by, u1, y1, v1, hNW, hNE, hSW, hSE)) {
            return true;
        }
        if (side0 == side1) {
            return false;
        }
        double f1 = u1 - v1;
        double f0 = u0 - v0;
        double denom = f1 - f0;
        if (Math.abs(denom) < 1.0E-12) {
            return false;
        }
        double tDiag = t0 + -f0 * (t1 - t0) / denom;
        if (tDiag <= t0 + 1.0E-12 || tDiag >= t1 - 1.0E-12) {
            return false;
        }
        double xDiag = start.x + rayX * tDiag;
        double yDiag = start.y + rayY * tDiag;
        double zDiag = start.z + rayZ * tDiag;
        return BloodWaterRendering.isPointBelowWaterSurface(by, xDiag - (double)bx, yDiag, zDiag - (double)bz, hNW, hNE, hSW, hSE);
    }

    private static boolean isPointBelowWaterSurface(int blockY, double localX, double worldY, double localZ, float hNW, float hNE, float hSW, float hSE) {
        double surfaceY = (double)blockY + BloodWaterRendering.waterSurfaceFracAt(BloodWaterRendering.clamp01d(localX), BloodWaterRendering.clamp01d(localZ), hNW, hNE, hSW, hSE);
        return worldY <= surfaceY - 0.002;
    }

    private static double waterSurfaceFracAt(double u, double v, float hNW, float hNE, float hSW, float hSE) {
        double nw = BloodWaterRendering.clamp01d(hNW);
        double ne = BloodWaterRendering.clamp01d(hNE);
        double sw = BloodWaterRendering.clamp01d(hSW);
        double se = BloodWaterRendering.clamp01d(hSE);
        if (u <= v) {
            return (1.0 - v) * nw + (v - u) * sw + u * se;
        }
        return (1.0 - u) * nw + v * se + (u - v) * ne;
    }

    private static float getFluidCornerHeight(@Nonnull Level world, @Nonnull BlockPos cornerPos, @Nonnull GoreMaterial mat) {
        int sampleWeight = 0;
        float heightSum = 0.0f;
        for (int i = 0; i < 4; ++i) {
            BlockState state;
            BlockState above;
            BlockPos samplePos = cornerPos.offset(-(i & 1), 0, -(i >> 1 & 1));
            BlockPos samplePosUp = samplePos.above();
            if (world.isLoaded(samplePosUp) && (above = BloodWaterRendering.safeGetState(world, samplePosUp)) != null && GoreMaterial.of(above) == mat) {
                return 1.0f;
            }
            if (!world.isLoaded(samplePos) || (state = BloodWaterRendering.safeGetState(world, samplePos)) == null) continue;
            GoreMaterial sampleMat = GoreMaterial.of(state);
            if (sampleMat == mat) {
                int level = BloodWaterRendering.getLiquidLevelRaw(state);
                float heightPercent = GoreUtil.liquidHeightPercent((int)level);
                if (level >= 8 || level == 0) {
                    heightSum += heightPercent * 10.0f;
                    sampleWeight += 10;
                }
                heightSum += heightPercent;
                ++sampleWeight;
                continue;
            }
            if (sampleMat.isSolid()) continue;
            heightSum += 1.0f;
            ++sampleWeight;
        }
        if (sampleWeight <= 0) {
            return 0.0f;
        }
        float h = 1.0f - heightSum / (float)sampleWeight;
        if (h < 0.0f) {
            return 0.0f;
        }
        return Math.min(h, 1.0f);
    }

    private static int getLiquidLevelRaw(@Nullable BlockState state) {
        if (state == null) {
            return 0;
        }
        try {
            if (state.getBlock() instanceof LiquidBlock) {
                Integer level = (Integer)state.getValue((Property)LiquidBlock.LEVEL);
                return level != null ? level : 0;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return 0;
    }

    private static double clamp01d(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }

    private static void renderPolyTwoPassTint(@Nonnull BufferBuilder buffer, @Nonnull List<BloodWaterUtil.Vertex> poly, @Nonnull BloodFluidSurfaceCache.Plane plane, float targetAlpha, float r, float g, float b, double yPushBase, double yPushAdd, double camX, double camY, double camZ, int lmHi, int lmLo) {
        float basePassAlpha = BloodWaterUtil.solveBasePassAlphaForTarget(targetAlpha);
        float overlayAlpha = BloodLiquidUtil.clamp01(basePassAlpha * 0.85f);
        float tintR = BloodLiquidUtil.clamp01(r);
        float tintG = BloodLiquidUtil.clamp01(g);
        float tintB = BloodLiquidUtil.clamp01(b);
        BloodWaterRendering.renderPolyOnPlaneAsQuads(buffer, poly, plane, tintR, tintG, tintB, basePassAlpha, yPushBase, yPushAdd, camX, camY, camZ, lmHi, lmLo);
        BloodWaterRendering.renderPolyOnPlaneAsQuads(buffer, poly, plane, tintR, tintG, tintB, overlayAlpha, yPushBase, yPushAdd, camX, camY, camZ, lmHi, lmLo);
    }

    private static void renderPolyOnPlaneAsQuads(@Nonnull BufferBuilder buffer, @Nonnull List<BloodWaterUtil.Vertex> poly, @Nonnull BloodFluidSurfaceCache.Plane plane, float r, float g, float b, float a, double yPushBase, double yPushAdd, double camX, double camY, double camZ, int lmHi, int lmLo) {
        if (poly.size() < 3) {
            return;
        }
        double yPush = yPushBase + yPushAdd;
        if (poly.size() == 4) {
            BloodWaterRendering.emitProjected(buffer, poly.get(0), plane, yPush, r, g, b, a, camX, camY, camZ, lmHi, lmLo);
            BloodWaterRendering.emitProjected(buffer, poly.get(1), plane, yPush, r, g, b, a, camX, camY, camZ, lmHi, lmLo);
            BloodWaterRendering.emitProjected(buffer, poly.get(2), plane, yPush, r, g, b, a, camX, camY, camZ, lmHi, lmLo);
            BloodWaterRendering.emitProjected(buffer, poly.get(3), plane, yPush, r, g, b, a, camX, camY, camZ, lmHi, lmLo);
            return;
        }
        BloodWaterUtil.Vertex root = poly.get(0);
        for (int i = 1; i < poly.size() - 1; ++i) {
            BloodWaterUtil.Vertex v1 = poly.get(i);
            BloodWaterUtil.Vertex v2 = poly.get(i + 1);
            BloodWaterRendering.emitProjected(buffer, root, plane, yPush, r, g, b, a, camX, camY, camZ, lmHi, lmLo);
            BloodWaterRendering.emitProjected(buffer, v1, plane, yPush, r, g, b, a, camX, camY, camZ, lmHi, lmLo);
            BloodWaterRendering.emitProjected(buffer, v2, plane, yPush, r, g, b, a, camX, camY, camZ, lmHi, lmLo);
            BloodWaterRendering.emitProjected(buffer, v2, plane, yPush, r, g, b, a, camX, camY, camZ, lmHi, lmLo);
        }
    }

    private static void emitProjected(@Nonnull BufferBuilder buffer, @Nonnull BloodWaterUtil.Vertex v, @Nonnull BloodFluidSurfaceCache.Plane plane, double yPush, float r, float g, float b, float a, double camX, double camY, double camZ, int lmHi, int lmLo) {
        double wx = v.x;
        double wz = v.z;
        double wy = plane.yAt(wx, wz) + yPush;
        buffer.pos(wx - camX, wy - camY, wz - camZ).tex((double)v.u, (double)v.v).color(r, g, b, a).lightmap(lmHi, lmLo).endVertex();
    }

    private static final class FloorData {
        final BlockPos pos;
        final double topY;

        private FloorData(BlockPos pos, double topY) {
            this.pos = pos;
            this.topY = topY;
        }
    }

    private static final class FootprintBounds {
        final double minX;
        final double maxX;
        final double minZ;
        final double maxZ;

        private FootprintBounds(double minX, double maxX, double minZ, double maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }
    }
}

