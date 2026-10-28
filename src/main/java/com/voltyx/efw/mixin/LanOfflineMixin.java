package com.voltyx.efw.mixin;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A world opened to LAN lets in players without a Mojang account (launcher nick): the pack is played with offline nicks, and with
 * account checks on they could not join a friend's world at all.
 */
@Mixin(IntegratedServer.class)
public abstract class LanOfflineMixin {
    @Inject(method = "publishServer", at = @At("HEAD"))
    private void efw$offline(GameType gameType, boolean cheats, int port, CallbackInfoReturnable<Boolean> cir) {
        ((IntegratedServer) (Object) this).setUsesAuthentication(false);
    }
}
