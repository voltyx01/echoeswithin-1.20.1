package com.voltyx.efw.mixin;

import com.voltyx.efw.hook.ChainHookItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Better Combat empties the off hand while a two-handed weapon is held (Survival Instinct's katanas, greataxes...): the chain hook
 * is kept in it anyway for a player who has learnt it (Stat.HOOK_TWO_HANDED, the melee manual's third volume). Runs before Better
 * Combat's hook on the same method (lower priority).
 */
@Mixin(value = Player.class, priority = 900)
public abstract class PlayerOffhandHookMixin {
    /** Working out the stats may ask for what is in the hands again: that inner ask goes the usual way. */
    private static final ThreadLocal<Boolean> EFW_CHECKING = ThreadLocal.withInitial(() -> false);

    @Inject(method = "getItemBySlot", at = @At("HEAD"), cancellable = true)
    private void efw$keepChainHook(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot != EquipmentSlot.OFFHAND || EFW_CHECKING.get()) {
            return;
        }
        Player self = (Player) (Object) this;
        ItemStack offhand = self.getInventory().offhand.get(0);
        if (!(offhand.getItem() instanceof ChainHookItem)) {
            return;
        }
        // another player seen from this client: their manuals are not known here, and the server has already decided what they hold
        // (the equipment it sends goes through this same check there)
        if (self.level().isClientSide && !self.isLocalPlayer()) {
            cir.setReturnValue(offhand);
            return;
        }
        EFW_CHECKING.set(true);
        try {
            if (ChainHookItem.twoHandedGrip(self)) {
                cir.setReturnValue(offhand);
            }
        } finally {
            EFW_CHECKING.set(false);
        }
    }
}
