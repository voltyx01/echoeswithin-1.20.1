package com.voltyx.efw.mixin;

import com.voltyx.efw.compat.SsrTransition;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shoulder Surfing: entering starts with the camera in the eyes and glides out to the shoulder offset. */
@Pseudo
@Mixin(targets = "com.github.exopandora.shouldersurfing.client.ShoulderSurfingCamera", remap = false)
public abstract class SsrCameraMixin {
    @Shadow(remap = false)
    private Vec3 offset;
    @Shadow(remap = false)
    private Vec3 offsetO;
    @Shadow(remap = false)
    private Vec3 renderOffset;
    @Shadow(remap = false)
    private double maxCameraDistance;
    @Shadow(remap = false)
    private double maxCameraDistanceO;
    @Shadow(remap = false)
    private Vec3 targetOffset;

    /**
     * During the glides of the transitions the camera takes a second smoothing step per tick with the same factor SSR just used,
     * so it moves twice as fast there and keeps SSR's speed everywhere else.
     */
    @Inject(method = "tick", at = @At("TAIL"), remap = false, require = 0)
    private void efw$fasterGlide(CallbackInfo ci) {
        if (!SsrTransition.isGliding() || targetOffset == null || offsetO == null || offset == null) {
            return;
        }
        double whole = targetOffset.distanceTo(offsetO);
        if (whole < 1.0E-4) {
            return;
        }
        double k = Math.max(0.0, Math.min(1.0, offset.distanceTo(offsetO) / whole));
        offset = offset.lerp(targetOffset, k);
        maxCameraDistance += (offset.length() - maxCameraDistance) * k;
    }

    @Inject(method = "init", at = @At("TAIL"), remap = false, require = 0)
    private void efw$smoothEntry(CallbackInfo ci) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        Vec3 close = SsrTransition.CLOSE;
        offset = close;
        offsetO = close;
        renderOffset = close;
        maxCameraDistance = close.length();
        maxCameraDistanceO = maxCameraDistance;
    }
}
