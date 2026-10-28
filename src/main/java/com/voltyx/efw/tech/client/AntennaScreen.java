package com.voltyx.efw.tech.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Keyboard capture of the safe's keypad (port of GuiAntenna). */
public class AntennaScreen extends Screen {
    public AntennaScreen() {
        super(Component.literal("Safe"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        TerminalScreen.hideCursor(true);
    }

    @Override
    public void removed() {
        TerminalScreen.hideCursor(false);
        if (AntennaCamera.isActive()) {
            AntennaCamera.close();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            AntennaCamera.close();
            return true;
        }
        AntennaSession.get().keyPressed(key, AntennaCamera.getCurrentAntenna(), AntennaCamera.getAntennaPos());
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        AntennaSession.get().charTyped(c, AntennaCamera.getCurrentAntenna(), AntennaCamera.getAntennaPos());
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            AntennaCamera.close();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        AntennaCamera.renderPrompt(g);
    }
}
