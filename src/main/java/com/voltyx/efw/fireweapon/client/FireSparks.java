package com.voltyx.efw.fireweapon.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.voltyx.efw.EFW;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Glowing incandescent sparks of the burning weapons (port of FireWeaponSparkManager): they leave the burning pixels of the
 * weapon, fly off a struck mob and rise from smoldering mobs. All sparks live in absolute world coordinates, so they stay behind
 * when the player walks away. They are drawn as additive ribbons after the particles.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class FireSparks {
    private static final Random RAND = new Random();
    private static final int MAX_SPARKS = 600;
    private static final int MAX_SPARKS_SOAKED = 900;

    /** Bounding box of a burning pixel of the weapon in local [0..1] item space. */
    public record Voxel(float xMin, float xMax, float yMin, float yMax, float zMin, float zMax, boolean front) {
    }

    private static final class Spark {
        double x, y, z;
        float vx, vy, vz;
        float length;
        float grav = 0.85f;
        float age;
        final float maxAge;

        Spark(double x, double y, double z, float vx, float vy, float vz) {
            this.x = x + (RAND.nextFloat() - 0.5f) * 0.015f;
            this.y = y + (RAND.nextFloat() - 0.5f) * 0.015f;
            this.z = z + (RAND.nextFloat() - 0.5f) * 0.015f;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.length = 0.26f + RAND.nextFloat() * 0.28f;
            this.maxAge = 0.80f + RAND.nextFloat() * 0.70f;
        }

        boolean update(float dt) {
            age += dt;
            if (age >= maxAge) {
                return false;
            }
            x += vx * dt;
            y += vy * dt;
            z += vz * dt;
            vy -= grav * dt; // gravity
            float drag = 1.0f - 0.40f * dt;
            vx *= drag;
            vy *= drag;
            vz *= drag;
            return true;
        }

        /** Hot white-yellow core, orange body, cooling ash. */
        void color(float[] out) {
            float t = age / maxAge;
            out[3] = Math.max(0.0f, Math.min(1.0f, t < 0.2f ? t / 0.2f : 1.0f - (t - 0.2f) / 0.8f));
            if (t < 0.25f) {
                float p = t / 0.25f;
                out[0] = 1.0f;
                out[1] = 0.95f - 0.2f * p;
                out[2] = 0.8f - 0.6f * p;
            } else if (t < 0.65f) {
                float p = (t - 0.25f) / 0.40f;
                out[0] = 1.0f;
                out[1] = 0.75f - 0.45f * p;
                out[2] = 0.1f * (1.0f - p);
            } else {
                float p = (t - 0.65f) / 0.35f;
                out[0] = 0.9f - 0.5f * p;
                out[1] = 0.25f - 0.15f * p;
                out[2] = 0.05f + 0.1f * p;
            }
        }
    }

    private static final List<Spark> SPARKS = new ArrayList<>(MAX_SPARKS);
    /** Sparks of the burning weapons seen in inventories: x, y in GUI pixels, y down. */
    private static final List<Spark> GUI_SPARKS = new ArrayList<>();
    private static final int MAX_GUI_SPARKS = 300;
    private static boolean inScreen;
    private static long lastGuiNanos;
    private static float guiDt = 0.016f;
    private static long lastFrameNanos;
    /** Seconds of the last frame: the sparks of every weapon drawn in a frame share it. */
    private static float emitDt = 0.016f;
    private static Vec3 prevCentroid;
    private static long prevCentroidNanos;
    private static float swingX, swingY, swingZ;

    private FireSparks() {
    }

    // ------------------------------------------------------------------------------------------------ spawning

    public static void spawn(double x, double y, double z, float vx, float vy, float vz) {
        if (SPARKS.size() < MAX_SPARKS_SOAKED) {
            SPARKS.add(new Spark(x, y, z, vx, vy, vz));
        }
    }

    /** A fountain going up from a point. */
    public static void burst(Vec3 pos, int count, float speed) {
        for (int i = 0; i < count; i++) {
            float theta = RAND.nextFloat() * (float) (Math.PI * 2.0);
            float phi = RAND.nextFloat() * (float) (Math.PI * 0.5);
            float spd = (0.7f + RAND.nextFloat() * 1.1f) * speed;
            spawn(pos.x, pos.y, pos.z, (float) (Math.cos(theta) * Math.cos(phi)) * spd, (float) Math.sin(phi) * spd + 0.9f,
                    (float) (Math.sin(theta) * Math.cos(phi)) * spd);
        }
    }

    /** Sparks fly mostly along the strike direction with a conical spread (a weapon hitting a mob). */
    public static void impactBurst(Vec3 pos, float dirX, float dirY, float dirZ, int count, float speed) {
        Vector3f dir = new Vector3f(dirX, dirY, dirZ);
        if (dir.lengthSquared() < 1.0E-6f) {
            return;
        }
        dir.normalize();
        Vector3f ref = Math.abs(dir.y) > 0.85f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f r = new Vector3f(dir).cross(ref).normalize();
        Vector3f f = new Vector3f(r).cross(dir);
        for (int i = 0; i < count; i++) {
            float theta = RAND.nextFloat() * (float) (Math.PI * 2.0);
            float spread = 0.35f + RAND.nextFloat() * 0.65f;
            float cosT = (float) Math.cos(theta) * spread;
            float sinT = (float) Math.sin(theta) * spread;
            float spd = (0.8f + RAND.nextFloat() * 1.2f) * speed;
            float forward = 0.9f + RAND.nextFloat() * 0.7f;
            spawn(pos.x, pos.y, pos.z, (dir.x * forward + r.x * cosT + f.x * sinT) * spd,
                    (dir.y * forward + r.y * cosT + f.y * sinT) * spd + 0.4f, (dir.z * forward + r.z * cosT + f.z * sinT) * spd);
        }
    }

    /**
     * Emits sparks from the burning pixels of the weapon being drawn. {@code localToEye} is the pose of the item model at the moment
     * of drawing; with the camera orientation it gives the world position, so every spark is born in the right place and then
     * lives on its own.
     */
    public static void emit(List<Voxel> voxels, boolean soaked, Matrix4f localToEye, boolean firstPerson) {
        if (voxels == null || voxels.isEmpty() || SPARKS.size() >= (soaked ? MAX_SPARKS_SOAKED : MAX_SPARKS)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized() || mc.isPaused()) {
            return;
        }
        long now = System.nanoTime();
        Vec3 cam = camera.getPosition();

        // swing velocity: the centroid of the burning part, tracked for the weapon in the own first person hand only (other
        // weapons drawn in the same frame would make the centroid jump between them)
        Vector3f sum = new Vector3f();
        int count = 0;
        for (Voxel v : firstPerson ? voxels : List.<Voxel>of()) {
            Vec3 w = toWorld(localToEye, camera, cam, (v.xMin + v.xMax) * 0.5f, (v.yMin + v.yMax) * 0.5f, (v.zMin + v.zMax) * 0.5f);
            sum.add((float) w.x, (float) w.y, (float) w.z);
            count++;
        }
        if (count > 0) {
            Vec3 centroid = new Vec3(sum.x / count, sum.y / count, sum.z / count);
            if (prevCentroid != null && prevCentroidNanos != 0L) {
                float dt = (now - prevCentroidNanos) / 1.0E9f;
                if (dt > 0.001f && dt < 0.5f) {
                    swingX = (float) (centroid.x - prevCentroid.x) / dt;
                    swingY = (float) (centroid.y - prevCentroid.y) / dt;
                    swingZ = (float) (centroid.z - prevCentroid.z) / dt;
                    float len = (float) Math.sqrt(swingX * swingX + swingY * swingY + swingZ * swingZ);
                    if (len > 12.0f) {
                        float k = 12.0f / len;
                        swingX *= k;
                        swingY *= k;
                        swingZ *= k;
                    }
                }
            }
            prevCentroid = centroid;
            prevCentroidNanos = now;
        }

        float dt = emitDt;
        float swayX = firstPerson ? swingX : 0.0f, swayY = firstPerson ? swingY : 0.0f, swayZ = firstPerson ? swingZ : 0.0f;
        float frontRate = 4.5f; // sparks per second per burning pixel
        float backRate = soaked ? 2.0f : 1.0f;
        for (Voxel v : voxels) {
            if (SPARKS.size() >= (soaked ? MAX_SPARKS_SOAKED : MAX_SPARKS)) {
                break;
            }
            if (RAND.nextFloat() >= (v.front ? frontRate : backRate) * dt) {
                continue;
            }
            Vec3 p = toWorld(localToEye, camera, cam, v.xMin + RAND.nextFloat() * (v.xMax - v.xMin),
                    v.yMin + RAND.nextFloat() * (v.yMax - v.yMin), v.zMin + RAND.nextFloat() * (v.zMax - v.zMin));
            // cone around world up, plus a share of the weapon's swing
            float theta = RAND.nextFloat() * (float) (Math.PI * 2.0);
            float out = 0.14f + RAND.nextFloat() * 0.32f;
            float up = 1.20f + RAND.nextFloat() * 1.65f;
            spawn(p.x, p.y, p.z, (float) Math.cos(theta) * out + swayX * 0.4f, up + swayY * 0.4f,
                    (float) Math.sin(theta) * out + swayZ * 0.4f);
        }
    }

    /** True while a screen is being drawn (slot items, player model in the inventory). */
    public static boolean inScreen() {
        return inScreen;
    }

    /**
     * Like {@link #emit} for items drawn inside a screen: the pose (with the GUI matrices) turns the burning pixels into screen
     * pixels, sparks then fly up the screen at a speed proportional to the size of the item on it.
     */
    public static void emitGui(List<Voxel> voxels, boolean soaked, Matrix4f pose) {
        if (voxels == null || voxels.isEmpty() || GUI_SPARKS.size() >= MAX_GUI_SPARKS) {
            return;
        }
        Matrix4f m = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose);
        Vector3f origin = m.transformPosition(0f, 0f, 0f, new Vector3f());
        float scale = origin.distance(m.transformPosition(0f, 1f, 0f, new Vector3f()));
        if (scale < 1.0E-3f) {
            return;
        }
        float k = scale * 0.7f;
        float frontRate = 4.5f;
        float backRate = soaked ? 2.0f : 1.0f;
        for (Voxel v : voxels) {
            if (GUI_SPARKS.size() >= MAX_GUI_SPARKS) {
                break;
            }
            if (RAND.nextFloat() >= (v.front ? frontRate : backRate) * guiDt) {
                continue;
            }
            Vector3f p = m.transformPosition(v.xMin + RAND.nextFloat() * (v.xMax - v.xMin), v.yMin + RAND.nextFloat() * (v.yMax - v.yMin),
                    v.zMin + RAND.nextFloat() * (v.zMax - v.zMin), new Vector3f());
            float side = (RAND.nextFloat() - 0.5f) * 2.0f * (0.14f + RAND.nextFloat() * 0.32f);
            float up = 1.20f + RAND.nextFloat() * 1.65f;
            Spark s = new Spark(p.x, p.y, 0.0, side * k, -up * k, 0.0f);
            s.grav = -0.85f * k;
            GUI_SPARKS.add(s);
        }
    }

    @SubscribeEvent
    public static void onScreenPre(ScreenEvent.Render.Pre event) {
        inScreen = true;
    }

    @SubscribeEvent
    public static void onScreenPost(ScreenEvent.Render.Post event) {
        inScreen = false;
        long now = System.nanoTime();
        float dt = lastGuiNanos == 0L ? 0.016f : Math.min(0.1f, (now - lastGuiNanos) / 1.0E9f);
        lastGuiNanos = now;
        guiDt = Math.max(0.001f, dt);
        for (Iterator<Spark> it = GUI_SPARKS.iterator(); it.hasNext(); ) {
            if (!it.next().update(dt)) {
                it.remove();
            }
        }
        if (GUI_SPARKS.isEmpty()) {
            return;
        }
        event.getGuiGraphics().flush();
        Matrix4f mat = event.getGuiGraphics().pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] col = new float[4];
        for (Spark s : GUI_SPARKS) {
            s.color(col);
            float a = col[3];
            if (a <= 0.01f) {
                continue;
            }
            float hx = (float) s.x, hy = (float) s.y;
            float tx = hx - s.vx * s.length * 0.085f, ty = hy - s.vy * s.length * 0.085f;
            float dx = hx - tx, dy = hy - ty;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1.0E-4f) {
                continue;
            }
            float w = 0.6f / (float) Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
            float sx = -dy / len * w, sy = dx / len * w;
            int ir = (int) (col[0] * 255f), ig = (int) (col[1] * 255f), ib = (int) (col[2] * 255f), ia = (int) (a * 255f);
            int tr = (int) (Math.max(0f, col[0] - 0.2f) * 255f), tg = (int) (Math.max(0f, col[1] - 0.3f) * 255f), tb = (int) (col[2] * 255f);
            int ta = (int) (a * 0.45f * 255f);
            buffer.vertex(mat, tx - sx, ty - sy, 0f).color(tr, tg, tb, ta).endVertex();
            buffer.vertex(mat, tx + sx, ty + sy, 0f).color(tr, tg, tb, ta).endVertex();
            buffer.vertex(mat, hx + sx, hy + sy, 0f).color(ir, ig, ib, ia).endVertex();
            buffer.vertex(mat, hx - sx, hy - sy, 0f).color(ir, ig, ib, ia).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** A point of the item model to world coordinates (eye space -> camera axes -> world). */
    private static Vec3 toWorld(Matrix4f localToEye, Camera camera, Vec3 cam, float lx, float ly, float lz) {
        Vector3f eye = localToEye.transformPosition(lx, ly, lz, new Vector3f());
        Vector3f v = new Vector3f(-eye.x, eye.y, -eye.z).rotate(camera.rotation());
        return new Vec3(cam.x + v.x, cam.y + v.y, cam.z + v.z);
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().level == null) {
            SPARKS.clear();
            GUI_SPARKS.clear();
            prevCentroid = null;
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.0f : Math.min(0.1f, (now - lastFrameNanos) / 1.0E9f);
        lastFrameNanos = now;
        if (Minecraft.getInstance().isPaused()) {
            dt = 0.0f;
        }
        if (dt > 0.0f) {
            emitDt = dt;
        }
        for (Iterator<Spark> it = SPARKS.iterator(); it.hasNext(); ) {
            if (!it.next().update(dt)) {
                it.remove();
            }
        }
        if (SPARKS.isEmpty()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f mat = event.getPoseStack().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float[] col = new float[4];
        for (Spark s : SPARKS) {
            s.color(col);
            float a = col[3];
            if (a <= 0.01f) {
                continue;
            }
            float hx = (float) (s.x - cam.x);
            float hy = (float) (s.y - cam.y);
            float hz = (float) (s.z - cam.z);
            float speed = (float) Math.sqrt(s.vx * s.vx + s.vy * s.vy + s.vz * s.vz);
            float tail = speed > 0.001f ? s.length * 0.085f : 0.01f;
            float tx = hx - s.vx * tail;
            float ty = hy - s.vy * tail;
            float tz = hz - s.vz * tail;
            // ribbon width: roughly constant on screen
            float dist = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
            float half = Math.max(0.004f, dist * 0.0035f);
            // side vector = direction x view
            float dx = hx - tx, dy = hy - ty, dz = hz - tz;
            float sx = dy * hz - dz * hy;
            float sy = dz * hx - dx * hz;
            float sz = dx * hy - dy * hx;
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1.0E-6f) {
                continue;
            }
            sx = sx / sl * half;
            sy = sy / sl * half;
            sz = sz / sl * half;
            int ir = (int) (col[0] * 255f), ig = (int) (col[1] * 255f), ib = (int) (col[2] * 255f), ia = (int) (a * 255f);
            int tr = (int) (Math.max(0f, col[0] - 0.2f) * 255f), tg = (int) (Math.max(0f, col[1] - 0.3f) * 255f), tb = (int) (col[2] * 255f);
            int ta = (int) (a * 0.45f * 255f);
            buffer.vertex(mat, tx - sx, ty - sy, tz - sz).color(tr, tg, tb, ta).endVertex();
            buffer.vertex(mat, tx + sx, ty + sy, tz + sz).color(tr, tg, tb, ta).endVertex();
            buffer.vertex(mat, hx + sx, hy + sy, hz + sz).color(ir, ig, ib, ia).endVertex();
            buffer.vertex(mat, hx - sx, hy - sy, hz - sz).color(ir, ig, ib, ia).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}
