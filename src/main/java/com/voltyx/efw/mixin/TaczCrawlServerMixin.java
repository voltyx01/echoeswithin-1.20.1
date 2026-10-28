package com.voltyx.efw.mixin;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The server half of crawling without a gun (see {@code TaczCrawlClientMixin}): TACZ would stand the player up at once. */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityCrawl", remap = false)
public abstract class TaczCrawlServerMixin {
    @Shadow
    @Final
    private LivingEntity shooter;
    @Shadow
    @Final
    private ShooterDataHolder data;

    @Shadow
    private void setCrawlPose() {
    }

    @Inject(method = "tickCrawling", at = @At("HEAD"), cancellable = true, remap = false)
    private void efw$tickWithoutGun(CallbackInfo ci) {
        ItemStack held = data.currentGunItem == null ? shooter.getMainHandItem() : data.currentGunItem.get();
        if (held.getItem() instanceof IGun) {
            return;
        }
        ci.cancel();
        if (data.isCrawling && (shooter.isSpectator() || shooter.isPassenger() || shooter.isSwimming() || !shooter.onGround())) {
            data.isCrawling = false;
        }
        setCrawlPose();
    }
}
