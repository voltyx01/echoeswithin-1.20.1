package com.voltyx.efw.client.loading;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.chat.Component;

/** The one-frame "Joining world" progress screen that vanilla shows right before the world appears. */
public class EfwProgressScreen extends ProgressScreen {
    private Component header;
    private Component stage;

    public EfwProgressScreen(boolean clearScreenAfterStop, Component header, Component stage) {
        super(clearScreenAfterStop);
        this.header = header;
        this.stage = stage;
    }

    @Override
    public void progressStartNoAbort(Component header) {
        this.header = header;
        super.progressStartNoAbort(header);
    }

    @Override
    public void progressStart(Component header) {
        this.header = header;
        super.progressStart(header);
    }

    @Override
    public void progressStage(Component stage) {
        this.stage = stage;
        super.progressStage(stage);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the stage is the status; the heading ("Joining world") only while there is no stage yet
        boolean hasStage = this.stage != null && !this.stage.getString().isBlank();
        LoadingPainter.render(this, g, hasStage ? this.stage : this.header, null);
    }
}
