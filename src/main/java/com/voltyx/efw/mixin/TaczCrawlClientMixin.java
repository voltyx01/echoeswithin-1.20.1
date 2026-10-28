package com.voltyx.efw.mixin;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessagePlayerCrawl;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TACZ lets the player crawl only with a gun in the hand. Without one (empty hand, a tool, food...) the same crawl works here, with
 * TACZ's own rules: on the ground, not riding, not swimming; jumping gets the player up. With a gun TACZ decides as before.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerCrawl", remap = false)
public abstract class TaczCrawlClientMixin {
    @Shadow
    @Final
    private LocalPlayer player;
    @Shadow
    private boolean isCrawling;
    @Shadow
    private int crawCooldownTicks;

    @Shadow
    private void setCrawlPose() {
    }

    @Inject(method = "crawl", at = @At("HEAD"), cancellable = true, remap = false)
    private void efw$crawlWithoutGun(boolean crawl, CallbackInfo ci) {
        if (player.getMainHandItem().getItem() instanceof IGun) {
            return;
        }
        ci.cancel();
        if (crawCooldownTicks <= 0 && !player.isSpectator() && !player.isPassenger() && player.onGround()) {
            isCrawling = crawl;
            crawCooldownTicks = 10;
            NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerCrawl(crawl));
        }
    }

    @Inject(method = "tickCrawl", at = @At("HEAD"), cancellable = true, remap = false)
    private void efw$tickWithoutGun(CallbackInfo ci) {
        if (player.getMainHandItem().getItem() instanceof IGun) {
            return;
        }
        ci.cancel();
        if (crawCooldownTicks > 0) {
            crawCooldownTicks--;
        }
        if (isCrawling && (player.isSpectator() || player.isPassenger() || player.input.jumping || player.isSwimming()
                || !player.onGround())) {
            isCrawling = false;
            NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerCrawl(false));
        }
        setCrawlPose();
    }
}
