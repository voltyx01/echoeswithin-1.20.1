package com.voltyx.efw.heartbeat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.voltyx.efw.compat.TaczCompat;
import net.minecraft.client.Minecraft;

import java.util.Random;

/** Shaking of the hands and the weapon in the first person at a pulse over 150 (mwccf HeartbeatTremorHelper). */
public final class HeartbeatTremor {
    private static float smoothShake;
    private static final Random RAND = new Random();

    private HeartbeatTremor() {
    }

    /** Smoothed shake of the body for the third person model, ticked on its own: the first person pass does not run there. */
    private static float bodyShake;

    public static void reset() {
        smoothShake = 0.0f;
        bodyShake = 0.0f;
    }

    /** How hard the body of the player shakes from the pulse, 0..1 (starts where the hands start shaking in first person). */
    public static float bodyShake() {
        return bodyShake < 0.3f ? 0.0f : (bodyShake - 0.3f) / 0.7f;
    }

    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.voltyx.efw.EFW.MOD_ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class Events {
        private Events() {
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent e) {
            if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !HeartbeatConfig.TREMOR.get() || !HeartbeatConfig.ENABLED.get()) {
                bodyShake = 0.0f;
                return;
            }
            float bpm = HeartbeatManager.currentBPM;
            float target = bpm > 120.0f ? Math.min(1.0f, (bpm - 120.0f) / 60.0f) : 0.0f;
            bodyShake += (target - bodyShake) * 0.25f;
        }
    }

    public static void apply(PoseStack pose) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused() || !HeartbeatConfig.TREMOR.get() || !HeartbeatConfig.ENABLED.get()) {
            return;
        }
        float bpm = HeartbeatManager.currentBPM;
        float targetShake = bpm > 120.0f ? Math.min(1.0f, (bpm - 120.0f) / 60.0f) : 0.0f;
        smoothShake += (targetShake - smoothShake) * 0.06f;
        if (smoothShake < 0.3f) {
            return;
        }
        float transAmp = (smoothShake - 0.3f) / 0.7f;
        transAmp = transAmp * transAmp * 0.06f;
        float rotAmp = smoothShake * 0.6f;

        float tx = (RAND.nextFloat() - 0.5f) * transAmp;
        float ty = (RAND.nextFloat() - 0.5f) * transAmp;
        float tz = (RAND.nextFloat() - 0.5f) * transAmp;
        float rx = (RAND.nextFloat() - 0.5f) * rotAmp;
        float ry = (RAND.nextFloat() - 0.5f) * rotAmp;
        float rz = (RAND.nextFloat() - 0.5f) * rotAmp * 0.5f;
        if (TaczCompat.isAiming(mc.player)) {
            tx *= 0.1f;
            ty *= 0.1f;
            tz *= 0.1f;
            rx *= 0.2f;
            ry *= 0.2f;
            rz *= 0.2f;
        }
        pose.translate(tx, ty, tz);
        pose.mulPose(Axis.XP.rotationDegrees(rx));
        pose.mulPose(Axis.YP.rotationDegrees(ry));
        pose.mulPose(Axis.ZP.rotationDegrees(rz));
    }
}
