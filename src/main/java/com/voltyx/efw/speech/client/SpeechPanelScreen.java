package com.voltyx.efw.speech.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Replaces the vanilla chat screen: the panel slides in from the left edge and slides out when closed. */
public class SpeechPanelScreen extends Screen {
    private static final float OPEN_MS = 180.0F;
    private static final float CLOSE_MS = 160.0F;

    private final String initialText;
    private SpeechPanel panel;
    private final long openedAt = System.currentTimeMillis();
    private long closingAt;

    public SpeechPanelScreen(String initialText) {
        super(Component.empty());
        this.initialText = initialText == null ? "" : initialText;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void startClosing() {
        if (closingAt == 0L) {
            closingAt = System.currentTimeMillis();
            panel.setFocused(false);
        }
    }

    @Override
    protected void init() {
        if (panel == null) {
            panel = new SpeechPanel(this, initialText);
        }
        panel.setFocused(closingAt == 0L);
    }

    @Override
    public void tick() {
        if (closingAt > 0L && System.currentTimeMillis() - closingAt >= CLOSE_MS) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float progress = Math.min(1.0F, (System.currentTimeMillis() - openedAt) / OPEN_MS);
        if (closingAt > 0L) {
            progress = 1.0F - Math.min(1.0F, (System.currentTimeMillis() - closingAt) / CLOSE_MS);
        }
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
        int slideOffset = -Math.round((width - 8.0F) * (1.0F - eased));
        panel.render(g, SpeechPanel.screenLayout(width, height), mouseX, mouseY, slideOffset);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closingAt > 0L) {
            return true;
        }
        if (panel.mouseClicked(SpeechPanel.screenLayout(width, height), mouseX, mouseY, button)) {
            return true;
        }
        startClosing();
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        return panel.mouseDragged(SpeechPanel.screenLayout(width, height), mouseY) || super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        panel.stopDragging();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return panel.mouseScrolled(SpeechPanel.screenLayout(width, height), mouseX, mouseY, delta) || super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (closingAt > 0L) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            startClosing();
            return true;
        }
        if (SpeechPanel.isEnter(key) && panel.isFocused()) {
            panel.send(); // stays open: the output of a command is in the history and its links can be clicked
            return true;
        }
        if (panel.keyPressed(key, scanCode, modifiers)) {
            return true;
        }
        if (!panel.isFocused() && Minecraft.getInstance().options.keyChat.matches(key, scanCode)) {
            panel.setFocused(true);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return closingAt == 0L && panel.charTyped(codePoint, modifiers);
    }

    /** A clicked link with a suggested command (the coordinates of /locate) puts it into the input line, as the vanilla chat does. */
    @Override
    protected void insertText(String text, boolean overwrite) {
        if (panel != null) {
            panel.insert(text);
        }
    }
}
