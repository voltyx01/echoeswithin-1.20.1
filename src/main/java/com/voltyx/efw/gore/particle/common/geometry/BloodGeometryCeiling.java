package com.voltyx.efw.gore.particle.common.geometry;

import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.Util;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.Direction;
public final class BloodGeometryCeiling {
    private static final double JOIN = 1.2E-4;

    public static float computeCapHeight(@Nonnull ParticleBlood p) {
        float s = 0.1f * p.getScale();
        float cap = s * 0.75f;
        if (cap < 0.03f) {
            cap = 0.03f;
        }
        return cap;
    }

    public static double computeDripTopY(@Nonnull ParticleBlood p) {
        return p.posY + 1.5E-4;
    }

    public static double computeDripTipY(@Nonnull ParticleBlood p, float bodyLen) {
        float capH = BloodGeometryCeiling.computeCapHeight(p);
        float body = Math.max(0.0f, bodyLen);
        return BloodGeometryCeiling.computeDripTopY(p) - (double)capH - (double)body;
    }

    @Nullable
    public static List<List<Util.Vertex>> buildTailPolys(@Nonnull ParticleBlood p, @Nonnull TextureAtlasSprite sprite) {
        float body;
        if (!p.isStuck || p.stuckFace != Direction.DOWN) {
            return null;
        }
        if (!p.ceilingDripEnabled || p.ceilingDripStartAge < 0) {
            return null;
        }
        float capH = BloodGeometryCeiling.computeCapHeight(p);
        double totalLen = (double)capH + (double)(body = Math.max(0.0f, p.dripAmount));
        if (totalLen <= 1.0E-6) {
            return null;
        }
        double yTop = p.posY + 1.5E-4;
        double yBot = yTop - totalLen;
        UvBand uv = UvBand.of(sprite);
        float baseHalfW = capH * 0.6f;
        baseHalfW = (float)Util.clamp(baseHalfW, 0.012, 0.08);
        float prog = 0.0f;
        if (p.ceilingDripTargetLen > 1.0E-6f) {
            prog = Util.clamp01(p.dripAmount / p.ceilingDripTargetLen);
        }
        float shrink = Util.clamp01(p.planCeilingShrinkAtFull);
        float halfW = baseHalfW * (1.0f - shrink * prog);
        halfW = (float)Util.clamp(halfW, 0.01, 0.08);
        Axis axis = Axis.yawBillboard(p);
        List<Util.Vertex> drip = BloodGeometryCeiling.buildSegmentQuad(p, uv, axis, halfW, yTop, yBot);
        if (drip == null) {
            return null;
        }
        ArrayList<List<Util.Vertex>> out = new ArrayList<List<Util.Vertex>>(1);
        out.add(drip);
        return out;
    }

    @Nullable
    public static List<List<Util.Vertex>> buildFallPolys(@Nonnull ParticleBlood p, @Nonnull TextureAtlasSprite sprite) {
        if (!p.fallingDripActive) {
            return null;
        }
        float len = p.fallingDripLen;
        if (len <= 1.0E-6f) {
            return null;
        }
        double yBot = p.posY;
        double yTop = yBot + (double)len;
        UvBand uv = UvBand.of(sprite);
        float capH = BloodGeometryCeiling.computeCapHeight(p);
        float baseHalfW = capH * 0.6f;
        baseHalfW = (float)Util.clamp(baseHalfW, 0.012, 0.08);
        float ratio = 1.0f;
        if (p.fallingDripStartLen > 1.0E-6f) {
            ratio = Util.clamp01(p.fallingDripLen / p.fallingDripStartLen);
        }
        float shrink = Util.clamp01(p.planCeilingShrinkAtFull);
        float halfW = baseHalfW * (1.0f - shrink * ratio);
        halfW = (float)Util.clamp(halfW, 0.01, 0.08);
        Axis axis = Axis.yawBillboard(p);
        List<Util.Vertex> trail = BloodGeometryCeiling.buildSegmentQuad(p, uv, axis, halfW, yTop, yBot - 1.2E-4);
        float vanillaHalf = 0.1f * p.getScale();
        vanillaHalf = (float)Util.clamp(vanillaHalf, 0.01, 0.12);
        float blobHalf = halfW + (vanillaHalf - halfW) * (1.0f - ratio);
        blobHalf = (float)Util.clamp(blobHalf, 0.01, 0.12);
        double blobTop = yBot + 1.2E-4;
        double blobBot = blobTop - (double)(blobHalf * 2.0f);
        List<Util.Vertex> blob = BloodGeometryCeiling.buildSegmentQuad(p, uv, axis, blobHalf, blobTop, blobBot);
        ArrayList<List<Util.Vertex>> out = new ArrayList<List<Util.Vertex>>(2);
        if (trail != null) {
            out.add(trail);
        }
        if (blob != null) {
            out.add(blob);
        }
        return out.isEmpty() ? null : out;
    }

    @Nullable
    private static List<Util.Vertex> buildSegmentQuad(@Nonnull ParticleBlood p, @Nonnull UvBand uv, @Nonnull Axis axis, float halfW, double yTop, double yBot) {
        float uL = p.flipU ? uv.u1 : uv.u0;
        float uR = p.flipU ? uv.u0 : uv.u1;
        double xL = p.posX - axis.ax * (double)halfW;
        double zL = p.posZ - axis.az * (double)halfW;
        double xR = p.posX + axis.ax * (double)halfW;
        double zR = p.posZ + axis.az * (double)halfW;
        ArrayList<Util.Vertex> poly = new ArrayList<Util.Vertex>(4);
        poly.add(new Util.Vertex(xL, yTop, zL, uL, uv.v0));
        poly.add(new Util.Vertex(xL, yBot, zL, uL, uv.v1));
        poly.add(new Util.Vertex(xR, yBot, zR, uR, uv.v1));
        poly.add(new Util.Vertex(xR, yTop, zR, uR, uv.v0));
        return poly;
    }

    private static final class Axis {
        final double ax;
        final double az;

        private Axis(double ax, double az) {
            this.ax = ax;
            this.az = az;
        }

        static Axis yawBillboard(@Nonnull ParticleBlood p) {
            double dz;
            double dx;
            Minecraft mc = Minecraft.getInstance();
            Entity view = mc.getCameraEntity();
            if (view != null) {
                dx = view.getX() - p.posX;
                dz = view.getZ() - p.posZ;
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len <= 1.0E-6) {
                    double yawRad = Math.toRadians(view.getYRot());
                    dx = -Math.sin(yawRad);
                    dz = Math.cos(yawRad);
                    len = Math.sqrt(dx * dx + dz * dz);
                }
                if (len > 1.0E-6) {
                    dx /= len;
                    dz /= len;
                } else {
                    dx = Math.cos(p.groundRot);
                    dz = Math.sin(p.groundRot);
                }
            } else {
                dx = Math.cos(p.groundRot);
                dz = Math.sin(p.groundRot);
            }
            return new Axis(dz, -dx);
        }
    }

    private static final class UvBand {
        final float u0;
        final float u1;
        final float v0;
        final float v1;

        private UvBand(float u0, float u1, float v0, float v1) {
            this.u0 = u0;
            this.u1 = u1;
            this.v0 = v0;
            this.v1 = v1;
        }

        static UvBand of(@Nonnull TextureAtlasSprite sprite) {
            float uMin = sprite.getU0();
            float uMax = sprite.getU1();
            float vMin = sprite.getV0();
            float vMax = sprite.getV1();
            float du = uMax - uMin;
            float dv = vMax - vMin;
            return new UvBand(uMin + du * 0.15f, uMin + du * 0.85f, vMin + dv * 0.25f, vMin + dv * 0.85f);
        }
    }
}

