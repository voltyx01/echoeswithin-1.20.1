package com.voltyx.efw.hook.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.ShoulderCompat;
import com.voltyx.efw.compat.TaczCompat;
import com.voltyx.efw.compat.client.TaczShoot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * The finish of the chain combo on this client (HookFinisher on the server sends it, as the camera starts back to where it was). Each
 * step waits for what it needs, not for a time:
 * <ol>
 * <li>DRAW: the shotgun's hotbar slot is taken at once and TACZ draws it; on to the aim when the gun is out and the camera is back
 * (the glide into first person over);</li>
 * <li>AIM: the aim key is held; when the gun is fully up to the eye, it is fired;</li>
 * <li>HOLD: the gun stays up a moment after the shot, then the key is let go.</li>
 * </ol>
 * The view stays on the creature until the shot. Given up when the creature is gone, the gun leaves the hand, or it never comes ready.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class HookFinisherClient {
    private enum Step { NONE, DRAW, AIM, HOLD }

    /** Only a safety net: a gun that never comes out or up (no aim, jammed) is given up after this long. */
    private static final int GIVE_UP_TICKS = 100;
    /** Ticks the gun stays up after the shot. */
    private static final int HOLD_TICKS = 8;
    /** TACZ's answers that mean "not yet": try the trigger again next tick. */
    private static final Set<String> WAIT = Set.of("IS_DRAWING", "NOT_DRAW", "COOL_DOWN", "IS_BOLTING", "IS_SPRINTING");
    private static final float PULL = 0.5F;

    private static Step step = Step.NONE;
    private static int targetId = -1;
    private static int ticks;

    private HookFinisherClient() {
    }

    public static void start(int target, int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !TaczCompat.isLoaded() || slot < 0 || slot > 8) {
            return;
        }
        mc.player.getInventory().selected = slot; // TACZ starts drawing it
        targetId = target;
        ticks = 0;
        step = Step.DRAW;
    }

    private static LivingEntity target(Minecraft mc) {
        return targetId >= 0 && mc.level != null && mc.level.getEntity(targetId) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /** The camera is where it was before the flight: the chain's camera switch is done and Shoulder Surfing is not gliding. */
    private static boolean cameraSettled() {
        return !HookFx.restoringCamera() && !ShoulderCompat.isGliding();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || step == Step.NONE) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || !TaczCompat.isHoldingGun(player) || ++ticks > GIVE_UP_TICKS) {
            stop();
            return;
        }
        switch (step) {
            case DRAW -> {
                if (target(mc) == null) {
                    stop();
                } else if (TaczShoot.isDrawn() && cameraSettled()) {
                    step = Step.AIM;
                }
            }
            case AIM -> {
                if (target(mc) == null) {
                    stop();
                    return;
                }
                player.setSprinting(false);
                TaczShoot.holdAim(true);
                if (TaczShoot.aimProgress() < 1.0F) {
                    return; // still coming up to the eye
                }
                String result = TaczShoot.shoot();
                if ("SUCCESS".equals(result)) {
                    step = Step.HOLD;
                    ticks = 0;
                } else if (result == null || !WAIT.contains(result)) {
                    stop(); // it cannot be fired: no rounds, reloading
                }
            }
            case HOLD -> {
                if (ticks >= HOLD_TICKS) {
                    stop();
                }
            }
            default -> {
            }
        }
    }

    private static void stop() {
        if (step == Step.AIM || step == Step.HOLD) {
            TaczShoot.holdAim(false);
        }
        step = Step.NONE;
        targetId = -1;
    }

    /** The view stays on the creature until the shot. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.START || (step != Step.DRAW && step != Step.AIM) || mc.player == null || mc.isPaused()) {
            return;
        }
        LivingEntity target = target(mc);
        if (target == null) {
            return;
        }
        LocalPlayer player = mc.player;
        float pt = e.renderTickTime;
        Vec3 dir = new Vec3(Mth.lerp(pt, target.xo, target.getX()), Mth.lerp(pt, target.yo, target.getY()) + target.getBbHeight() * 0.6,
                Mth.lerp(pt, target.zo, target.getZ())).subtract(player.getEyePosition(pt));
        if (dir.lengthSqr() < 0.25) {
            return;
        }
        float wantYaw = (float) Math.toDegrees(Mth.atan2(-dir.x, dir.z));
        float wantPitch = (float) Math.toDegrees(-Math.asin(Mth.clamp(dir.normalize().y, -1.0, 1.0)));
        float yaw = player.getYRot() + Mth.wrapDegrees(wantYaw - player.getYRot()) * PULL;
        float pitch = player.getXRot() + (wantPitch - player.getXRot()) * PULL;
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setXRot(pitch);
        player.xRotO = pitch;
        player.yHeadRot = yaw;
        player.yHeadRotO = yaw;
        float[] cam = ShoulderCompat.cameraRotation();
        if (cam != null) {
            Vec3 off = ShoulderCompat.cameraOffset();
            double dist = Math.max(dir.length(), 0.3);
            float camYaw = wantYaw + (float) Math.toDegrees(Math.atan(off.x / dist));
            float camPitch = wantPitch + (float) Math.toDegrees(Math.atan(off.y / dist));
            ShoulderCompat.setCameraRotation(cam[0] + Mth.wrapDegrees(camYaw - cam[0]) * PULL, cam[1] + (camPitch - cam[1]) * PULL);
        }
    }
}
