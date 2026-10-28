package com.voltyx.efw.mixin;

import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.voltyx.efw.sins.stat.SinStats;
import com.voltyx.efw.sins.stat.Stat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SEVEN reload speed: TACZ measures a reload by the time since it started, so every server tick the start is moved back (faster)
 * or forward (slower) by the difference. Works for every gun and reload type; the client animation keeps its own pace.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityReload", remap = false)
public abstract class TaczReloadSpeedMixin {
    @Shadow
    @Final
    private LivingEntity shooter;
    @Shadow
    @Final
    private ShooterDataHolder data;

    @Inject(method = "tickReloadState", at = @At("HEAD"), remap = false, require = 0)
    private void efw$speed(CallbackInfoReturnable<?> cir) {
        if (!(shooter instanceof Player player) || data.reloadStateType == null || !data.reloadStateType.isReloading()
                || data.reloadTimestamp <= 0) {
            return;
        }
        double speed = SinStats.mult(player, Stat.GUN_RELOAD);
        if (Math.abs(speed - 1.0) > 1.0E-3) {
            data.reloadTimestamp -= Math.round(50.0 * (speed - 1.0));
        }
    }
}
