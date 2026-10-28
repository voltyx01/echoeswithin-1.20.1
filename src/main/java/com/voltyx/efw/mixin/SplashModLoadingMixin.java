package com.voltyx.efw.mixin;

import com.voltyx.efw.client.loading.EfwSplash;
import net.minecraftforge.client.loading.ClientModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * While the mods load (inside the Minecraft constructor, before any screen exists) Forge keeps drawing its early window through a
 * periodic tick. That tick is swapped for the efw splash. The call sits in the lambda of {@code begin} (lambda$begin$1 in Forge
 * 47.1 to 47.4); when a Forge version names it differently, nothing is changed and the Forge window stays.
 */
@Mixin(value = ClientModLoader.class, remap = false)
public abstract class SplashModLoadingMixin {
    @ModifyArg(method = "lambda$begin$1", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/fml/ModLoader;gatherAndInitializeMods(Lnet/minecraftforge/fml/ModWorkManager$DrivenExecutor;Ljava/util/concurrent/Executor;Ljava/lang/Runnable;)V"),
            index = 2, remap = false, require = 0)
    private static Runnable efw$splashTick(Runnable forgeTick) {
        return EfwSplash.providerActive() ? forgeTick : EfwSplash::earlyTick;
    }
}
