package com.voltyx.efw.tech.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.voltyx.efw.EFW;
import com.voltyx.efw.mixin.GameRendererAccessor;
import com.voltyx.efw.mixin.MinecraftAccessor;
import com.voltyx.efw.mixin.OptionsAccessor;
import com.voltyx.efw.mixin.WindowAccessor;
import com.voltyx.efw.tech.BodycamEntry;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the world from the watched camera into a small render target shown on the terminal monitor (port of
 * BodycamFeedRenderer): the camera sits on the carrier's chest, lower when sneaking or crawling, with a wide 85 degree lens.
 * With a shader pack the picture is drawn through Iris's vanilla pipeline (see {@link IrisSwap}); Fabulous graphics keeps its own render
 * targets, so there the feed is skipped.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class BodycamFeed {
    public static final ResourceLocation TEXTURE = new ResourceLocation(EFW.MOD_ID, "dynamic/bodycam_feed");
    public static final float FOV = 85.0F;
    private static final int FBO_W = 320;
    private static final int FBO_H = 200;

    public record TacticalRect(float left, float right, float top, float bottom) {
    }

    private static TextureTarget target;
    private static boolean rendering;
    private static boolean failed;
    private static Entity currentCarrier;
    private static CameraFocus.Pose pose;
    private static float roll;
    private static final List<TacticalRect> detectedRects = new ArrayList<>();

    private BodycamFeed() {
    }

    public static boolean isRendering() {
        return rendering;
    }

    public static Entity getCurrentCarrier() {
        return currentCarrier;
    }

    static CameraFocus.Pose cameraPose() {
        return pose;
    }

    static float cameraRoll() {
        return roll;
    }

    public static List<TacticalRect> getDetectedRects() {
        return detectedRects;
    }

    public static boolean hasValidTexture() {
        return target != null && !failed;
    }

    /** The render target as a texture of the texture manager. */
    private static final class FeedTexture extends AbstractTexture {
        @Override
        public void load(ResourceManager manager) {
        }

        @Override
        public int getId() {
            return target != null ? target.getColorTextureId() : -1;
        }

        @Override
        public void releaseId() {
        }
    }

    private static boolean shaderPackInUse() {
        for (String cls : new String[] {"net.irisshaders.iris.api.v0.IrisApi", "net.coderbot.iris.api.v0.IrisApi"}) {
            try {
                Object api = Class.forName(cls).getMethod("getInstance").invoke(null);
                return (Boolean) api.getClass().getMethod("isShaderPackInUse").invoke(api);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }
        return false;
    }

    /** Lying / crawling near the ground, sneaking lower, else the collarbone mount (see {@link BodycamPose}). */
    static BodycamPose.Stance stanceOf(Entity carrier) {
        if (carrier.getPose() == Pose.SWIMMING || carrier.getEyeHeight() < 0.8F || carrier.getBbHeight() < 1.0F) {
            return BodycamPose.Stance.CRAWL;
        }
        return carrier.isCrouching() ? BodycamPose.Stance.CROUCH : BodycamPose.Stance.STAND;
    }

    public static int feedWidth() {
        return FBO_W;
    }

    public static int feedHeight() {
        return FBO_H;
    }

    private static float interpolateRotation(float prev, float cur, float partial) {
        return prev + partial * Mth.wrapDegrees(cur - prev);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START || rendering) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameMode == null || mc.isPaused()) {
            return;
        }
        Entity carrier;
        BodycamPose.Stance forced = null;
        if (mc.screen instanceof BodycamDebugScreen debug) {
            // tuning the mount: the feed of the chosen carrier, without a terminal
            carrier = debug.carrier();
            forced = debug.forcedStance();
        } else {
            TerminalSession session = TerminalSession.get();
            BodycamEntry cam = session.getStage() == TerminalSession.Stage.BODYCAM_VIEW ? session.getCurrentViewingCamera() : null;
            if (cam == null || !cam.isOnline) {
                detectedRects.clear();
                return;
            }
            // far from the terminal: keep the last picture
            if (session.getTerminalPos() != null && mc.player.distanceToSqr(session.getTerminalPos().getCenter()) > 48.0 * 48.0) {
                return;
            }
            carrier = mc.level.getEntity(cam.carrierEntityId);
            if (carrier == null) {
                for (Player p : mc.level.players()) {
                    if (p.getName().getString().equals(cam.carrierName)) {
                        carrier = p;
                        break;
                    }
                }
            }
        }
        if (carrier == null) {
            detectedRects.clear();
            return;
        }
        if (Minecraft.useShaderTransparency()) {
            failed = true;
            detectedRects.clear();
            return;
        }
        boolean swapped = false;
        if (shaderPackInUse()) {
            swapped = IrisSwap.enter();
            if (!swapped) {
                failed = true;
                detectedRects.clear();
                return;
            }
        }

        float pt = e.renderTickTime;
        double posX = Mth.lerp(pt, carrier.xo, carrier.getX());
        double posY = Mth.lerp(pt, carrier.yo, carrier.getY());
        double posZ = Mth.lerp(pt, carrier.zo, carrier.getZ());

        BodycamPose.Mount mount = BodycamPose.get(forced != null ? forced : stanceOf(carrier));
        double heightOffset = mount.height;
        double forwardOffset = mount.forward;
        double leftOffset = mount.left;
        float camPitch = mount.pitch;
        float torsoYaw = carrier instanceof LivingEntity living
                ? interpolateRotation(living.yBodyRotO, living.yBodyRot, pt)
                : interpolateRotation(carrier.yRotO, carrier.getYRot(), pt);
        float camYaw = torsoYaw + mount.yaw;
        double rad = Math.toRadians(torsoYaw);
        double forwardX = -Math.sin(rad);
        double forwardZ = Math.cos(rad);
        double leftX = Math.cos(rad);
        double leftZ = Math.sin(rad);
        double camX = posX + forwardX * forwardOffset + leftX * leftOffset;
        double camY = posY + heightOffset;
        double camZ = posZ + forwardZ * forwardOffset + leftZ * leftOffset;
        pose = new CameraFocus.Pose(new Vec3(camX, camY, camZ), camYaw, camPitch);
        roll = mount.roll;

        try {
            render(mc, carrier, pt);
        } finally {
            if (swapped) {
                IrisSwap.exit();
            }
        }
        updateTacticalRectangles(mc, carrier, camX, camY, camZ, camYaw, camPitch, pt);
    }

    private static void render(Minecraft mc, Entity carrier, float partialTick) {
        if (target == null) {
            target = new TextureTarget(FBO_W, FBO_H, true, Minecraft.ON_OSX);
            target.setClearColor(0.6F, 0.7F, 0.8F, 1.0F);
            target.setFilterMode(GL11.GL_LINEAR);
            mc.getTextureManager().register(TEXTURE, new FeedTexture());
        }
        RenderTarget main = mc.getMainRenderTarget();
        Window window = mc.getWindow();
        int width = window.getWidth();
        int height = window.getHeight();
        CameraType cameraType = mc.options.getCameraType();
        boolean hideGui = mc.options.hideGui;
        GameRendererAccessor gameRenderer = (GameRendererAccessor) mc.gameRenderer;
        boolean renderHand = gameRenderer.efw$getRenderHand();
        var hitResult = mc.hitResult;
        Entity pickEntity = mc.crosshairPickEntity;
        try {
            rendering = true;
            failed = false;
            currentCarrier = carrier;
            ((MinecraftAccessor) mc).efw$setMainRenderTarget(target);
            ((WindowAccessor) (Object) window).efw$setFramebufferWidth(FBO_W);
            ((WindowAccessor) (Object) window).efw$setFramebufferHeight(FBO_H);
            // a detached camera so that every player, the local one included, is drawn; the pose comes from CameraFocus
            ((OptionsAccessor) mc.options).efw$setCameraTypeRaw(CameraType.THIRD_PERSON_FRONT);
            mc.options.hideGui = true;
            gameRenderer.efw$setRenderHand(false);
            target.bindWrite(true);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            mc.gameRenderer.renderLevel(partialTick, Util.getNanos() + 1_000_000_000L / 60, new PoseStack());
        } catch (Throwable t) {
            failed = true;
            EFW.LOGGER.error("[Bodycam] feed render failed", t);
        } finally {
            rendering = false;
            currentCarrier = null;
            ((MinecraftAccessor) mc).efw$setMainRenderTarget(main);
            ((WindowAccessor) (Object) window).efw$setFramebufferWidth(width);
            ((WindowAccessor) (Object) window).efw$setFramebufferHeight(height);
            ((OptionsAccessor) mc.options).efw$setCameraTypeRaw(cameraType);
            mc.options.hideGui = hideGui;
            gameRenderer.efw$setRenderHand(renderHand);
            mc.hitResult = hitResult;
            mc.crosshairPickEntity = pickEntity;
            main.bindWrite(true);
        }
    }

    /** Boxes around living things seen by the camera, in 0..1 screen space of the feed. */
    private static void updateTacticalRectangles(Minecraft mc, Entity carrier, double camX, double camY, double camZ,
                                                 float camYaw, float camPitch, float pt) {
        detectedRects.clear();
        double tanHalfFov = Math.tan(Math.toRadians(BodycamPose.fov() / 2.0));
        double aspect = (double) FBO_W / FBO_H;
        double radYaw = Math.toRadians(camYaw);
        double cosYaw = Math.cos(radYaw);
        double sinYaw = Math.sin(radYaw);
        double radP = Math.toRadians(camPitch);
        double cosP = Math.cos(radP);
        double sinP = Math.sin(radP);
        for (Entity ent : mc.level.entitiesForRendering()) {
            if (!(ent instanceof LivingEntity) || !ent.isAlive() || ent == carrier) {
                continue;
            }
            double distSq = ent.distanceToSqr(camX, camY, camZ);
            if (distSq < 0.5 || distSq > 28.0 * 28.0) {
                continue;
            }
            double ex = Mth.lerp(pt, ent.xo, ent.getX());
            double ey = Mth.lerp(pt, ent.yo, ent.getY());
            double ez = Mth.lerp(pt, ent.zo, ent.getZ());
            double hw = ent.getBbWidth() * 0.5;
            double eh = ent.getBbHeight();
            double[] cxs = {ex - hw, ex + hw, ex - hw, ex + hw, ex - hw, ex + hw, ex - hw, ex + hw};
            double[] cys = {ey, ey, ey + eh, ey + eh, ey, ey, ey + eh, ey + eh};
            double[] czs = {ez - hw, ez - hw, ez - hw, ez - hw, ez + hw, ez + hw, ez + hw, ez + hw};
            float minU = 2.0f, maxU = -1.0f, minV = 2.0f, maxV = -1.0f;
            int visible = 0;
            for (int i = 0; i < 8; i++) {
                double dx = cxs[i] - camX;
                double dy = cys[i] - camY;
                double dz = czs[i] - camZ;
                double x1 = -dx * cosYaw - dz * sinYaw;
                double z1 = dx * sinYaw - dz * cosYaw;
                double y2 = dy * cosP - z1 * sinP;
                double z2 = dy * sinP + z1 * cosP;
                double depth = -z2;
                if (depth <= 0.4) {
                    continue;
                }
                float u = (float) (0.5 + 0.5 * (x1 / depth) / (tanHalfFov * aspect));
                float v = (float) (0.5 - 0.5 * (y2 / depth) / tanHalfFov);
                minU = Math.min(minU, u);
                maxU = Math.max(maxU, u);
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
                visible++;
            }
            if (visible >= 2 && maxU > 0.02f && minU < 0.98f && maxV > 0.02f && minV < 0.98f) {
                float left = Math.max(0.01f, minU - 0.015f);
                float right = Math.min(0.99f, maxU + 0.015f);
                float top = Math.max(0.01f, minV - 0.015f);
                float bottom = Math.min(0.99f, maxV + 0.015f);
                if (right - left > 0.02f && bottom - top > 0.02f) {
                    detectedRects.add(new TacticalRect(left, right, top, bottom));
                }
            }
        }
    }
}
