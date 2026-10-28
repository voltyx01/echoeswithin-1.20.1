package com.voltyx.efw.mixin;

import com.voltyx.efw.sins.stat.SinHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** SEVEN: weapons and tools wear faster or slower in the hands of this player (the damage is scaled before unbreaking). */
@Mixin(ItemStack.class)
public abstract class ItemStackWearMixin {
    @Unique
    private static final ThreadLocal<Boolean> EFW_INSIDE = ThreadLocal.withInitial(() -> false);

    @Shadow
    public abstract boolean hurt(int amount, RandomSource random, @Nullable ServerPlayer player);

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void efw$wear(int amount, RandomSource random, @Nullable ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (player == null || amount <= 0 || EFW_INSIDE.get()) {
            return;
        }
        int scaled = SinHooks.wear((ItemStack) (Object) this, player, amount);
        if (scaled == amount) {
            return;
        }
        EFW_INSIDE.set(true);
        try {
            cir.setReturnValue(scaled > 0 && hurt(scaled, random, player));
        } finally {
            EFW_INSIDE.set(false);
        }
    }
}
