package com.voltyx.efw.client.loading;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;

/** Singleplayer world start ("Preparing spawn area", without vanilla's percentage). Vanilla logic is kept, only drawing is replaced. */
public class EfwLevelLoadingScreen extends LevelLoadingScreen {
    public EfwLevelLoadingScreen(StoringChunkProgressListener progress) {
        super(progress);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // only the status: the "Loading world" heading stood above it the whole time
        LoadingPainter.render(this, g, Component.translatable("loading.efw.preparing_spawn"), null);
    }
}
