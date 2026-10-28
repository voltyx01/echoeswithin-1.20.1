package com.voltyx.efw.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** No hands in the bodycam picture. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("renderHand")
    boolean efw$getRenderHand();

    @Accessor("renderHand")
    void efw$setRenderHand(boolean renderHand);
}
