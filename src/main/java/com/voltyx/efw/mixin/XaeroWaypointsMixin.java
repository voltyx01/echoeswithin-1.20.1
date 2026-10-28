package com.voltyx.efw.mixin;

import com.voltyx.efw.tech.client.XaeroGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The waypoints in the world are drawn only while a charged portable map is worn. */
@Pseudo
@Mixin(targets = "xaero.common.minimap.waypoints.render.WaypointsIngameRenderer", remap = false)
public abstract class XaeroWaypointsMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void efw$noMapNoWaypoints(CallbackInfo ci) {
        if (!XaeroGate.hasActiveMap()) {
            ci.cancel();
        }
    }
}
