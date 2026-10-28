package com.voltyx.efw.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.ShoulderCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Lock-on targeting (port of the 1.12 LockOnHandler): the key picks the mob under the camera ray or the best hostile in front,
 * the player (and the Shoulder Surfing camera when it is active) then turns smoothly to it every frame.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class LockOnClient {
    public static final KeyMapping LOCK_ON = new KeyMapping("key.efw.lock_on", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, "key.categories.efw");
    public static final KeyMapping SWITCH = new KeyMapping("key.efw.lock_on_switch", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_TAB, "key.categories.efw");

    private static LivingEntity target;
    private static boolean lockedOn;
    private static int wallTimer;
    private static float targetYaw;
    private static float targetPitch;
    private static long lastRollTime;

    private LockOnClient() {
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent e) {
            e.register(LOCK_ON);
            e.register(SWITCH);
        }
    }

    public static boolean isLockedOn() {
        return lockedOn && target != null;
    }

    public static LivingEntity target() {
        return target;
    }

    public static void leave() {
        target = null;
        lockedOn = false;
        lookInit = false;
    }

    /** The lock-on taken off for a moment (something else turns the view), to be put back by {@link #resume}. */
    private static LivingEntity suspended;

    public static void suspend() {
        if (lockedOn && target != null) {
            suspended = target;
            leave();
        }
    }

    /** The lock-on taken off by {@link #suspend} is back, on the same creature if it is still there. */
    public static void resume() {
        LivingEntity was = suspended;
        suspended = null;
        if (was != null && was.isAlive() && !was.isRemoved() && !lockedOn) {
            target = was;
            lockedOn = true;
            wallTimer = 0;
        }
    }

    @SubscribeEvent
    public static void onCrosshair(RenderGuiOverlayEvent.Pre e) {
        if (lockedOn && e.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type()) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !player.isAlive() || mc.level == null) {
            if (lockedOn) {
                leave();
            }
            return;
        }
        if (event.phase != TickEvent.Phase.START || mc.screen != null) {
            return;
        }
        if (RollClient.isRolling(player)) {
            lastRollTime = System.currentTimeMillis();
        }

        if (lockedOn) {
            if (target == null || !target.isAlive() || target.isRemoved() || target.level() != mc.level) {
                autoChangeTarget(player);
                return;
            }
            if (player.distanceToSqr(target) > LockOnConfig.MAX_RANGE.get() * LockOnConfig.MAX_RANGE.get()) {
                autoChangeTarget(player);
                return;
            }
            if (!canSee(player, target)) {
                if (++wallTimer >= LockOnConfig.WALL_TIMEOUT_TICKS.get()) {
                    leave();
                    return;
                }
            } else {
                wallTimer = 0;
            }
        }

        if (LOCK_ON.consumeClick()) {
            if (lockedOn) {
                leave();
            } else {
                LivingEntity candidate = underCameraRay(mc, player, LockOnConfig.MAX_RANGE.get());
                if (candidate == null) {
                    candidate = findBest(player, null, true, LockOnConfig.MAX_RANGE.get());
                }
                if (candidate != null) {
                    target = candidate;
                    lockedOn = true;
                    wallTimer = 0;
                    float[] cam = ShoulderCompat.cameraRotation();
                    if (cam != null) { // start turning from where the crosshair points, no jump
                        player.setYRot(cam[0]);
                        player.yRotO = cam[0];
                        player.setXRot(cam[1]);
                        player.xRotO = cam[1];
                    }
                }
            }
        }
        if (SWITCH.consumeClick() && lockedOn) {
            LivingEntity next = findBest(player, target, false, LockOnConfig.MAX_RANGE.get());
            if (next != null) {
                target = next;
            }
        }
        if (lockedOn && target != null) {
            updateAngles(player, target, event.renderTickTime);
            smoothLook(player, target, event.renderTickTime);
        }
    }

    private static void autoChangeTarget(LocalPlayer player) {
        LivingEntity next = findBest(player, null, true, LockOnConfig.AUTO_SWITCH_RANGE.get());
        if (next != null) {
            target = next;
            wallTimer = 0;
        } else {
            leave();
        }
    }

    /** The living entity the camera ray points at (the camera position under shoulder surfing, the eyes otherwise). */
    private static LivingEntity underCameraRay(Minecraft mc, LocalPlayer player, double range) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 from = camera.isInitialized() ? camera.getPosition() : player.getEyePosition();
        Vec3 dir = camera.isInitialized() ? new Vec3(camera.getLookVector()) : player.getLookAngle();
        Vec3 to = from.add(dir.scale(range));
        AABB box = new AABB(from, to).inflate(1.0);
        LivingEntity closest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && e.isPickable())) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(from, to);
            if (hit.isPresent()) {
                double d = from.distanceTo(hit.get());
                if (d < best) {
                    best = d;
                    closest = e;
                }
            }
        }
        return closest;
    }

    private static LivingEntity findBest(LocalPlayer player, LivingEntity current, boolean auto, double range) {
        List<LivingEntity> list = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(range, range / 2, range), e -> e != player && e.isAlive());
        Vec3 look = player.getLookAngle();
        Vec3 eye = player.getEyePosition();
        double maxAngle = LockOnConfig.AUTO_SWITCH_ANGLE.get();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : list) {
            if (e == current || !isHostile(e)) {
                continue;
            }
            Vec3 v = focus(e, 1.0f).subtract(eye);
            double dist = v.length();
            double dot = look.dot(v.normalize());
            if (dot < 0 || (auto && Math.toDegrees(Math.acos(Mth.clamp(dot, -1.0, 1.0))) > maxAngle) || !canSee(player, e)) {
                continue;
            }
            double score = auto ? dist : Math.acos(Mth.clamp(dot, -1.0, 1.0)) * dist;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private static boolean isHostile(LivingEntity e) {
        return e instanceof Enemy || e.getTeam() != null;
    }

    private static boolean canSee(LocalPlayer player, LivingEntity tgt) {
        return player.level().clip(new ClipContext(player.getEyePosition(), focus(tgt, 1.0f), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    static Vec3 focus(LivingEntity e, float pt) {
        return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()) + e.getBbHeight() * LockOnConfig.TARGET_HEIGHT_MULTIPLIER.get(),
                Mth.lerp(pt, e.zo, e.getZ()));
    }

    private static void updateAngles(LocalPlayer player, LivingEntity tgt, float pt) {
        Vec3 dir = focus(tgt, pt).subtract(player.getEyePosition(pt));
        if (dir.x * dir.x + dir.z * dir.z > 0.05) { // dead zone: standing inside the enemy must not spin the camera
            targetYaw = (float) Math.toDegrees(Mth.atan2(-dir.x, dir.z));
        }
        Vec3 n = dir.normalize();
        targetPitch = (float) Math.toDegrees(-Math.asin(Mth.clamp(n.y, -1.0, 1.0)));
    }

    private static boolean lookInit;
    private static float lookYaw;
    private static float lookPitch;

    private static void smoothLook(LocalPlayer player, LivingEntity tgt, float pt) {
        boolean postRoll = System.currentTimeMillis() - lastRollTime < 800;
        float speed = (float) (double) (postRoll ? LockOnConfig.POST_ROLL_ROTATION_SPEED.get() : LockOnConfig.ROTATION_SPEED.get());
        // own look angles: Shoulder Surfing turns the player to the camera while walking, which made the correction feed back into the aim
        if (!lookInit) {
            lookInit = true;
            lookYaw = player.getYRot();
            lookPitch = player.getXRot();
        }
        lookYaw = approach(lookYaw, targetYaw, speed, postRoll);
        lookPitch = approach(lookPitch, targetPitch, speed, postRoll);
        float yaw = lookYaw;
        float pitch = lookPitch;
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setXRot(pitch);
        player.xRotO = pitch;
        player.yHeadRot = yaw;
        player.yHeadRotO = yaw;
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;

        if (ShoulderCompat.cameraRotation() != null) {
            // parallax: the camera sits beside the player, aim it so the crosshair really lands on the target
            Vec3 off = ShoulderCompat.cameraOffset();
            double dist = Math.max(player.getEyePosition(pt).distanceTo(focus(tgt, pt)), 0.3);
            float yawCorrection = (float) Math.toDegrees(Math.atan(off.x / dist));
            float pitchCorrection = (float) Math.toDegrees(Math.atan(off.y / dist));
            ShoulderCompat.setCameraRotation(yaw + yawCorrection, pitch + pitchCorrection);
        }
    }

    private static float approach(float current, float target, float speed, boolean fast) {
        float diff = Mth.wrapDegrees(target - current);
        float step = fast ? diff * 0.45F : diff * 0.15F;
        return current + Mth.clamp(step, -speed, speed);
    }
}
