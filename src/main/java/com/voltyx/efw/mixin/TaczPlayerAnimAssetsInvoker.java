package com.voltyx.efw.mixin;

import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

/** The third person animations of a TACZ gun pack (the lookup is package private). */
@Pseudo
@Mixin(targets = "com.tacz.guns.compat.playeranimator.animation.PlayerAnimatorAssetManager", remap = false)
public interface TaczPlayerAnimAssetsInvoker {
    @Invoker(value = "getAnimations", remap = false)
    Optional<KeyframeAnimation> efw$getAnimations(ResourceLocation id, String name);
}
