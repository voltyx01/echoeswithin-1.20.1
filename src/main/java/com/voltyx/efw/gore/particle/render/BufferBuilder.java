package com.voltyx.efw.gore.particle.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;

import java.util.Arrays;

/**
 * The vertex writer of 1.12 ({@code buffer.pos(..).tex(..).color(..).lightmap(..).endVertex()}) that the Extra Gore drawing code is
 * written against. Vertices (camera relative, as in 1.12) are kept here and drawn as quads by {@link #draw} with the matrix of the
 * frame, so the drawing code itself did not have to change.
 *
 * <p>{@link #PARTICLES} collects what particles draw while the particle engine walks over them (1.12's particle pass),
 * {@link #WORLD_LAST} the sorted batch of the gore renderer after it.</p>
 */
public final class BufferBuilder {
    public static final BufferBuilder PARTICLES = new BufferBuilder();
    public static final BufferBuilder WORLD_LAST = new BufferBuilder();

    /** x y z u v r g b a block sky */
    private static final int STRIDE = 11;
    private float[] data = new float[STRIDE * 4 * 256];
    private int size;
    private double x;
    private double y;
    private double z;
    private float u;
    private float v;
    private float r = 1.0f;
    private float g = 1.0f;
    private float b = 1.0f;
    private float a = 1.0f;
    private int sky;
    private int block;

    private BufferBuilder() {
    }

    public BufferBuilder pos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public BufferBuilder tex(double u, double v) {
        this.u = (float) u;
        this.v = (float) v;
        return this;
    }

    public BufferBuilder color(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
        return this;
    }

    public BufferBuilder color(int r, int g, int b, int a) {
        return color(r / 255.0f, g / 255.0f, b / 255.0f, a / 255.0f);
    }

    /** 1.12 order: the sky light first, then the block light. */
    public BufferBuilder lightmap(int skyLight, int blockLight) {
        this.sky = skyLight;
        this.block = blockLight;
        return this;
    }

    public BufferBuilder normal(float x, float y, float z) {
        return this;
    }

    public void endVertex() {
        if (size + STRIDE > data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }
        float[] d = data;
        int i = size;
        d[i] = (float) x;
        d[i + 1] = (float) y;
        d[i + 2] = (float) z;
        d[i + 3] = u;
        d[i + 4] = v;
        d[i + 5] = r;
        d[i + 6] = g;
        d[i + 7] = b;
        d[i + 8] = a;
        d[i + 9] = block;
        d[i + 10] = sky;
        size += STRIDE;
    }

    public boolean isEmpty() {
        return size < STRIDE * 4;
    }

    public void clear() {
        size = 0;
    }

    /**
     * Draws the collected quads (block format: the translucent terrain shader takes it, with the light map and the fog and without
     * an alpha cut, like 1.12's alpha func near zero) and empties the buffer. The caller sets up the render state.
     */
    public void draw(Matrix4f pose) {
        int vertices = (size / STRIDE) / 4 * 4;
        if (vertices == 0) {
            size = 0;
            return;
        }
        com.mojang.blaze3d.vertex.BufferBuilder out = Tesselator.getInstance().getBuilder();
        out.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        float[] d = data;
        for (int n = 0; n < vertices; n++) {
            int i = n * STRIDE;
            out.vertex(pose, d[i], d[i + 1], d[i + 2])
                    .color(d[i + 5], d[i + 6], d[i + 7], d[i + 8])
                    .uv(d[i + 3], d[i + 4])
                    .uv2((int) d[i + 9], (int) d[i + 10])
                    .normal(0.0f, 1.0f, 0.0f)
                    .endVertex();
        }
        size = 0;
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(out.end());
    }
}
