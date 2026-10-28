package com.voltyx.efw.mixin;

import com.voltyx.efw.sins.stat.SinStats;
import com.voltyx.efw.sins.stat.Stat;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** SEVEN: how fast the player gets hungry (every source of exhaustion goes through here). */
@Mixin(Player.class)
public abstract class PlayerExhaustionMixin {
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float efw$exhaustion(float amount) {
        Player self = (Player) (Object) this;
        return self.level().isClientSide ? amount : amount * (float) SinStats.mult(self, Stat.FOOD_EXHAUSTION);
    }
}
