package com.voltyx.efw.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The projection aspect follows the window size: the bodycam feed sets it to its own size for its pass. */
@Mixin(Window.class)
public interface WindowAccessor {
    @Accessor("framebufferWidth")
    void efw$setFramebufferWidth(int width);

    @Accessor("framebufferHeight")
    void efw$setFramebufferHeight(int height);
}
