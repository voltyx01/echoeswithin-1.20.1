package com.voltyx.efw.gore.particle.render;

import com.voltyx.efw.gore.particle.common.GoreUtil;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.blocksupport.PistonSupport;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.render.BloodRenderType;
import com.voltyx.efw.gore.particle.render.parts.BloodLavaRendering;
import com.voltyx.efw.gore.particle.state.BloodHotBlocks;
import com.voltyx.efw.gore.particle.state.BloodMagic;
import com.voltyx.efw.gore.particle.state.BloodSlimy;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.LiquidBlock;
import com.voltyx.efw.gore.particle.common.GoreMaterial;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import com.voltyx.efw.gore.particle.render.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
public final class BloodRender {
    private static final boolean OVERLAY_ENABLED = true;
    private static final float OVERLAY_ALPHA_MUL = 0.85f;
    private static final float BASE_DARK_MUL = 0.35f;
    private static final float ALPHA_EPS = 1.0E-6f;
    private static final float FALLING_TO_VANILLA_FADE_TICKS = 4.0f;
    private static final float FALLING_TO_VANILLA_HOLD_TICKS = 40.0f;
    private static final float CEILING_ATTACH_FADE_TICKS = 6.0f;
    private static final float CEILING_TAIL_FADE_TICKS = 4.0f;
    private static final double RAY_LEN_EPS_SQ = 1.0E-12;
    private static final double EDGE_PARAM_EPS = 1.0E-12;
    private static final double DDA_TIE_EPS = 1.0E-10;
    private static final int CUTOUT_BISECT_STEPS = 6;
    private static final double WATER_HIT_PAD = 0.002;
    private static final double VIEW_WATER_PROBE_Y_BIAS = 0.02;

    private BloodRender() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void renderParticle(@Nonnull ParticleBlood p, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        float oldR = p.getRed();
        float oldG = p.getGreen();
        float oldB = p.getBlue();
        float oldA = p.getAlpha();
        try {
            if (!p.isStuck) {
                p.captureQueuedBillboard(rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
                if (BloodMagic.isMagic(p)) {
                    BloodRenderType.queueMagicBillboard(p);
                } else {
                    BloodRenderType.queueBloodBillboard(p);
                }
                return;
            }
            p.clearQueuedBillboard();
            BloodRenderType.track(p);
        }
        finally {
            p.setRGBA(oldR, oldG, oldB, oldA);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void renderStuckDecal(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, float partialTicks) {
        if (!p.isStuck) {
            return;
        }
        float oldR = p.getRed();
        float oldG = p.getGreen();
        float oldB = p.getBlue();
        float oldA = p.getAlpha();
        try {
            if (p.isGroundTop() && BloodHotBlocks.isHotGroundTopHost(p)) {
                BloodLavaRendering.renderHotGroundFromBlood(p, buffer, partialTicks);
                return;
            }
            if (p.cache.shape.polys == null || p.cache.shape.polys.isEmpty()) {
                return;
            }
            BloodRender.renderDecalWithOverlay(p, buffer, partialTicks, oldA, p.getTintR(), p.getTintG(), p.getTintB());
        }
        finally {
            p.setRGBA(oldR, oldG, oldB, oldA);
        }
    }

    public static void renderCustomPolyList(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, float partialTicks, @Nullable List<List<Util.Vertex>> polys) {
        PistonSupport.MovingInfo miNow;
        if (polys == null || polys.isEmpty()) {
            return;
        }
        int br = p.getBrightnessForRender(partialTicks);
        int lmHi = br >> 16 & 0xFFFF;
        int lmLo = br & 0xFFFF;
        double ix = p.prevPosX + (p.posX - p.prevPosX) * (double)partialTicks;
        double iy = p.prevPosY + (p.posY - p.prevPosY) * (double)partialTicks;
        double iz = p.prevPosZ + (p.posZ - p.prevPosZ) * (double)partialTicks;
        double baseX = Double.isNaN(p.cache.view.x) ? p.posX : p.cache.view.x;
        double baseY = Double.isNaN(p.cache.view.y) ? p.posY : p.cache.view.y;
        double baseZ = Double.isNaN(p.cache.view.z) ? p.posZ : p.cache.view.z;
        double dx = ix - baseX;
        double dy = iy - baseY;
        double dz = iz - baseZ;
        Level world = p.getParticleWorld();
        if (p.isStuck && p.stuckPos != null && world != null && (miNow = PistonSupport.getMovingInfo(world, p.stuckPos, partialTicks, p)) != null && miNow.offset != null && !miNow.staticBaseNoOffset) {
            PistonSupport.MovingInfo miBuild = PistonSupport.getMovingInfo(world, p.stuckPos, 1.0f, p);
            Vec3 offNow = miNow.offset;
            Vec3 offBuild = miBuild != null && miBuild.offset != null ? miBuild.offset : Util.ZERO;
            dx = offNow.x - offBuild.x;
            dy = offNow.y - offBuild.y;
            dz = offNow.z - offBuild.z;
        }
        Entity viewEnt = p.isStuck && world != null ? BloodRender.getViewEntitySafe(null) : null;
        Vec3 viewEye = viewEnt != null && viewEnt.level() == world ? BloodRender.getViewEyeSafe(viewEnt, partialTicks) : null;
        boolean doWaterCutout = viewEye != null;
        for (List<Util.Vertex> poly : polys) {
            if (poly == null || poly.size() < 3) continue;
            if (!doWaterCutout) {
                BloodRender.renderPolyAsQuads(p, buffer, poly, lmHi, lmLo, dx, dy, dz);
                continue;
            }
            List<Util.Vertex> worldPoly = BloodRender.offsetPoly(poly, dx, dy, dz);
            List<Util.Vertex> visiblePoly = BloodRender.clipPolyToVisibleThroughWater(world, viewEye, worldPoly);
            if (visiblePoly.size() < 3) continue;
            BloodRender.renderPolyAsQuads(p, buffer, visiblePoly, lmHi, lmLo, 0.0, 0.0, 0.0);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    static void renderQueuedBloodParticle(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        float oldR = p.getRed();
        float oldG = p.getGreen();
        float oldB = p.getBlue();
        float oldA = p.getAlpha();
        float tintR = p.getTintR();
        float tintG = p.getTintG();
        float tintB = p.getTintB();
        try {
            if (p.isExpiredSafe() || p.isStuck) {
                return;
            }
            if (p.fallingDripActive) {
                BloodRender.renderFallingDripTransition(p, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, oldA, tintR, tintG, tintB);
                return;
            }
            if (BloodHotBlocks.shouldRenderHotAirBillboard(p)) {
                BloodLavaRendering.renderHotBillboardFromBlood(p, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
                return;
            }
            BloodRender.renderFreeBillboardWithOverlay(p, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, oldA, tintR, tintG, tintB);
        }
        finally {
            p.setRGBA(oldR, oldG, oldB, oldA);
        }
    }

    private static void renderFallingDripTransition(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ, float targetAlpha, float tintR, float tintG, float tintB) {
        boolean hasDecal;
        boolean bl = hasDecal = p.cache.shape.polys != null && !p.cache.shape.polys.isEmpty();
        if (!hasDecal) {
            BloodRender.renderVanillaWithOverlay(p, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, targetAlpha, tintR, tintG, tintB);
            return;
        }
        float vanillaMul = BloodRender.fallingVanillaBlend01(p, partialTicks);
        float decalMul = 1.0f - vanillaMul;
        if (decalMul > 1.0E-6f) {
            BloodRender.renderDecalWithOverlay(p, buffer, partialTicks, targetAlpha * decalMul, tintR, tintG, tintB);
        }
        if (vanillaMul > 1.0E-6f) {
            BloodRender.renderVanillaWithOverlay(p, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ, targetAlpha * vanillaMul, tintR, tintG, tintB);
        }
    }

    private static void renderFreeBillboardWithOverlay(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ, float targetAlpha, float tintR, float tintG, float tintB) {
        BloodRender.renderTwoPass(p, targetAlpha, tintR, tintG, tintB, () -> BloodRender.renderFreeBillboardGeometry(p, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ));
    }

    private static void renderFreeBillboardGeometry(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        if (BloodSlimy.hasGroundBounceVisual(p)) {
            BloodRender.renderSlimyBounceBillboard(p, buffer, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
            return;
        }
        p.vanillaRenderParticle(buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
    }

    private static void renderSlimyBounceBillboard(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        TextureAtlasSprite sprite = p.getSprite();
        if (sprite == null) {
            return;
        }
        float minU = sprite.getU0();
        float maxU = sprite.getU1();
        float minV = sprite.getV0();
        float maxV = sprite.getV1();
        float uA = p.flipU ? minU : maxU;
        float uB = p.flipU ? maxU : minU;
        float sx = Math.max(0.001f, 0.1f * p.getScale() * BloodSlimy.groundBounceBillboardScaleX(p, partialTicks));
        float sy = Math.max(0.001f, 0.1f * p.getScale() * BloodSlimy.groundBounceBillboardScaleY(p, partialTicks));
        double x = p.prevPosX + (p.posX - p.prevPosX) * (double)partialTicks - ParticleBlood.getInterpX();
        double y = p.prevPosY + (p.posY - p.prevPosY) * (double)partialTicks - ParticleBlood.getInterpY();
        double z = p.prevPosZ + (p.posZ - p.prevPosZ) * (double)partialTicks - ParticleBlood.getInterpZ();
        int br = p.getBrightnessForRender(partialTicks);
        int lmHi = br >> 16 & 0xFFFF;
        int lmLo = br & 0xFFFF;
        double ax = (double)rotationX * (double)sx;
        double az = (double)rotationYZ * (double)sx;
        double bx = (double)rotationXY * (double)sy;
        double by = (double)rotationZ * (double)sy;
        double bz = (double)rotationXZ * (double)sy;
        buffer.pos(x - ax - bx, y - by, z - az - bz).tex((double)uA, (double)maxV).color(p.getRed(), p.getGreen(), p.getBlue(), p.getAlpha()).lightmap(lmHi, lmLo).endVertex();
        buffer.pos(x - ax + bx, y + by, z - az + bz).tex((double)uA, (double)minV).color(p.getRed(), p.getGreen(), p.getBlue(), p.getAlpha()).lightmap(lmHi, lmLo).endVertex();
        buffer.pos(x + ax + bx, y + by, z + az + bz).tex((double)uB, (double)minV).color(p.getRed(), p.getGreen(), p.getBlue(), p.getAlpha()).lightmap(lmHi, lmLo).endVertex();
        buffer.pos(x + ax - bx, y - by, z + az - bz).tex((double)uB, (double)maxV).color(p.getRed(), p.getGreen(), p.getBlue(), p.getAlpha()).lightmap(lmHi, lmLo).endVertex();
    }

    private static void renderVanillaWithOverlay(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ, float targetAlpha, float tintR, float tintG, float tintB) {
        BloodRender.renderTwoPass(p, targetAlpha, tintR, tintG, tintB, () -> p.vanillaRenderParticle(buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ));
    }

    private static void renderDecalWithOverlay(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, float partialTicks, float targetAlpha, float tintR, float tintG, float tintB) {
        float attachFade = BloodRender.ceilingAttachFadeMul(p, partialTicks);
        float alpha = Util.clamp01(targetAlpha * attachFade);
        if (alpha <= 1.0E-6f) {
            return;
        }
        BloodRender.renderTwoPass(p, alpha, tintR, tintG, tintB, () -> BloodRender.renderDecalPolys(p, buffer, partialTicks));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static void renderTwoPass(@Nonnull ParticleBlood p, float targetAlpha, float tintR, float tintG, float tintB, @Nonnull Runnable geometry) {
        if ((targetAlpha = Util.clamp01(targetAlpha)) <= 1.0E-6f) {
            return;
        }
        boolean slimy = BloodSlimy.isSlimy(p);
        float baseDarkMul = slimy ? 0.24f : 0.35f;
        float overlayAlphaMul = slimy ? 0.96f : 0.85f;
        float oldR = p.getRed();
        float oldG = p.getGreen();
        float oldB = p.getBlue();
        float oldA = p.getAlpha();
        float passA0 = BloodRender.solveBasePassAlphaForTarget(targetAlpha, overlayAlphaMul);
        float passA1 = Util.clamp01(passA0 * overlayAlphaMul);
        try {
            BloodRender.applyTint(p, tintR * baseDarkMul, tintG * baseDarkMul, tintB * baseDarkMul, passA0);
            geometry.run();
            if (passA1 > 1.0E-6f) {
                BloodRender.applyTint(p, tintR, tintG, tintB, passA1);
                geometry.run();
            }
        }
        finally {
            p.setRGBA(oldR, oldG, oldB, oldA);
        }
    }

    private static void applyTint(@Nonnull ParticleBlood p, float r, float g, float b, float a) {
        p.setRGBA(Util.clamp01(r), Util.clamp01(g), Util.clamp01(b), Util.clamp01(a));
    }

    private static float solveBasePassAlphaForTarget(float targetAlpha, float overlayAlphaMul) {
        double x;
        targetAlpha = Util.clamp01(targetAlpha);
        float overlay = Util.clamp01(overlayAlphaMul);
        if (overlay <= 1.0E-6f) {
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
        double d = x = x1 >= 0.0 && x1 <= 1.0 ? x1 : x2;
        if (x < 0.0) {
            x = 0.0;
        }
        if (x > 1.0) {
            x = 1.0;
        }
        return (float)x;
    }

    private static float fallingVanillaBlend01(@Nullable ParticleBlood p, float partialTicks) {
        if (p == null || !p.fallingDripActive) {
            return 0.0f;
        }
        if (p.fallingDripStartAge < 0) {
            return 1.0f;
        }
        float ageF = (float)p.getAge() + partialTicks;
        float elapsed = ageF - (float)p.fallingDripStartAge;
        if (elapsed <= 40.0f) {
            return 0.0f;
        }
        float denom = Math.max(1.0f, 4.0f);
        float t = (elapsed - 40.0f) / denom;
        return Util.smoothstep01(Util.clamp01(t));
    }

    private static float ceilingAttachFadeMul(@Nullable ParticleBlood p, float partialTicks) {
        if (p == null) {
            return 1.0f;
        }
        if (!p.isStuck) {
            return 1.0f;
        }
        if (p.stuckFace != Direction.DOWN) {
            return 1.0f;
        }
        if (p.stuckStartAge < 0) {
            return 1.0f;
        }
        return BloodRender.fadeFromAge01(p.getAge(), partialTicks, p.stuckStartAge, 6.0f);
    }

    private static float ceilingTailFadeMul(@Nullable ParticleBlood p, float partialTicks) {
        if (p == null) {
            return 1.0f;
        }
        if (!p.isStuck) {
            return 1.0f;
        }
        if (p.stuckFace != Direction.DOWN) {
            return 1.0f;
        }
        if (!p.ceilingDripEnabled || p.ceilingDripConsumed) {
            return 1.0f;
        }
        if (p.ceilingDripStartAge < 0) {
            return 1.0f;
        }
        if (p.stuckStartAge >= 0 && p.ceilingDripStartAge == p.stuckStartAge) {
            return 1.0f;
        }
        return BloodRender.fadeFromAge01(p.getAge(), partialTicks, p.ceilingDripStartAge, 4.0f);
    }

    private static float fadeFromAge01(int age, float partialTicks, int startAge, float durationTicks) {
        if (startAge < 0) {
            return 1.0f;
        }
        if (durationTicks <= 1.0E-6f) {
            return 1.0f;
        }
        float t = ((float)age + partialTicks - (float)startAge) / durationTicks;
        return Util.smoothstep01(Util.clamp01(t));
    }

    private static boolean isVerticalSegmentQuad(@Nullable List<Util.Vertex> poly) {
        boolean botY;
        if (poly == null || poly.size() != 4) {
            return false;
        }
        Util.Vertex v0 = poly.get(0);
        Util.Vertex v1 = poly.get(1);
        Util.Vertex v2 = poly.get(2);
        Util.Vertex v3 = poly.get(3);
        if (v0 == null || v1 == null || v2 == null || v3 == null) {
            return false;
        }
        double eps = 1.0E-6;
        boolean leftXZ = Math.abs(v0.x - v1.x) < 1.0E-6 && Math.abs(v0.z - v1.z) < 1.0E-6;
        boolean rightXZ = Math.abs(v2.x - v3.x) < 1.0E-6 && Math.abs(v2.z - v3.z) < 1.0E-6;
        boolean topY = Math.abs(v0.y - v3.y) < 1.0E-6;
        boolean bl = botY = Math.abs(v1.y - v2.y) < 1.0E-6;
        if (!(leftXZ && rightXZ && topY && botY)) {
            return false;
        }
        double dy = Math.abs(v0.y - v1.y);
        return dy > 0.002;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static void renderDecalPolys(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, float partialTicks) {
        PistonSupport.MovingInfo miNow;
        if (p.cache.shape.polys == null || p.cache.shape.polys.isEmpty()) {
            return;
        }
        int br = p.getBrightnessForRender(partialTicks);
        int lmHi = br >> 16 & 0xFFFF;
        int lmLo = br & 0xFFFF;
        double ix = p.prevPosX + (p.posX - p.prevPosX) * (double)partialTicks;
        double iy = p.prevPosY + (p.posY - p.prevPosY) * (double)partialTicks;
        double iz = p.prevPosZ + (p.posZ - p.prevPosZ) * (double)partialTicks;
        double baseX = Double.isNaN(p.cache.view.x) ? p.posX : p.cache.view.x;
        double baseY = Double.isNaN(p.cache.view.y) ? p.posY : p.cache.view.y;
        double baseZ = Double.isNaN(p.cache.view.z) ? p.posZ : p.cache.view.z;
        double dx = ix - baseX;
        double dy = iy - baseY;
        double dz = iz - baseZ;
        Level world = p.getParticleWorld();
        if (p.isStuck && p.stuckPos != null && world != null && (miNow = PistonSupport.getMovingInfo(world, p.stuckPos, partialTicks, p)) != null && miNow.offset != null && !miNow.staticBaseNoOffset) {
            PistonSupport.MovingInfo miBuild = PistonSupport.getMovingInfo(world, p.stuckPos, 1.0f, p);
            Vec3 offNow = miNow.offset;
            Vec3 offBuild = miBuild != null && miBuild.offset != null ? miBuild.offset : Util.ZERO;
            dx = offNow.x - offBuild.x;
            dy = offNow.y - offBuild.y;
            dz = offNow.z - offBuild.z;
        }
        Entity viewEnt = p.isStuck && world != null ? BloodRender.getViewEntitySafe(null) : null;
        Vec3 viewEye = viewEnt != null && viewEnt.level() == world ? BloodRender.getViewEyeSafe(viewEnt, partialTicks) : null;
        boolean doWaterCutout = viewEye != null;
        float baseA = p.getAlpha();
        float tailMul = BloodRender.ceilingTailFadeMul(p, partialTicks);
        List<List<Util.Vertex>> drawPolys = p.isStuck && p.stuckFace == Direction.DOWN ? BloodRender.sortCeilingPolysBackToFront(p.cache.shape.polys, dx, dy, dz) : p.cache.shape.polys;
        try {
            for (List<Util.Vertex> poly : drawPolys) {
                if (poly == null || poly.size() < 3) continue;
                if (tailMul < 0.9999f && p.isStuck && p.stuckFace == Direction.DOWN && BloodRender.isVerticalSegmentQuad(poly)) {
                    p.setAlpha(baseA * tailMul);
                } else {
                    p.setAlpha(baseA);
                }
                if (!doWaterCutout) {
                    BloodRender.renderPolyAsQuads(p, buffer, poly, lmHi, lmLo, dx, dy, dz);
                    continue;
                }
                List<Util.Vertex> worldPoly = BloodRender.offsetPoly(poly, dx, dy, dz);
                List<Util.Vertex> visiblePoly = BloodRender.clipPolyToVisibleThroughWater(world, viewEye, worldPoly);
                if (visiblePoly.size() < 3) continue;
                BloodRender.renderPolyAsQuads(p, buffer, visiblePoly, lmHi, lmLo, 0.0, 0.0, 0.0);
            }
        }
        finally {
            p.setAlpha(baseA);
        }
    }

    @Nonnull
    private static List<List<Util.Vertex>> sortCeilingPolysBackToFront(@Nonnull List<List<Util.Vertex>> polys, double dx, double dy, double dz) {
        if (polys.size() <= 1) {
            return polys;
        }
        double camX = ParticleBlood.getInterpX();
        double camY = ParticleBlood.getInterpY();
        double camZ = ParticleBlood.getInterpZ();
        ArrayList<List<Util.Vertex>> out = new ArrayList<List<Util.Vertex>>(polys.size());
        for (List<Util.Vertex> poly : polys) {
            if (poly == null || poly.size() < 3) continue;
            out.add(poly);
        }
        if (out.size() <= 1) {
            return out;
        }
        out.sort((a, b) -> Double.compare(BloodRender.minPolyDistanceSq(b, dx, dy, dz, camX, camY, camZ), BloodRender.minPolyDistanceSq(a, dx, dy, dz, camX, camY, camZ)));
        return out;
    }

    private static double minPolyDistanceSq(@Nullable List<Util.Vertex> poly, double dx, double dy, double dz, double camX, double camY, double camZ) {
        if (poly == null || poly.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        double best = Double.POSITIVE_INFINITY;
        for (Util.Vertex v : poly) {
            double pz;
            double py;
            double px;
            double dsq;
            if (v == null || !((dsq = (px = v.x + dx - camX) * px + (py = v.y + dy - camY) * py + (pz = v.z + dz - camZ) * pz) < best)) continue;
            best = dsq;
        }
        return best;
    }

    @Nonnull
    private static List<Util.Vertex> offsetPoly(@Nonnull List<Util.Vertex> poly, double dx, double dy, double dz) {
        if (Math.abs(dx) <= 1.0E-12 && Math.abs(dy) <= 1.0E-12 && Math.abs(dz) <= 1.0E-12) {
            return poly;
        }
        ArrayList<Util.Vertex> out = new ArrayList<Util.Vertex>(poly.size());
        for (Util.Vertex v : poly) {
            out.add(new Util.Vertex(v.x + dx, v.y + dy, v.z + dz, v.u, v.v));
        }
        return out;
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

    @Nullable
    private static Vec3 getViewEyeSafe(@Nullable Entity view, float partialTicks) {
        if (view == null) {
            return null;
        }
        try {
            return view.getEyePosition(partialTicks);
        }
        catch (Throwable ignored) {
            return new Vec3(view.xo + (view.getX() - view.xo) * (double)partialTicks, view.yo + (view.getY() - view.yo) * (double)partialTicks + (double)view.getEyeHeight(), view.zo + (view.getZ() - view.zo) * (double)partialTicks);
        }
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

    private static boolean isPointOccludedByWater(@Nonnull Level world, @Nonnull Vec3 eye, @Nonnull Util.Vertex v) {
        Vec3 point = new Vec3(v.x, v.y, v.z);
        Vec3 eyeProbe = eye.add(0.0, -0.02, 0.0);
        boolean eyeInWater = BloodRender.isPointInsideWaterVolume(world, eye) || BloodRender.isPointInsideWaterVolume(world, eyeProbe);
        boolean pointInWater = BloodRender.isPointInsideWaterVolume(world, point);
        if (eyeInWater && pointInWater) {
            return false;
        }
        Vec3 rayStart = eyeInWater ? eyeProbe : eye;
        return BloodRender.isAnyWaterVolumeOnRay(world, rayStart, point);
    }

    private static boolean isPointInsideWaterVolume(@Nullable Level world, @Nonnull Vec3 point) {
        BlockState upState;
        if (world == null) {
            return false;
        }
        BlockPos cell = BlockPos.containing(point.x, point.y, point.z);
        if (!world.isLoaded(cell)) {
            return false;
        }
        BlockState state = BloodRender.safeGetState(world, cell);
        if (state == null || GoreMaterial.of(state) != GoreMaterial.WATER) {
            return false;
        }
        BlockPos up = cell.above();
        if (world.isLoaded(up) && (upState = BloodRender.safeGetState(world, up)) != null && GoreMaterial.of(upState) == GoreMaterial.WATER) {
            return true;
        }
        float hNW = BloodRender.getFluidCornerHeight(world, cell, GoreMaterial.WATER);
        float hNE = BloodRender.getFluidCornerHeight(world, cell.east(), GoreMaterial.WATER);
        float hSW = BloodRender.getFluidCornerHeight(world, cell.south(), GoreMaterial.WATER);
        float hSE = BloodRender.getFluidCornerHeight(world, cell.south().east(), GoreMaterial.WATER);
        return BloodRender.isPointBelowWaterSurface(((net.minecraft.core.Vec3i) cell).getY(), point.x - (double)((net.minecraft.core.Vec3i) cell).getX(), point.y, point.z - (double)((net.minecraft.core.Vec3i) cell).getZ(), hNW, hNE, hSW, hSE);
    }

    @Nonnull
    private static Util.Vertex lerpVertex(@Nonnull Util.Vertex a, @Nonnull Util.Vertex b, double t) {
        return new Util.Vertex(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t, (float)((double)a.u + (double)(b.u - a.u) * t), (float)((double)a.v + (double)(b.v - a.v) * t));
    }

    @Nonnull
    private static Util.Vertex findOcclusionBoundaryOnEdge(@Nonnull Level world, @Nonnull Vec3 eye, @Nonnull Util.Vertex a, boolean occA, @Nonnull Util.Vertex b, boolean occB) {
        if (occA == occB) {
            return BloodRender.lerpVertex(a, b, 0.5);
        }
        double lo = 0.0;
        double hi = 1.0;
        boolean loOcc = occA;
        for (int i = 0; i < 6; ++i) {
            double mid = 0.5 * (lo + hi);
            Util.Vertex m = BloodRender.lerpVertex(a, b, mid);
            boolean midOcc = BloodRender.isPointOccludedByWater(world, eye, m);
            if (midOcc == loOcc) {
                lo = mid;
                continue;
            }
            hi = mid;
        }
        return BloodRender.lerpVertex(a, b, 0.5 * (lo + hi));
    }

    @Nonnull
    private static List<Util.Vertex> clipPolyToVisibleThroughWater(@Nonnull Level world, @Nonnull Vec3 eye, @Nonnull List<Util.Vertex> in) {
        boolean prevVisible;
        if (in.size() < 3) {
            return new ArrayList<Util.Vertex>(0);
        }
        ArrayList<Util.Vertex> out = new ArrayList<Util.Vertex>(in.size() + 4);
        Util.Vertex prev = in.get(in.size() - 1);
        boolean prevOccluded = BloodRender.isPointOccludedByWater(world, eye, prev);
        boolean anyVisible = prevVisible = !prevOccluded;
        boolean anyHidden = prevOccluded;
        for (Util.Vertex curr : in) {
            boolean currOccluded = BloodRender.isPointOccludedByWater(world, eye, curr);
            boolean currVisible = !currOccluded;
            anyVisible |= currVisible;
            anyHidden |= currOccluded;
            if (currVisible) {
                if (!prevVisible) {
                    out.add(BloodRender.findOcclusionBoundaryOnEdge(world, eye, prev, prevOccluded, curr, currOccluded));
                }
                out.add(curr);
            } else if (prevVisible) {
                out.add(BloodRender.findOcclusionBoundaryOnEdge(world, eye, prev, prevOccluded, curr, currOccluded));
            }
            prev = curr;
            prevOccluded = currOccluded;
            prevVisible = currVisible;
        }
        if (!anyHidden) {
            return in;
        }
        if (!anyVisible) {
            return new ArrayList<Util.Vertex>(0);
        }
        return out;
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
            if (world.isLoaded((BlockPos)cursor) && (state = BloodRender.safeGetState(world, (BlockPos)cursor)) != null && GoreMaterial.of(state) == GoreMaterial.WATER && BloodRender.segmentIntersectsWaterVolume(world, (BlockPos)cursor, start, rayX, rayY, rayZ, tCur, tNext)) {
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
        if (world.isLoaded(up) && (upState = BloodRender.safeGetState(world, up)) != null && GoreMaterial.of(upState) == GoreMaterial.WATER) {
            return true;
        }
        float hNW = BloodRender.getFluidCornerHeight(world, cell, GoreMaterial.WATER);
        float hNE = BloodRender.getFluidCornerHeight(world, cell.east(), GoreMaterial.WATER);
        float hSW = BloodRender.getFluidCornerHeight(world, cell.south(), GoreMaterial.WATER);
        float hSE = BloodRender.getFluidCornerHeight(world, cell.south().east(), GoreMaterial.WATER);
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
        if (BloodRender.isPointBelowWaterSurface(by, u0, y0, v0, hNW, hNE, hSW, hSE)) {
            return true;
        }
        if (BloodRender.isPointBelowWaterSurface(by, u1, y1, v1, hNW, hNE, hSW, hSE)) {
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
        return BloodRender.isPointBelowWaterSurface(by, xDiag - (double)bx, yDiag, zDiag - (double)bz, hNW, hNE, hSW, hSE);
    }

    private static boolean isPointBelowWaterSurface(int blockY, double localX, double worldY, double localZ, float hNW, float hNE, float hSW, float hSE) {
        double surfaceY = (double)blockY + BloodRender.waterSurfaceFracAt(BloodRender.clamp01d(localX), BloodRender.clamp01d(localZ), hNW, hNE, hSW, hSE);
        return worldY <= surfaceY - 0.002;
    }

    private static double waterSurfaceFracAt(double u, double v, float hNW, float hNE, float hSW, float hSE) {
        double nw = BloodRender.clamp01d(hNW);
        double ne = BloodRender.clamp01d(hNE);
        double sw = BloodRender.clamp01d(hSW);
        double se = BloodRender.clamp01d(hSE);
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
            if (world.isLoaded(samplePosUp) && (above = BloodRender.safeGetState(world, samplePosUp)) != null && GoreMaterial.of(above) == mat) {
                return 1.0f;
            }
            if (!world.isLoaded(samplePos) || (state = BloodRender.safeGetState(world, samplePos)) == null) continue;
            GoreMaterial sampleMat = GoreMaterial.of(state);
            if (sampleMat == mat) {
                int level = BloodRender.getLiquidLevelRaw(state);
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

    private static void renderPolyAsQuads(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, @Nonnull List<Util.Vertex> poly, int lmHi, int lmLo, double dx, double dy, double dz) {
        if (poly.size() == 4) {
            BloodRender.emit(p, buffer, poly.get(0), lmHi, lmLo, dx, dy, dz);
            BloodRender.emit(p, buffer, poly.get(1), lmHi, lmLo, dx, dy, dz);
            BloodRender.emit(p, buffer, poly.get(2), lmHi, lmLo, dx, dy, dz);
            BloodRender.emit(p, buffer, poly.get(3), lmHi, lmLo, dx, dy, dz);
            return;
        }
        Util.Vertex a = poly.get(0);
        for (int i = 1; i < poly.size() - 1; ++i) {
            Util.Vertex b = poly.get(i);
            Util.Vertex c = poly.get(i + 1);
            BloodRender.emit(p, buffer, a, lmHi, lmLo, dx, dy, dz);
            BloodRender.emit(p, buffer, b, lmHi, lmLo, dx, dy, dz);
            BloodRender.emit(p, buffer, c, lmHi, lmLo, dx, dy, dz);
            BloodRender.emit(p, buffer, c, lmHi, lmLo, dx, dy, dz);
        }
    }

    private static void emit(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, @Nonnull Util.Vertex v, int lmHi, int lmLo, double dx, double dy, double dz) {
        double x = v.x + dx - ParticleBlood.getInterpX();
        double y = v.y + dy - ParticleBlood.getInterpY();
        double z = v.z + dz - ParticleBlood.getInterpZ();
        buffer.pos(x, y, z).tex((double)v.u, (double)v.v).color(p.getRed(), p.getGreen(), p.getBlue(), p.getAlpha()).lightmap(lmHi, lmLo).endVertex();
    }
}

