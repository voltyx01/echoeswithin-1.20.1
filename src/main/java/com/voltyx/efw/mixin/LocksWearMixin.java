package com.voltyx.efw.mixin;

import com.voltyx.efw.sins.stat.SinStats;
import com.voltyx.efw.sins.stat.Stat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Locks Reforged: the wear a wrong pin takes off the pick is scaled by SEVEN (the lockpicking manual, cards). */
@Pseudo
@Mixin(targets = "melonslise.locks.common.container.LockPickingContainer", remap = false)
public abstract class LocksWearMixin {
    @ModifyArg(method = "wearPick", at = @At(value = "INVOKE",
            target = "Lmelonslise/locks/common/item/LockPickItem;damagePick(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;I)Z"),
            index = 3, remap = false, require = 0)
    private int efw$wear(ItemStack pick, Player player, InteractionHand hand, int wear) {
        if (player == null || wear <= 0) {
            return wear;
        }
        double scaled = wear * SinStats.mult(player, Stat.LOCKPICK_WEAR);
        int whole = (int) Math.floor(scaled);
        return Math.max(1, whole + (player.getRandom().nextDouble() < scaled - whole ? 1 : 0));
    }
}
