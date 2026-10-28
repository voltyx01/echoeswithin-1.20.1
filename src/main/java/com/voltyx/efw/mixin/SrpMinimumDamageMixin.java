package com.voltyx.efw.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Scape and Run: Parasites takes a parasite's minimum damage (armour ignored) straight off the health before every normal attack and
 * plays the hurt of it on its own, so each blow looked like two (the second with Legendary Survival Overhaul's broken hearts). The
 * minimum damage stays; only its separate hurt is not shown, the normal attack right after shows the blow.
 */
@Pseudo
@Mixin(targets = "com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase", remap = false)
public abstract class SrpMinimumDamageMixin {
    @Redirect(method = "attackEntityAsMobMinimum", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;broadcastEntityEvent(Lnet/minecraft/world/entity/Entity;B)V", remap = true),
            remap = false, require = 0)
    private void efw$noSecondHurt(Level level, Entity entity, byte event) {
        if (event != 2) { // 2: the hurt (the shield being disabled goes through)
            level.broadcastEntityEvent(entity, event);
        }
    }
}
