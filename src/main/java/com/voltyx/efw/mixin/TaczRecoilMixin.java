package com.voltyx.efw.mixin;

import com.voltyx.efw.sins.stat.SinStats;
import com.voltyx.efw.sins.stat.Stat;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** SEVEN recoil: the camera kick TACZ builds for a shot of the local player is scaled. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.event.CameraSetupEvent", remap = false)
public abstract class TaczRecoilMixin {
    @ModifyArg(method = "initialCameraRecoil", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/resource/pojo/data/gun/GunRecoil;genPitchSplineFunction(F)Lorg/apache/commons/math3/analysis/polynomials/PolynomialSplineFunction;"),
            index = 0, remap = false, require = 0)
    private static float efw$pitch(float modifier) {
        return modifier * recoil();
    }

    @ModifyArg(method = "initialCameraRecoil", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/resource/pojo/data/gun/GunRecoil;genYawSplineFunction(F)Lorg/apache/commons/math3/analysis/polynomials/PolynomialSplineFunction;"),
            index = 0, remap = false, require = 0)
    private static float efw$yaw(float modifier) {
        return modifier * recoil();
    }

    private static float recoil() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 1.0f : (float) SinStats.mult(mc.player, Stat.GUN_RECOIL);
    }
}
