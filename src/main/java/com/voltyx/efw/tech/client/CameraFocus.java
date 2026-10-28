package com.voltyx.efw.tech.client;

import com.voltyx.efw.EFW;
import net.minecraft.client.player.Input;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Camera overrides of the terminal, the safe and the bodycam feed, applied at the end of Camera.setup (CameraFocusMixin). */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class CameraFocus {
    public record Pose(Vec3 pos, float yaw, float pitch) {
    }

    /** Pose applied by the last Camera.setup, kept against later angle tweaks of other mods. */
    private static Pose applied;

    private CameraFocus() {
    }

    /** The camera pose to use instead of the computed one, or null to keep it. */
    public static Pose compute(Vec3 pos, float yaw, float pitch) {
        Pose p;
        if (BodycamFeed.isRendering()) {
            p = BodycamFeed.cameraPose();
        } else {
            p = TerminalCamera.cameraPose(pos, yaw, pitch);
            if (p == null) {
                p = AntennaCamera.cameraPose(pos, yaw, pitch);
            }
        }
        applied = p;
        return p;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAngles(ViewportEvent.ComputeCameraAngles e) {
        if (applied != null) {
            e.setYaw(applied.yaw());
            e.setPitch(applied.pitch());
            if (BodycamFeed.isRendering()) {
                e.setRoll(BodycamFeed.cameraRoll());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFov(ViewportEvent.ComputeFov e) {
        if (BodycamFeed.isRendering()) {
            e.setFOV(BodycamPose.fov());
        }
    }

    /** No walking, jumping or sneaking while at a console. */
    static void freeze(Input input) {
        input.forwardImpulse = 0.0f;
        input.leftImpulse = 0.0f;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }
}
