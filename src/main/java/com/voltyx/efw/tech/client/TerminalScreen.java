package com.voltyx.efw.tech.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Keyboard capture of the terminal (port of GuiTerminal): transparent, the console is drawn on the monitor in the world. */
public class TerminalScreen extends Screen {
    public TerminalScreen() {
        super(Component.literal("Terminal"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        hideCursor(true);
    }

    /** Mouse.setGrabbed of 1.12: no cursor over the monitor. */
    static void hideCursor(boolean hide) {
        GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_CURSOR,
                hide ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_NORMAL);
    }

    @Override
    public void removed() {
        hideCursor(false);
        if (TerminalCamera.isActive()) {
            TerminalCamera.close();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        TerminalSession session = TerminalSession.get();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            TerminalSession.Stage stage = session.getStage();
            if (stage == TerminalSession.Stage.CHAT_MENU || stage == TerminalSession.Stage.APP_CHAT
                    || stage == TerminalSession.Stage.BODYCAM_LIST || stage == TerminalSession.Stage.BODYCAM_VIEW) {
                session.keyPressed(key);
            } else {
                TerminalCamera.close();
            }
            return true;
        }
        session.keyPressed(key);
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        TerminalSession.get().charTyped(c);
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            TerminalCamera.close();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        TerminalCamera.renderPrompt(g);
    }
}
