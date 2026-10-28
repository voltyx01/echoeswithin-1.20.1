package com.voltyx.efw.tech.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.tech.AntennaBlockEntity;
import com.voltyx.efw.tech.TechNetwork;
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
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Standing at the safe's keypad (port of AntennaCameraController): the camera glides to the keypad, and after the right
 * code it backs off to show the opening door.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class AntennaCamera {
    private static final double INPUT_DIST = 0.55;
    private static final double SUCCESS_DIST = 1.05;

    private static boolean active;
    private static BlockPos antennaPos;
    private static AntennaBlockEntity currentAntenna;
    private static float transitionProgress;
    private static float successProgress;
    private static long lastFrameTime = System.nanoTime();
    private static float targetYaw;
    private static double keypadX;
    private static double keypadY;
    private static double keypadZ;
    private static double blockX;
    private static double blockY;
    private static double blockZ;
    private static double normalX;
    private static double normalZ;
    private static double targetPlayerX;
    private static double targetPlayerZ;
    private static CameraType prevCameraType = CameraType.FIRST_PERSON;

    private AntennaCamera() {
    }

    public static boolean isActive() {
        return active;
    }

    public static BlockPos getAntennaPos() {
        return antennaPos;
    }

    public static AntennaBlockEntity getCurrentAntenna() {
        return currentAntenna;
    }

    public static void open(BlockPos pos, Direction facing, AntennaBlockEntity antenna) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        antennaPos = pos;
        currentAntenna = antenna;
        active = true;
        transitionProgress = 0.0f;
        successProgress = 0.0f;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY();
        double cz = pos.getZ() + 0.5;
        // keypad centre between buttons 5 and 2: model (-5.0, 8.6, -8.6) / 16, turned like the renderer
        keypadY = cy + 0.5375;
        switch (facing) {
            case SOUTH -> {
                keypadX = cx - 0.3125;
                keypadZ = cz + 0.5375;
                normalX = 0.0;
                normalZ = 1.0;
                targetYaw = 180.0F;
            }
            case WEST -> {
                keypadX = cx - 0.5375;
                keypadZ = cz - 0.3125;
                normalX = -1.0;
                normalZ = 0.0;
                targetYaw = 270.0F;
            }
            case EAST -> {
                keypadX = cx + 0.5375;
                keypadZ = cz + 0.3125;
                normalX = 1.0;
                normalZ = 0.0;
                targetYaw = 90.0F;
            }
            default -> {
                keypadX = cx + 0.3125;
                keypadZ = cz - 0.5375;
                normalX = 0.0;
                normalZ = -1.0;
                targetYaw = 0.0F;
            }
        }
        blockX = cx + normalX * 0.5;
        blockY = cy + 0.5;
        blockZ = cz + normalZ * 0.5;
        targetPlayerX = keypadX + normalX * INPUT_DIST;
        targetPlayerZ = keypadZ + normalZ * INPUT_DIST;
        prevCameraType = mc.options.getCameraType();
        com.voltyx.efw.compat.ShoulderCompat.runImmediate(() -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        lastFrameTime = System.nanoTime();
        AntennaSession.get().reset();
    }

    public static void close() {
        if (!active) {
            return;
        }
        active = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AntennaScreen) {
            mc.setScreen(null);
        }
        mc.options.setCameraType(prevCameraType);
        if (antennaPos != null) {
            TechNetwork.sendToServer(new TechNetwork.CloseAntenna(antennaPos));
        }
        if (currentAntenna != null) {
            currentAntenna.close();
            currentAntenna = null;
        }
        antennaPos = null;
    }

    private static float easeOutCubic(float t) {
        float c = Mth.clamp(t, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - c, 3);
    }

    static CameraFocus.Pose cameraPose(Vec3 eye, float yaw, float pitch) {
        if (transitionProgress <= 0.001f) {
            return null;
        }
        float t = easeOutCubic(transitionProgress);
        float newYaw = yaw + Mth.wrapDegrees(targetYaw - yaw) * t;
        float newPitch = pitch + (0.0f - pitch) * t;
        float s = easeOutCubic(successProgress);
        double tx = keypadX + (blockX - keypadX) * s;
        double ty = keypadY + (blockY - keypadY) * s;
        double tz = keypadZ + (blockZ - keypadZ) * s;
        double dist = INPUT_DIST + (SUCCESS_DIST - INPUT_DIST) * s;
        Vec3 desired = new Vec3(tx + normalX * dist, ty, tz + normalZ * dist);
        Vec3 pos = eye.add(desired.subtract(eye).scale(t));
        if (active && transitionProgress >= 1.0f) {
            newYaw = targetYaw;
            newPitch = 0.0f;
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                player.setYRot(targetYaw);
                player.yRotO = targetYaw;
                player.setXRot(0.0f);
                player.xRotO = 0.0f;
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
            if ("ACCESS".equals(AntennaSession.get().getStatusMessage())) {
                successProgress = Math.min(1.0f, successProgress + dt * 1.5f);
            }
        } else if (transitionProgress > 0.0f) {
            transitionProgress = Math.max(0.0f, transitionProgress - dt * 3.0f);
            if (transitionProgress <= 0.0f) {
                successProgress = 0.0f;
            }
        }
        AntennaSession.get().update(dt, active);
        Minecraft mc = Minecraft.getInstance();
        if (active && transitionProgress >= 1.0f && mc.screen == null) {
            mc.setScreen(new AntennaScreen());
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
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
            close();
            return;
        }
        if (antennaPos != null && player.distanceToSqr(antennaPos.getCenter()) > 9.0) {
            close();
            return;
        }
        double destX = targetPlayerX;
        double destZ = targetPlayerZ;
        if (successProgress > 0.0f) {
            float s = easeOutCubic(successProgress);
            destX = targetPlayerX + (blockX + normalX * SUCCESS_DIST - targetPlayerX) * s;
            destZ = targetPlayerZ + (blockZ + normalZ * SUCCESS_DIST - targetPlayerZ) * s;
        }
        Vec3 motion = player.getDeltaMovement();
        if (transitionProgress < 0.99f || successProgress > 0.0f) {
            player.setDeltaMovement((destX - player.getX()) * 0.25, motion.y, (destZ - player.getZ()) * 0.25);
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

    /** Bottom hint "[ESC / ПКМ] Отойти от сейфа". */
    static void renderPrompt(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        String hint = "[ESC / ПКМ] Отойти от сейфа";
        int w = mc.font.width(hint);
        int x = (g.guiWidth() - w) / 2;
        int y = g.guiHeight() - 25;
        g.fill(x - 8, y - 4, x + w + 8, y + mc.font.lineHeight + 4, 0xCC071520);
        g.drawString(mc.font, hint, x, y, 0xFF44AAFF, true);
    }
}
