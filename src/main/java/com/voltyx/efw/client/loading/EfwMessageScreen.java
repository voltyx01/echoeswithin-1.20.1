package com.voltyx.efw.client.loading;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.network.chat.Component;

/** A plain message screen of the world loading ("Reading world data...") drawn as the efw loading screen. */
public class EfwMessageScreen extends GenericDirtMessageScreen {
    public EfwMessageScreen(Component message) {
        super(message);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        LoadingPainter.render(this, g, this.title, null);
    }
}
