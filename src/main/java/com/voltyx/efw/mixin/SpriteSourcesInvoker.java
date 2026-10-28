package com.voltyx.efw.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Registers EFW's own atlas sprite source types (the bitten food sprites). */
@Mixin(SpriteSources.class)
public interface SpriteSourcesInvoker {
    @Invoker("register")
    static SpriteSourceType efw$register(String name, Codec<? extends SpriteSource> codec) {
        throw new AssertionError();
    }
}
