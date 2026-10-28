package com.voltyx.efw.notes.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * A buffer source that draws nothing and only keeps the box around every vertex it gets: the inspect screen renders the item into
 * it once to learn the real size and middle of the model (guns of every pack differ wildly).
 */
final class InspectBounds implements MultiBufferSource, VertexConsumer {
    record Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float sizeX() {
            return maxX - minX;
        }

        float sizeY() {
            return maxY - minY;
        }

        float sizeZ() {
            return maxZ - minZ;
        }

        float maxSize() {
            return Math.max(1.0e-4f, Math.max(sizeX(), Math.max(sizeY(), sizeZ())));
        }

        float centerX() {
            return (minX + maxX) / 2.0f;
        }

        float centerY() {
            return (minY + maxY) / 2.0f;
        }

        float centerZ() {
            return (minZ + maxZ) / 2.0f;
        }
    }

    private float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
    private float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
    private boolean any;

    Box box() {
        return any ? new Box(minX, minY, minZ, maxX, maxY, maxZ) : null;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return this;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        any = true;
        minX = Math.min(minX, (float) x);
        minY = Math.min(minY, (float) y);
        minZ = Math.min(minZ, (float) z);
        maxX = Math.max(maxX, (float) x);
        maxY = Math.max(maxY, (float) y);
        maxZ = Math.max(maxZ, (float) z);
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return this;
    }

    @Override
    public void endVertex() {
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
    }

    @Override
    public void unsetDefaultColor() {
    }
}
