package com.voltyx.efw.hook.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.ShoulderCompat;
import com.voltyx.efw.hook.ChainHookEntity;
import com.voltyx.efw.network.EfwNetwork;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * What the chain hook looks and feels like around it:
 * <ul>
 * <li>a caught creature shakes while it is stunned (as SRP's creatures do when they throw up);</li>
 * <li>the thrower's camera jolts when the hook bites and hard when he lands, the view widens while he is dragged through the air
 * and punches in on the landing; the flight is seen over the shoulder (Shoulder Surfing), the camera before comes back with the
 * landing;</li>
 * <li>A / D in flight are sent to the server to swing round the creature;</li>
 * <li>where the fist is in the world in first person, for the chain to start at the fist drawn on screen.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class HookFx {
    /** Entity id -> client game time its stun ends (HookStun packet). */
    private static final Map<Integer, Long> STUNNED = new HashMap<>();
    private static final Set<LivingEntity> SHAKEN = java.util.Collections.newSetFromMap(new WeakHashMap<>());

    private static float shake, prevShake;
    private static float flight, prevFlight;
    private static float punch, prevPunch;
    private static byte lastState = -1;
    private static int lastHookId = -1;
    private static byte sentSteer;
    /** The world's field of view last frame (the first person hand is drawn with its own, fixed one). */
    private static double worldFov = 70.0;

    private HookFx() {
    }

    public static void stunned(int entityId, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            STUNNED.put(entityId, mc.level.getGameTime() + ticks);
        }
    }

    /** Once a client tick, after the hooks were looked up (HookClient). */
    static void tick(Minecraft mc) {
        prevShake = shake;
        prevFlight = flight;
        prevPunch = punch;
        shake *= 0.78F;
        punch *= 0.7F;
        if (mc.level == null) {
            STUNNED.clear();
            return;
        }
        long now = mc.level.getGameTime();
        STUNNED.values().removeIf(end -> end < now);
        LocalPlayer player = mc.player;
        ChainHookEntity hook = player != null ? HookClient.hookOf(player) : null;
        byte state = hook != null ? hook.state() : -1;
        int id = hook != null ? hook.getId() : -1;
        boolean fresh = id != lastHookId;
        if (state != lastState || fresh) {
            if ((state == ChainHookEntity.DRAG || state == ChainHookEntity.PULLED) && (fresh || lastState == ChainHookEntity.FLYING)
                    || state == ChainHookEntity.THROUGH) {
                shake = Math.max(shake, 0.5F); // the hook bites
            } else if (state == ChainHookEntity.PASS) {
                shake = Math.max(shake, 1.0F); // the blow on the way through
                punch = 0.7F;
            } else if (state == ChainHookEntity.IMPACT) {
                shake = Math.max(shake, 1.3F); // the landing
                punch = 1.0F;
            }
        }
        flightCamera(mc, state);
        lockOnDuringRun(state);
        lastState = state;
        lastHookId = id;
        flight = Mth.approach(flight, state == ChainHookEntity.PULLED || state == ChainHookEntity.THROUGH || state == ChainHookEntity.PASS
                || state == ChainHookEntity.DRAG ? 1.0F : 0.0F, 0.2F);

        byte steer = 0;
        if (state == ChainHookEntity.PULLED && mc.screen == null) {
            steer = (byte) ((mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0));
        }
        if (steer != sentSteer) {
            sentSteer = steer;
            EfwNetwork.CHANNEL.sendToServer(new EfwNetwork.HookStrafe(steer));
        }
    }

    /** The camera the flight switched away from: "FIRST_PERSON" or "THIRD_PERSON_BACK", null when nothing was switched. */
    private static String cameraBeforeFlight;

    /** The flight switched the camera and has not switched it back yet. */
    static boolean restoringCamera() {
        return cameraBeforeFlight != null;
    }

    /**
     * The flight to a creature is seen over the shoulder: Shoulder Surfing comes on when it starts, and the camera that was before
     * comes back with the landing blow (or when the chain lets go on the way). Shoulder surfing that was on already stays.
     */
    private static void flightCamera(Minecraft mc, byte state) {
        if (!ShoulderCompat.isLoaded()) {
            return;
        }
        boolean latched = state == ChainHookEntity.PULLED || state == ChainHookEntity.DRAG;
        if (latched && cameraBeforeFlight == null
                && !ShoulderCompat.isShoulderSurfing()) {
            cameraBeforeFlight = mc.options.getCameraType().isFirstPerson() ? "FIRST_PERSON" : "THIRD_PERSON_BACK";
            ShoulderCompat.changePerspective("SHOULDER_SURFING");
        } else if (!inFlight(state) && cameraBeforeFlight != null) {
            String back = cameraBeforeFlight;
            cameraBeforeFlight = null;
            if (ShoulderCompat.isShoulderSurfing()) {
                ShoulderCompat.changePerspective(back);
            }
        }
    }

    /** The flight and what follows it in the air (the landing, the combo's bounce, run through and roll). */
    private static boolean inFlight(byte state) {
        return state == ChainHookEntity.PULLED || state == ChainHookEntity.DRAG || state == ChainHookEntity.ARRIVED
                || state == ChainHookEntity.IMPACT || state == ChainHookEntity.BOUNCE
                || state == ChainHookEntity.REHOOK || state == ChainHookEntity.THROUGH || state == ChainHookEntity.PASS
                || state == ChainHookEntity.ROLL;
    }

    private static boolean lockOnSuspended;

    /** From the second launch until the roll is over the lock-on is off (it would fight the run through); then it is back. */
    private static void lockOnDuringRun(byte state) {
        boolean run = state == ChainHookEntity.THROUGH || state == ChainHookEntity.PASS || state == ChainHookEntity.ROLL;
        if (run && !lockOnSuspended) {
            lockOnSuspended = true;
            com.voltyx.efw.client.LockOnClient.suspend();
        } else if (!run && lockOnSuspended) {
            lockOnSuspended = false;
            com.voltyx.efw.client.LockOnClient.resume();
        }
    }

    private static boolean shaking(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        Long end = STUNNED.get(entity.getId());
        return mc.level != null && end != null && end >= mc.level.getGameTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!shaking(entity)) {
            return;
        }
        float t = entity.tickCount + event.getPartialTick();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        SHAKEN.add(entity);
        // a fast, jerky shudder of the whole body
        pose.translate(Mth.sin(t * 7.3F) * 0.035F, 0.0F, Mth.cos(t * 6.1F) * 0.035F);
        pose.mulPose(Axis.YP.rotationDegrees(Mth.cos(t * 3.25F) * 7.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 5.7F) * 2.5F));
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (SHAKEN.remove(event.getEntity())) {
            event.getPoseStack().popPose();
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float f = Mth.lerp((float) event.getPartialTick(), prevFlight, flight);
        if (f > 0.01F) {
            // in flight the horizon leans into the pull and the view trembles with the speed
            double ft = (Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0) + event.getPartialTick();
            event.setRoll(event.getRoll() + (float) Math.sin(ft * 0.35) * f * 3.0F + (float) Math.sin(ft * 5.1) * f * 0.35F);
        }
        float s = Mth.lerp((float) event.getPartialTick(), prevShake, shake);
        if (s < 0.01F) {
            return;
        }
        double t = (Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0) + event.getPartialTick();
        event.setPitch(event.getPitch() + (float) Math.sin(t * 2.9) * s * 1.6F);
        event.setYaw(event.getYaw() + (float) Math.cos(t * 3.7) * s * 1.2F);
        event.setRoll(event.getRoll() + (float) Math.sin(t * 4.3) * s * 2.2F);
    }

    @SubscribeEvent
    public static void onFovModifier(ComputeFovModifierEvent event) {
        float partial = Minecraft.getInstance().getFrameTime();
        float f = Mth.lerp(partial, prevFlight, flight);
        float p = Mth.lerp(partial, prevPunch, punch);
        if (f > 0.001F || p > 0.001F) {
            event.setNewFovModifier(event.getNewFovModifier() * (1.0F + 0.22F * f - 0.14F * p));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) {
            worldFov = event.getFOV();
        }
    }

    /**
     * The first person fist in the world: the point of the view the swing is drawn from (HookSwingView), moved so it lands on the same
     * place of the screen with the world's field of view as with the hand's.
     */
    static Vec3 firstPersonHand(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        int side = player.getMainArm() == HumanoidArm.RIGHT ? -1 : 1;
        double k = Math.tan(Math.toRadians(worldFov) * 0.5) / Math.tan(Math.toRadians(70.0) * 0.5);
        // the camera's own frame looks along +Z with +X to the left; the view the hand is drawn in looks along -Z with +X to the right
        Vector3f local = new Vector3f((float) -(side * HookSwingView.SIDE * k), (float) (HookSwingView.DOWN * k), -HookSwingView.DEPTH);
        Vector3f world = camera.rotation().transform(local);
        Vec3 cam = camera.getPosition();
        return new Vec3(cam.x + world.x, cam.y + world.y, cam.z + world.z);
    }
}
