package com.voltyx.efw.mixin;

import com.voltyx.efw.roll.RollState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rolling players pass through entities and are not pushed (1.12 MixinPlayerRollCollision). */
@Mixin(Entity.class)
public abstract class EntityPushMixin {
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void efw$noPushWhileRolling(Entity other, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if ((self instanceof Player p && RollState.isRolling(p)) || (other instanceof Player q && RollState.isRolling(q))) {
            ci.cancel();
        }
    }
}
