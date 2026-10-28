package com.voltyx.efw.mixin;

import com.voltyx.efw.sins.stat.SinStats;
import com.voltyx.efw.sins.stat.Stat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** SEVEN spread: the inaccuracy TACZ settles on for a shot ({@code Math.max(0, inaccuracy)}) is scaled for players. */
@Pseudo
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunScriptAPI", remap = false)
public abstract class TaczSpreadMixin {
    @Shadow
    private LivingEntity shooter;

    @Redirect(method = "shootOnce", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F", ordinal = 0), remap = false, require = 0)
    private float efw$spread(float floor, float inaccuracy) {
        if (shooter instanceof Player player) {
            inaccuracy *= (float) SinStats.mult(player, Stat.GUN_SPREAD);
        }
        return Math.max(floor, inaccuracy);
    }
}
