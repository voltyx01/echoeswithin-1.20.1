package com.voltyx.efw.speech.client;

import com.voltyx.efw.speech.SpeechConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The panel with the history of nearby messages and the input line (mwccf SpeechPanelUi + SpeechPanelController): it replaces the chat
 * screen and also slides out next to the inventory. The command suggestions and the history of sent messages are the vanilla ones.
 */
public final class SpeechPanel {
    public static final int BUTTON_SIZE = 20;
    private static final int PANEL_WIDTH = 220;
    private static final int INVENTORY_PANEL_WIDTH = 320;
    private static final int PADDING = 7;
    private static final int LINE_HEIGHT = 11;

    public record Layout(int x, int y, int width, int height) {
        public int historyY() {
            return y + 23;
        }

        public int inputY() {
            return y + height - 23;
        }

        public boolean contains(double mx, double my) {
            return mx >= x && mx <= x + width && my >= y && my <= y + height;
        }
    }

    private record Row(FormattedCharSequence text, int indent, int color, boolean system) {
    }

    private final Screen host;
    private final EditBox input;
    private final CommandSuggestions suggestions;
    private int scroll;
    private boolean dragging;
    private boolean focused = true;
    private int historyIndex = -1;
    private String historyDraft = "";
    private boolean navigating;

    public SpeechPanel(Screen host, String initialText) {
        this.host = host;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        this.input = new EditBox(font, 0, 0, 100, 12, Component.empty());
        this.input.setBordered(false);
        this.input.setMaxLength(SpeechConfig.maxMessageLength());
        this.input.setCanLoseFocus(false);
        this.input.setTextColor(0xFFFFFF);
        this.suggestions = new CommandSuggestions(mc, host, this.input, font, false, false, 1, 7, true, 0xD0000000);
        this.suggestions.setAllowSuggestions(true);
        this.input.setResponder(s -> {
            // a message taken from the sent history shows no suggestions, so the arrows keep going through the history (vanilla)
            this.suggestions.setAllowSuggestions(!this.navigating);
            this.suggestions.updateCommandInfo();
        });
        if (initialText != null && !initialText.isEmpty()) {
            this.input.setValue(initialText);
            this.input.moveCursorToEnd();
        }
        this.suggestions.updateCommandInfo();
        setFocused(true);
    }

    // ---- layouts ----------------------------------------------------------------------------------------------------

    /** Beside the inventory window. */
    /** Left of the inventory; the chat button and the effect icons slide under the inventory meanwhile, so it takes all that room. */
    public static Layout inventoryLayout(int guiLeft, int guiTop, int guiHeight) {
        int width = Math.max(40, Math.min(INVENTORY_PANEL_WIDTH, guiLeft - 10));
        int x = Math.max(4, guiLeft - width - 6);
        int y = Math.max(4, guiTop);
        int height = Math.max(100, Math.min(guiHeight, 230));
        return new Layout(x, y, width, height);
    }

    /** In the left part of the screen. */
    public static Layout screenLayout(int screenWidth, int screenHeight) {
        int width = Math.min(PANEL_WIDTH, screenWidth - 16);
        int height = Math.min(230, screenHeight - 24);
        return new Layout(8, Math.max(8, (screenHeight - height) / 2), width, height);
    }

    // ---- state ------------------------------------------------------------------------------------------------------

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean value) {
        this.focused = value;
        this.input.setFocused(value);
    }

    public void insert(String text) {
        this.input.setValue(text);
        this.input.moveCursorToEnd();
        setFocused(true);
    }

    public void stopDragging() {
        dragging = false;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    public void render(GuiGraphics g, Layout layout, int mouseX, int mouseY, int slideOffset) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        g.pose().pushPose();
        g.pose().translate(slideOffset, 0.0F, 0.0F);
        int mx = mouseX - slideOffset;

        g.fill(layout.x, layout.y, layout.x + layout.width, layout.y + layout.height, 0xE8141A20);
        g.fill(layout.x, layout.y, layout.x + layout.width, layout.y + 1, 0xFFE0B84F);
        g.drawString(font, fit(font, "efw.speech.history", layout.width - PADDING * 2), layout.x + PADDING, layout.y + 7, 0xFFFFD66B, true);

        int historyBottom = layout.inputY() - 3;
        g.fill(layout.x + 3, layout.historyY(), layout.x + layout.width - 3, historyBottom, 0x7020272E);
        List<Row> rows = flattenHistory(font, layout.width - PADDING * 2 - 5);
        int visibleRows = Math.max(1, (historyBottom - layout.historyY() - 4) / LINE_HEIGHT);
        int maxScroll = Math.max(0, rows.size() - visibleRows);
        int clampedScroll = Math.min(maxScroll, scroll);
        int end = Math.max(0, rows.size() - clampedScroll);
        int start = Math.max(0, end - visibleRows);
        int textY = historyBottom - 4 - (end - start) * LINE_HEIGHT;
        for (int i = start; i < end; i++) {
            Row row = rows.get(i);
            g.drawString(font, row.text, layout.x + PADDING + row.indent, textY, row.color, false);
            textY += LINE_HEIGHT;
        }
        Style hovered = styleAt(font, rows, layout, mx, mouseY);
        if (hovered != null) {
            g.renderComponentHoverEffect(font, hovered, mx, mouseY);
        }
        if (rows.isEmpty()) {
            g.drawString(font, fit(font, "efw.speech.empty", layout.width - PADDING * 2 - 5), layout.x + PADDING, layout.historyY() + 7, 0xFFB7BEC7, true);
        }

        int trackX = layout.x + layout.width - 4;
        g.fill(trackX, layout.historyY() + 2, trackX + 2, historyBottom - 2, 0x704A555F);
        if (rows.size() > visibleRows) {
            int trackHeight = historyBottom - layout.historyY() - 4;
            int thumbHeight = Math.max(12, trackHeight * visibleRows / rows.size());
            int thumbTravel = trackHeight - thumbHeight;
            int thumbY = layout.historyY() + 2 + (maxScroll == 0 ? 0 : thumbTravel * (maxScroll - clampedScroll) / maxScroll);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFFE0B84F);
        }

        g.fill(layout.x + 3, layout.inputY(), layout.x + layout.width - 3, layout.y + layout.height - 3, 0xFF0D1116);
        g.drawString(font, ">", layout.x + PADDING, layout.inputY() + 7, 0xFFFFD66B, true);
        if (input.getValue().isEmpty() && !focused) {
            g.drawString(font, Component.translatable("efw.speech.input"), layout.x + PADDING + 10, layout.inputY() + 7, 0xFF8D959E, true);
        }
        input.setX(layout.x + PADDING + 10);
        input.setY(layout.inputY() + 6);
        input.setWidth(layout.width - PADDING * 2 - 17);
        input.render(g, mx, mouseY, 0.0F);
        if (focused) {
            // the vanilla list sits above the bottom edge of the screen; lift or lower it to just above the input line
            int shift = suggestionShift();
            g.pose().pushPose();
            g.pose().translate(0.0F, shift, 0.0F);
            suggestions.render(g, mx, mouseY - shift);
            g.pose().popPose();
        }
        g.pose().popPose();
    }

    /** The full text when it fits the width, else the short one (without "nearby"). */
    private static Component fit(Font font, String key, int width) {
        Component full = Component.translatable(key);
        return font.width(full) <= width ? full : Component.translatable(key + ".short");
    }

    private int suggestionShift() {
        return (input.getY() - 3) - (host.height - 15);
    }

    private List<Row> flattenHistory(Font font, int maxWidth) {
        List<Row> rows = new ArrayList<>();
        for (SpeechClientManager.HistoryMessage message : SpeechClientManager.getHistory()) {
            if (message.system() != null) {
                List<FormattedCharSequence> parts = font.split(message.system(), Math.max(20, maxWidth));
                for (int i = 0; i < parts.size(); i++) {
                    rows.add(new Row(parts.get(i), i == 0 ? 0 : 6, 0xFFB7E3FF, true));
                }
                continue;
            }
            Component line = Component.literal(message.speakerName()).withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(message.text()));
            List<FormattedCharSequence> parts = font.split(line, Math.max(20, maxWidth));
            for (int i = 0; i < parts.size(); i++) {
                rows.add(new Row(parts.get(i), i == 0 ? 0 : 6, i == 0 ? 0xFFFFFFFF : 0xFFDDDDDD, false));
            }
        }
        return rows;
    }

    /** The style of the history text under the mouse (links of command outputs), or null. */
    private Style styleAt(Font font, List<Row> rows, Layout layout, double mouseX, double mouseY) {
        int historyBottom = layout.inputY() - 3;
        if (mouseY < layout.historyY() || mouseY >= historyBottom - 4 || mouseX < layout.x || mouseX > layout.x + layout.width) {
            return null;
        }
        int visibleRows = Math.max(1, (historyBottom - layout.historyY() - 4) / LINE_HEIGHT);
        int maxScroll = Math.max(0, rows.size() - visibleRows);
        int end = Math.max(0, rows.size() - Math.min(maxScroll, scroll));
        int start = Math.max(0, end - visibleRows);
        int textY = historyBottom - 4 - (end - start) * LINE_HEIGHT;
        int index = start + (int) Math.floor((mouseY - textY) / (double) LINE_HEIGHT);
        if (mouseY < textY || index < start || index >= end) {
            return null;
        }
        Row row = rows.get(index);
        if (!row.system) {
            return null;
        }
        return font.getSplitter().componentStyleAtWidth(row.text, (int) (mouseX - (layout.x + PADDING + row.indent)));
    }

    // ---- input ------------------------------------------------------------------------------------------------------

    public boolean mouseClicked(Layout layout, double mouseX, double mouseY, int button) {
        if (focused && suggestions.mouseClicked(mouseX, mouseY - suggestionShift(), button)) {
            return true;
        }
        if (button == 0 && hitScrollbar(layout, mouseX, mouseY)) {
            dragging = true;
            scrollTo(layout, (int) mouseY);
            return true;
        }
        if (button == 0 && layout.contains(mouseX, mouseY)) {
            Font font = Minecraft.getInstance().font;
            Style style = styleAt(font, flattenHistory(font, layout.width - PADDING * 2 - 5), layout, mouseX, mouseY);
            if (style != null && host.handleComponentClicked(style)) {
                return true;
            }
        }
        if (layout.contains(mouseX, mouseY)) {
            setFocused(mouseY >= layout.inputY());
            if (focused) {
                input.mouseClicked(mouseX, mouseY, button);
            }
            return true;
        }
        return false;
    }

    public boolean mouseDragged(Layout layout, double mouseY) {
        if (!dragging) {
            return false;
        }
        scrollTo(layout, (int) mouseY);
        return true;
    }

    public boolean mouseScrolled(Layout layout, double mouseX, double mouseY, double delta) {
        if (delta == 0.0) {
            return false;
        }
        if (focused && suggestions.mouseScrolled(Math.max(-1.0, Math.min(1.0, delta)))) {
            return true;
        }
        if (mouseX < layout.x || mouseX > layout.x + layout.width || mouseY < layout.historyY() || mouseY >= layout.inputY()) {
            return false;
        }
        setScroll(scroll + (delta > 0 ? 3 : -3));
        return true;
    }

    /** True when the key was used by the panel. */
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!focused) {
            return false;
        }
        if (suggestions.keyPressed(key, scanCode, modifiers)) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            return true; // the owner decides what follows a sent message (see send())
        }
        if (key == GLFW.GLFW_KEY_UP) {
            navigateSent(-1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            navigateSent(1);
            return true;
        }
        return input.keyPressed(key, scanCode, modifiers);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return focused && input.charTyped(codePoint, modifiers);
    }

    public static boolean isEnter(int key) {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
    }

    /** Sends the typed message or command (as the vanilla chat does); returns true when it was a command. */
    public boolean send() {
        Minecraft mc = Minecraft.getInstance();
        String message = input.getValue().trim();
        boolean command = message.startsWith("/");
        if (mc.player != null && !message.isEmpty()) {
            mc.gui.getChat().addRecentChat(message);
            if (command) {
                mc.player.connection.sendCommand(message.substring(1));
            } else {
                mc.player.connection.sendChat(message);
            }
        }
        input.setValue("");
        historyIndex = -1;
        historyDraft = "";
        scroll = 0;
        setFocused(true);
        return command;
    }

    private void navigateSent(int direction) {
        List<String> sent = new ArrayList<>(Minecraft.getInstance().gui.getChat().getRecentChat());
        if (sent.isEmpty()) {
            return;
        }
        if (historyIndex < 0) {
            historyDraft = input.getValue();
            historyIndex = sent.size();
        }
        historyIndex = Math.max(0, Math.min(sent.size(), historyIndex + direction));
        navigating = true;
        input.setValue(historyIndex == sent.size() ? historyDraft : sent.get(historyIndex));
        navigating = false;
        input.moveCursorToEnd();
        suggestions.hide();
    }

    // ---- scrollbar --------------------------------------------------------------------------------------------------

    private void setScroll(int value) {
        int max = SpeechClientManager.getHistory().size() * Math.max(1, SpeechConfig.maxMessageLength() / 10);
        scroll = Math.max(0, Math.min(max, value));
    }

    private boolean hitScrollbar(Layout layout, double mouseX, double mouseY) {
        int historyBottom = layout.inputY() - 3;
        int trackX = layout.x + layout.width - 6;
        return mouseX >= trackX && mouseX <= trackX + 5 && mouseY >= layout.historyY() && mouseY < historyBottom;
    }

    private void scrollTo(Layout layout, int mouseY) {
        Font font = Minecraft.getInstance().font;
        List<Row> rows = flattenHistory(font, layout.width - PADDING * 2 - 5);
        int historyBottom = layout.inputY() - 3;
        int visibleRows = Math.max(1, (historyBottom - layout.historyY() - 4) / LINE_HEIGHT);
        int maxScroll = Math.max(0, rows.size() - visibleRows);
        int trackTop = layout.historyY() + 2;
        int trackHeight = historyBottom - layout.historyY() - 4;
        int thumbHeight = rows.size() <= visibleRows ? trackHeight : Math.max(12, trackHeight * visibleRows / rows.size());
        int travel = Math.max(1, trackHeight - thumbHeight);
        float fraction = Math.max(0.0F, Math.min(1.0F, (mouseY - trackTop - thumbHeight / 2.0F) / travel));
        setScroll(Math.round((1.0F - fraction) * maxScroll));
    }

    public Screen host() {
        return host;
    }
}
