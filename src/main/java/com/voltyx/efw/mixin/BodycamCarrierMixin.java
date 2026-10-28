package com.voltyx.efw.mixin;

import com.voltyx.efw.tech.client.BodycamFeed;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In the bodycam picture the camera sits inside the carrier's head; the 1.12 head was culled from inside, the 1.20 player
 * model is drawn without culling, so the carrier's head and hat are hidden for that pass.
 */
@Mixin(PlayerRenderer.class)
public abstract class BodycamCarrierMixin {
    @Inject(method = "setModelProperties", at = @At("TAIL"))
    private void efw$hideCarrierHead(AbstractClientPlayer player, CallbackInfo ci) {
        if (BodycamFeed.isRendering() && player == BodycamFeed.getCurrentCarrier()) {
            PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
            model.head.visible = false;
            model.hat.visible = false;
            if (com.voltyx.efw.tech.client.BodycamPose.hideArms()) {
                model.leftArm.visible = false;
                model.rightArm.visible = false;
                model.leftSleeve.visible = false;
                model.rightSleeve.visible = false;
            }
        }
    }
}
