package com.voltyx.efw.mixin;

import com.voltyx.efw.tech.client.CameraFocus;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Terminal / safe glide and the bodycam viewpoint override the camera after every other camera mod (high priority). */
@Mixin(value = Camera.class, priority = 2000)
public abstract class CameraFocusMixin {
    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract Vec3 getPosition();

    @Shadow
    public abstract float getYRot();

    @Shadow
    public abstract float getXRot();

    @Inject(method = "setup", at = @At("TAIL"))
    private void efw$focus(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        CameraFocus.Pose pose = CameraFocus.compute(getPosition(), getYRot(), getXRot());
        if (pose != null) {
            setRotation(pose.yaw(), pose.pitch());
            setPosition(pose.pos());
        }
    }
}
