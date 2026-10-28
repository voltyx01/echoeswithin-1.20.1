package com.voltyx.efw.client.loading;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;

/** "Loading terrain..." (joining a server / changing dimension). Vanilla closing logic is kept, only drawing is replaced. */
public class EfwReceivingLevelScreen extends ReceivingLevelScreen {
    private static final Component STATUS = Component.translatable("multiplayer.downloadingTerrain");

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        LoadingPainter.render(this, g, STATUS, null);
    }
}
