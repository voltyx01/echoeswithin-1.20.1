package com.voltyx.efw.mixin;

import com.voltyx.efw.client.dark.DarkGui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Dark theme: the dark grey labels of container screens become light. */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsDarkMixin {
    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private int efw$darkText1(int color) {
        return DarkGui.adjustTextColor(color);
    }

    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private int efw$darkText2(int color) {
        return DarkGui.adjustTextColor(color);
    }

    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private int efw$darkText3(int color) {
        return DarkGui.adjustTextColor(color);
    }
}
