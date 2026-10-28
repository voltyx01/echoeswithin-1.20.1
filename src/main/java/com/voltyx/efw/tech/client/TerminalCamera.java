package com.voltyx.efw.tech.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.tech.TechNetwork;
import com.voltyx.efw.tech.TerminalBlockEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Sitting at a wall terminal (port of TerminalCameraController): the player steps in front of the monitor, the camera
 * glides to the screen and the console takes the keyboard. ESC or right click steps away; a bodycam stream keeps running
 * on the monitor after leaving.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class TerminalCamera {
    private static final double CAM_DIST = 0.55;
    private static final double STAND_DIST = 0.70;

    private static boolean active;
    private static BlockPos terminalPos;
    private static TerminalBlockEntity currentTerminal;
    private static float transitionProgress;
    private static long lastFrameTime = System.nanoTime();
    private static float targetYaw;
    private static float targetPitch;
    private static double normalX;
    private static double normalZ;
    private static double targetCenterX;
    private static double targetCenterY;
    private static double targetCenterZ;
    private static double targetPlayerX;
    private static double targetPlayerZ;
    private static CameraType prevCameraType = CameraType.FIRST_PERSON;

    private TerminalCamera() {
    }

    public static boolean isActive() {
        return active;
    }

    public static BlockPos getTerminalPos() {
        return terminalPos;
    }

    public static float getTransitionProgress() {
        return transitionProgress;
    }

    public static void open(BlockPos pos, Direction facing, TerminalBlockEntity terminal) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        terminalPos = pos;
        currentTerminal = terminal;
        active = true;
        transitionProgress = 0.0f;
        normalX = facing.getStepX();
        normalZ = facing.getStepZ();
        targetYaw = facing.getOpposite().toYRot();
        // front surface of the display: centre - normal * 7/16, vertical centre of the screen at 9.5/16
        double screenSurfaceOffset = 7.0 / 16.0;
        targetCenterX = pos.getX() + 0.5 - normalX * screenSurfaceOffset;
        targetCenterY = pos.getY() + 9.5 / 16.0;
        targetCenterZ = pos.getZ() + 0.5 - normalZ * screenSurfaceOffset;
        targetPitch = 0.0f;
        targetPlayerX = targetCenterX + normalX * STAND_DIST;
        targetPlayerZ = targetCenterZ + normalZ * STAND_DIST;
        prevCameraType = mc.options.getCameraType();
        com.voltyx.efw.compat.ShoulderCompat.runImmediate(() -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        lastFrameTime = System.nanoTime();

        TerminalSession session = TerminalSession.get();
        boolean resumingBodycam = session.getStage() == TerminalSession.Stage.BODYCAM_VIEW && pos.equals(session.getTerminalPos());
        if (!resumingBodycam) {
            session.begin(pos, terminal);
        }
    }

    public static void close() {
        if (!active) {
            return;
        }
        active = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TerminalScreen) {
            mc.setScreen(null);
        }
        mc.options.setCameraType(prevCameraType);
        boolean streaming = TerminalSession.get().getStage() == TerminalSession.Stage.BODYCAM_VIEW;
        if (!streaming) {
            if (terminalPos != null) {
                TechNetwork.sendToServer(new TechNetwork.CloseTerminal(terminalPos));
            }
            if (currentTerminal != null) {
                currentTerminal.close();
            }
        } else if (currentTerminal != null) {
            // keep the casing open so the feed stays on the wall monitor
            currentTerminal.setOpen(true);
        }
        currentTerminal = null;
        terminalPos = null;
    }

    private static float easeOutCubic(float t) {
        float c = Mth.clamp(t, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - c, 3);
    }

    /** Camera pose while sitting at (or leaving) the terminal, or null. Called from the Camera.setup mixin. */
    static CameraFocus.Pose cameraPose(Vec3 eye, float yaw, float pitch) {
        if (transitionProgress <= 0.001f || BodycamFeed.isRendering()) {
            return null;
        }
        float t = easeOutCubic(transitionProgress);
        float newYaw = yaw + Mth.wrapDegrees(targetYaw - yaw) * t;
        float newPitch = pitch + (targetPitch - pitch) * t;
        Vec3 desired = new Vec3(targetCenterX + normalX * CAM_DIST, targetCenterY, targetCenterZ + normalZ * CAM_DIST);
        Vec3 pos = eye.add(desired.subtract(eye).scale(t));
        if (active && transitionProgress >= 1.0f) {
            newYaw = targetYaw;
            newPitch = targetPitch;
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                player.setYRot(targetYaw);
                player.yRotO = targetYaw;
                player.setXRot(targetPitch);
                player.xRotO = targetPitch;
            }
        }
        return new CameraFocus.Pose(pos, newYaw, newPitch);
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) {
            return;
        }
        long now = System.nanoTime();
        float dt = Mth.clamp((now - lastFrameTime) / 1_000_000_000.0f, 0.001f, 0.1f);
        lastFrameTime = now;
        if (active) {
            transitionProgress = Math.min(1.0f, transitionProgress + dt * 2.5f);
        } else {
            transitionProgress = Math.max(0.0f, transitionProgress - dt * 3.0f);
        }
        TerminalSession.get().update(dt, active);
        Minecraft mc = Minecraft.getInstance();
        if (active && transitionProgress >= 1.0f && mc.screen == null) {
            mc.setScreen(new TerminalScreen());
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !player.isAlive()) {
            close();
            return;
        }
        // ESC during the glide opened the pause menu: leave the terminal instead
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
            close();
            return;
        }
        if (terminalPos != null && player.distanceToSqr(terminalPos.getCenter()) > 9.0) {
            close();
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (transitionProgress < 0.99f) {
            player.setDeltaMovement((targetPlayerX - player.getX()) * 0.25, motion.y, (targetPlayerZ - player.getZ()) * 0.25);
        } else {
            player.setDeltaMovement(0, Math.min(motion.y, 0), 0);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInput(MovementInputUpdateEvent e) {
        if (active && transitionProgress >= 0.5f) {
            CameraFocus.freeze(e.getInput());
        }
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre e) {
        if (active && transitionProgress >= 0.5f && e.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && e.getAction() == GLFW.GLFW_PRESS) {
            close();
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderHand(RenderHandEvent e) {
        if (transitionProgress > 0.001f) {
            if (active && transitionProgress >= 1.0f) {
                e.setCanceled(true);
            } else {
                e.getPoseStack().translate(0.0F, -easeOutCubic(transitionProgress) * 1.5F, 0.0F);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        if (active && transitionProgress > 0.3f) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post e) {
        if (active && transitionProgress >= 0.8f && Minecraft.getInstance().screen == null) {
            renderPrompt(e.getGuiGraphics());
        }
    }

    /** Bottom hint "[ESC / ПКМ] Отойти от терминала". */
    static void renderPrompt(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        String hint = "[ESC / ПКМ] Отойти от терминала";
        int w = mc.font.width(hint);
        int x = (g.guiWidth() - w) / 2;
        int y = g.guiHeight() - 25;
        g.fill(x - 8, y - 4, x + w + 8, y + mc.font.lineHeight + 4, 0xCC07150A);
        g.drawString(mc.font, hint, x, y, 0xFF44FFAA, true);
    }
}
