package com.voltyx.efw.coth.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.voltyx.efw.EFW;
import com.voltyx.efw.coth.Coth;
import com.voltyx.efw.coth.CothConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * What the sick one sees at the last stage of the Call of the Hive: human silhouettes made of black (and a few white) squares that keep
 * twitching, the squares growing and shrinking. They come out of view, behind the back or far off, and only this player sees them
 * (nothing is sent anywhere). The moment one is seen it is gone, a glimpse; sometimes a faint sound stays there.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class CothPhantoms {
    private static final Random RANDOM = new Random();
    private static final float CELL = 0.1f;
    /** The figure, top row first: a head, hanging arms, legs. */
    private static final String[] SHAPE = {
            "...XXX...",
            "..XXXXX..",
            "..XXXXX..",
            "...XXX...",
            "....X....",
            ".XXXXXXX.",
            "XXXXXXXXX",
            "XX.XXX.XX",
            "XX.XXX.XX",
            "XX.XXX.XX",
            "X..XXX..X",
            "X..XXX..X",
            "X..XXX..X",
            "...XXX...",
            "...X.X...",
            "...X.X...",
            "...X.X...",
            "...X.X...",
            "...X.X...",
            "..XX.XX..",
    };
    private static final int COLS = SHAPE[0].length();
    private static final int ROWS = SHAPE.length;
    private static final int FADE_IN = 24;
    private static final int FADE_OUT = 18;
    /** Seen: gone almost at once, a glimpse. */
    private static final int FADE_SEEN = 3;

    private static final class Phantom {
        final double x, y, z;
        /** Stands behind the back (only shown with the first person camera). */
        final boolean behind;
        final float[] scale = new float[COLS * ROWS];
        final boolean[] white = new boolean[COLS * ROWS];
        final float[] stray = new float[24];
        int age;
        int fading = -1;
        int fadeTicks = FADE_OUT;
        int nextTwitch;
        float shiftX;
        int headShift;
        boolean blink;

        Phantom(double x, double y, double z, boolean behind) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.behind = behind;
            twitch();
        }

        /** A new jerk: squares change size and colour, the figure and its head move a little. */
        void twitch() {
            for (int i = 0; i < scale.length; i++) {
                scale[i] = 1.25f + RANDOM.nextFloat() * 1.4f; // always overlapping: one blot, no gaps
                white[i] = RANDOM.nextFloat() < 0.13f;
            }
            for (int i = 0; i < stray.length; i += 3) {
                stray[i] = (RANDOM.nextFloat() - 0.5f) * (COLS + 4) * CELL;
                stray[i + 1] = RANDOM.nextFloat() * ROWS * CELL;
                stray[i + 2] = RANDOM.nextFloat() < 0.5f ? 0.0f : 0.9f + RANDOM.nextFloat() * 1.3f;
            }
            shiftX = (RANDOM.nextFloat() - 0.5f) * 0.18f;
            headShift = RANDOM.nextFloat() < 0.4f ? RANDOM.nextInt(3) - 1 : headShift;
            blink = RANDOM.nextFloat() < 0.06f;
            boolean fast = CothConfig.PHANTOM_FLICKER.get();
            nextTwitch = fast ? 2 + RANDOM.nextInt(7) : 18 + RANDOM.nextInt(24);
        }

        float alpha(float partial) {
            float a = Math.min(1.0f, (age + partial) / FADE_IN);
            if (fading >= 0) {
                a *= Math.max(0.0f, 1.0f - (fading + partial) / fadeTicks);
            }
            return a;
        }
    }

    private static final List<Phantom> PHANTOMS = new ArrayList<>();
    private static int spawnCheck;

    private CothPhantoms() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            PHANTOMS.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        boolean sick = CothConfig.ENABLED.get() && CothConfig.PHANTOMS.get() && player.isAlive()
                && Coth.stage(player) >= CothConfig.PHANTOM_STAGE.get();
        Vec3 eye = player.getEyePosition();
        double vanish = CothConfig.PHANTOM_VANISH.get();
        for (Iterator<Phantom> it = PHANTOMS.iterator(); it.hasNext(); ) {
            Phantom p = it.next();
            p.age++;
            Vec3 chest = new Vec3(p.x, p.y + 1.2, p.z);
            double dist = chest.distanceTo(eye);
            if (p.fading >= 0) {
                if (++p.fading >= p.fadeTicks || dist > 64) {
                    it.remove();
                }
                continue;
            }
            if (--p.nextTwitch <= 0) {
                p.twitch();
            }
            // the moment it is seen it is gone: only a glimpse, turning round
            if (seen(mc, level, player, chest, 0) || (p.behind && !firstPerson(mc))) {
                p.fadeTicks = FADE_SEEN;
                vanish(level, p);
            } else if (!sick || dist < vanish || p.age > 20 * 60 || dist > 64) {
                vanish(level, p);
            }
        }
        if (sick && --spawnCheck <= 0) {
            spawnCheck = 20;
            if (PHANTOMS.size() < CothConfig.PHANTOM_MAX.get() && RANDOM.nextFloat() < 1.0f / Math.max(1, CothConfig.PHANTOM_INTERVAL.get())) {
                trySpawn(level, player);
            }
        }
    }

    private static void vanish(ClientLevel level, Phantom p) {
        p.fading = 0;
        if (CothConfig.PHANTOM_SOUNDS.get() && RANDOM.nextFloat() < 0.5f) {
            level.playLocalSound(p.x, p.y + 1.2, p.z, SoundEvents.AMBIENT_CAVE.get(), SoundSource.AMBIENT, 0.45f,
                    0.55f + RANDOM.nextFloat() * 0.3f, false);
        }
    }

    /**
     * Either right behind the back (first person only: any other camera would show it) or far off; out of view (it would be gone at
     * once), on the ground, with nothing between it and the player's eyes.
     */
    private static void trySpawn(ClientLevel level, LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        double vanish = CothConfig.PHANTOM_VANISH.get();
        boolean behind = firstPerson(mc) && RANDOM.nextBoolean();
        float yaw = player.getYRot();
        for (int attempt = 0; attempt < 10; attempt++) {
            float angle;
            double dist;
            if (behind) {
                angle = (float) Math.toRadians(yaw + 180 + (RANDOM.nextFloat() - 0.5f) * 80);
                dist = vanish + 2 + RANDOM.nextDouble() * 6;
            } else {
                angle = (float) Math.toRadians(yaw + (RANDOM.nextFloat() - 0.5f) * 220);
                dist = Math.max(vanish + 18, 28) + RANDOM.nextDouble() * 16;
            }
            double x = player.getX() - Math.sin(angle) * dist;
            double z = player.getZ() + Math.cos(angle) * dist;
            BlockPos ground = ground(level, BlockPos.containing(x, player.getY(), z));
            if (ground == null) {
                continue;
            }
            Vec3 chest = new Vec3(x, ground.getY() + 1.2, z);
            if (!visible(level, player, chest) || inView(mc, chest, 15)) {
                continue;
            }
            PHANTOMS.add(new Phantom(x, ground.getY(), z, behind));
            return;
        }
    }

    private static boolean firstPerson(Minecraft mc) {
        return mc.options.getCameraType().isFirstPerson();
    }

    /** In the picture and not hidden behind blocks, from the camera (whatever view it is in). */
    private static boolean seen(Minecraft mc, ClientLevel level, LocalPlayer player, Vec3 target, double margin) {
        if (!inView(mc, target, margin)) {
            return false;
        }
        Vec3 from = mc.gameRenderer.getMainCamera().getPosition();
        return level.clip(new ClipContext(from, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    /** Within the field of view of the camera (the horizontal half angle, plus a margin in degrees). */
    private static boolean inView(Minecraft mc, Vec3 target, double margin) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 to = target.subtract(camera.getPosition()).normalize();
        Vec3 look = new Vec3(camera.getLookVector());
        double vertical = Math.toRadians(mc.options.fov().get());
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        double halfHorizontal = Math.atan(Math.tan(vertical / 2) * aspect);
        return look.dot(to) > Math.cos(Math.min(Math.PI, halfHorizontal + Math.toRadians(margin)));
    }

    /** The feet position: air for two blocks above something solid, near the player's height. */
    private static BlockPos ground(ClientLevel level, BlockPos start) {
        BlockPos.MutableBlockPos pos = start.above(6).mutable();
        for (int i = 0; i < 16; i++, pos.move(0, -1, 0)) {
            if (!level.isLoaded(pos)) {
                return null;
            }
            BlockPos below = pos.below();
            if (free(level, pos) && free(level, pos.above()) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static boolean free(ClientLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
    }

    private static boolean visible(ClientLevel level, LocalPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        return level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || PHANTOMS.isEmpty()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f mat = event.getPoseStack().last().pose();
        float partial = event.getPartialTick();
        // the colour shader has no fog: they come out of the fog by hand
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Phantom p : PHANTOMS) {
            if (p.blink) {
                continue;
            }
            float alpha = p.alpha(partial);
            double dx = p.x - cam.x, dz = p.z - cam.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (fogEnd > fogStart && fogEnd < 1000) {
                alpha *= Math.max(0.0f, Math.min(1.0f, (fogEnd - (float) dist) / Math.max(1.0f, (fogEnd - fogStart) * 0.6f)));
            }
            if (alpha <= 0.01f || dist < 0.01) {
                continue;
            }
            // turned to the camera around the vertical axis
            float rx = (float) (-dz / dist), rz = (float) (dx / dist);
            float bx = (float) (p.x - cam.x) + rx * p.shiftX, by = (float) (p.y - cam.y), bz = (float) (p.z - cam.z) + rz * p.shiftX;
            for (int row = 0; row < ROWS; row++) {
                String line = SHAPE[row];
                int shift = row < 4 ? p.headShift : 0;
                for (int col = 0; col < COLS; col++) {
                    if (line.charAt(col) != 'X') {
                        continue;
                    }
                    int i = row * COLS + col;
                    float s = p.scale[i];
                    if (s <= 0) {
                        continue;
                    }
                    float u = (col + shift - (COLS - 1) * 0.5f) * CELL;
                    float v = (ROWS - 1 - row) * CELL + CELL * 0.5f;
                    float c = p.white[i] ? 0.9f : 0.02f;
                    square(buf, mat, bx, by, bz, rx, rz, u, v, CELL * s * 0.5f, c, alpha * (p.white[i] ? 0.85f : 0.95f));
                }
            }
            for (int k = 0; k < p.stray.length; k += 3) {
                if (p.stray[k + 2] > 0) {
                    square(buf, mat, bx, by, bz, rx, rz, p.stray[k], p.stray[k + 1], CELL * p.stray[k + 2] * 0.5f, 0.02f, alpha * 0.9f);
                }
            }
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** A square of half size {@code h} at (u, v) on the plane of the figure (u along the right vector, v up). */
    private static void square(BufferBuilder buf, Matrix4f mat, float bx, float by, float bz, float rx, float rz, float u, float v,
            float h, float c, float a) {
        float cx = bx + rx * u, cy = by + v, cz = bz + rz * u;
        float hx = rx * h, hz = rz * h;
        buf.vertex(mat, cx - hx, cy - h, cz - hz).color(c, c, c, a).endVertex();
        buf.vertex(mat, cx + hx, cy - h, cz + hz).color(c, c, c, a).endVertex();
        buf.vertex(mat, cx + hx, cy + h, cz + hz).color(c, c, c, a).endVertex();
        buf.vertex(mat, cx - hx, cy + h, cz - hz).color(c, c, c, a).endVertex();
    }
}
