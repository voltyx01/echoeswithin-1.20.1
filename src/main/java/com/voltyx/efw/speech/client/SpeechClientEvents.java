package com.voltyx.efw.speech.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.speech.SpeechConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Chat overhaul on the client: the chat key opens the speech panel instead of the vanilla chat, chat messages of players are shown as
 * bubbles above the speakers (not in the chat), replicas appear above the hotbar, and the panel can slide out next to the inventory.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class SpeechClientEvents {
    private static final float INVENTORY_OPEN_MS = 180.0F;
    private static final float INVENTORY_CLOSE_MS = 160.0F;

    private static SpeechPanel inventoryPanel;
    private static boolean inventoryPanelOpen;
    private static long inventoryOpenAt;
    private static long inventoryClosingAt;

    private SpeechClientEvents() {
    }

    // ---- chat screen / chat messages --------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        Minecraft mc = Minecraft.getInstance();
        if (screen instanceof ChatScreen) {
            String initial = "";
            for (var child : event.getListenersList()) {
                if (child instanceof EditBox box) {
                    initial = box.getValue();
                    break;
                }
            }
            String text = initial;
            mc.tell(() -> {
                if (mc.screen == screen) {
                    mc.setScreen(new SpeechPanelScreen(text));
                }
            });
        } else if (screen instanceof InventoryScreen) {
            inventoryPanel = new SpeechPanel(screen, "");
            inventoryPanel.setFocused(false);
            inventoryPanelOpen = false;
            inventoryClosingAt = 0L;
        }
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof InventoryScreen) {
            inventoryPanel = null;
            inventoryPanelOpen = false;
            inventoryClosingAt = 0L;
        }
    }

    /** Messages of players are bubbles now; they do not go to the chat. */
    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        if (event.getBoundChatType() != null) {
            event.setCanceled(true);
        }
    }

    /** Output of commands and other system messages: shown as short lines at the lower left, as the chat panel hides the vanilla chat. */
    private static final java.util.ArrayDeque<Object[]> SYSTEM_LINES = new java.util.ArrayDeque<>();

    @SubscribeEvent
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        if (event.isOverlay() || event.getMessage() == null) {
            return;
        }
        SpeechClientManager.addSystemMessage(event.getMessage());
        SYSTEM_LINES.addLast(new Object[] {event.getMessage(), System.currentTimeMillis() + 7000L});
        while (SYSTEM_LINES.size() > 6) {
            SYSTEM_LINES.removeFirst();
        }
    }

    private static void renderSystemLines(GuiGraphics g, Minecraft mc) {
        if (mc.screen instanceof SpeechPanelScreen) {
            return; // the panel shows them in its history
        }
        long now = System.currentTimeMillis();
        SYSTEM_LINES.removeIf(e -> (long) e[1] < now);
        int y = mc.getWindow().getGuiScaledHeight() - 48;
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
        for (Object[] e : SYSTEM_LINES) {
            lines.addAll(mc.font.split((net.minecraft.network.chat.Component) e[0], 320));
        }
        y -= lines.size() * 10;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            g.fill(2, y - 1, 6 + mc.font.width(line), y + 9, 0x80000000);
            g.drawString(mc.font, line, 4, y, 0xFFFFFFFF, true);
            y += 10;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            SpeechClientManager.clearWorldState();
            return;
        }
        if (!mc.isPaused()) {
            SpeechClientManager.update();
        }
    }

    // ---- replica above the hotbar -----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || com.voltyx.efw.doll.client.DollClient.hidesHud() && !com.voltyx.efw.doll.client.DollClient.activating()) {
            return;
        }
        renderPersonalReplica(event.getGuiGraphics(), mc);
        renderSystemLines(event.getGuiGraphics(), mc);
    }

    public static void renderPersonalReplica(GuiGraphics g, Minecraft mc) {
        float replicaAlpha = SpeechClientManager.getPersonalAlpha();
        if (replicaAlpha < 0.04F) {
            return;
        }
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // the gradient is only for procedural calls (special replicas), never for ordinary chat, and only over an empty screen
        // bottom: it fades in as the auto-hiding HUD slides away and out as the HUD comes back
        float hudHidden = com.voltyx.efw.client.autohud.AutoHud.hiddenFactor();
        if (SpeechClientManager.isPersonalProcedural() && SpeechConfig.personalBottomGradient() && hudHidden > 0.01F) {
            int bottomAlpha = Math.max(0, Math.min(255, (int) (Math.min(1.0F, replicaAlpha * 0.88F) * hudHidden * 255.0F)));
            g.fillGradient(0, (int) (height * (2.0F / 3.0F)), width, height, 0x00000000, bottomAlpha << 24);
        }

        SpeechClientManager.TypedLine line = SpeechClientManager.getPersonalLine();
        if (line == null || line.getVisibleText().isEmpty()) {
            return;
        }
        Font font = mc.font;
        String visible = line.getVisibleText();
        String toRender = SpeechConfig.personalShowBrackets() ? "[ " + visible + " ]" : visible;
        int textWidth = font.width(toRender);
        int centerX = width / 2 + Math.round(SpeechConfig.personalXOffset());
        int y = (height - 50) + Math.round(SpeechConfig.personalYOffset());
        int a = Math.max(8, Math.min(255, (int) (replicaAlpha * 255.0F)));
        int color = (a << 24) | (SpeechConfig.personalTextColorRgb() & 0x00FFFFFF);

        float scale = Math.max(0.1F, SpeechConfig.personalScale());
        g.pose().pushPose();
        if (Math.abs(scale - 1.0F) > 0.001F) {
            g.pose().translate(centerX, y + 4, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.pose().translate(-centerX, -(y + 4), 0.0F);
        }
        g.drawString(font, toRender, centerX - textWidth / 2, y, color, true);
        g.pose().popPose();
    }

    // ---- panel next to the inventory --------------------------------------------------------------------------------

    private static boolean inventoryPanelActive() {
        return inventoryPanel != null && inventoryPanelOpen;
    }

    private static boolean inventoryPanelVisible() {
        if (inventoryPanelOpen) {
            return true;
        }
        if (inventoryClosingAt > 0L) {
            if (System.currentTimeMillis() - inventoryClosingAt < INVENTORY_CLOSE_MS) {
                return true;
            }
            inventoryClosingAt = 0L;
        }
        return false;
    }

    /**
     * How far the chat panel of the inventory is open, eased (0 closed .. 1 open): the chat button and the effect icons slide under
     * the inventory as far.
     */
    public static float inventoryChatSlide() {
        if (inventoryPanel == null || !inventoryPanelVisible()) {
            return 0.0F;
        }
        float p = inventoryOpenProgress();
        return 1.0F - (1.0F - p) * (1.0F - p);
    }

    private static float inventoryOpenProgress() {
        long now = System.currentTimeMillis();
        if (inventoryClosingAt > 0L) {
            return Math.max(0.0F, 1.0F - (now - inventoryClosingAt) / INVENTORY_CLOSE_MS);
        }
        return inventoryPanelOpen ? Math.max(0.0F, Math.min(1.0F, (now - inventoryOpenAt) / INVENTORY_OPEN_MS)) : 0.0F;
    }

    private static void openInventoryPanel(String text) {
        inventoryPanelOpen = true;
        inventoryClosingAt = 0L;
        inventoryOpenAt = System.currentTimeMillis();
        if (text != null && !text.isEmpty()) {
            inventoryPanel.insert(text);
        }
        inventoryPanel.setFocused(true);
    }

    private static void closeInventoryPanel() {
        if (inventoryPanelOpen) {
            inventoryPanelOpen = false;
            inventoryClosingAt = System.currentTimeMillis();
            inventoryPanel.setFocused(false);
        }
    }

    private static SpeechPanel.Layout inventoryLayout(InventoryScreen screen) {
        return SpeechPanel.inventoryLayout(screen.getGuiLeft(), screen.getGuiTop(), screen.getYSize());
    }

    private static int[] buttonBounds(InventoryScreen screen) {
        int x = Math.max(2, screen.getGuiLeft() - SpeechPanel.BUTTON_SIZE - 4);
        int y = screen.getGuiTop() + screen.getYSize() - SpeechPanel.BUTTON_SIZE - 4;
        return new int[] {x, y};
    }

    /**
     * The chat button, drawn right after the dim of the screen and before the inventory itself: sliding right it goes under the
     * inventory, which covers it (no scissor: it flushes the batch of the screen mid-frame and puts the text of the tooltips under
     * the things drawn after it).
     */
    @SubscribeEvent
    public static void onInventoryBackground(ScreenEvent.BackgroundRendered event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || inventoryPanel == null) {
            return;
        }
        float slide = inventoryChatSlide();
        if (slide >= 1.0F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        int[] b = buttonBounds(screen);
        int mouseX = (int) (mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth());
        int mouseY = (int) (mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight());
        int shift = Math.round((screen.getGuiLeft() - b[0]) * slide);
        boolean over = slide <= 0.0F && mouseX >= b[0] && mouseX < b[0] + SpeechPanel.BUTTON_SIZE
                && mouseY >= b[1] && mouseY < b[1] + SpeechPanel.BUTTON_SIZE;
        g.pose().pushPose();
        g.pose().translate(shift, 0.0F, 0.0F);
        g.fill(b[0], b[1], b[0] + SpeechPanel.BUTTON_SIZE, b[1] + SpeechPanel.BUTTON_SIZE, 0xFF000000);
        g.fill(b[0] + 1, b[1] + 1, b[0] + SpeechPanel.BUTTON_SIZE - 1, b[1] + SpeechPanel.BUTTON_SIZE - 1, over ? 0xFF4A4A4A : 0xFF2B2B2B);
        g.drawCenteredString(mc.font, Component.translatable("efw.speech.button"),
                b[0] + SpeechPanel.BUTTON_SIZE / 2, b[1] + 6, over ? 0xFFFFFFA0 : 0xFFE0E0E0);
        g.pose().popPose();
        // the text waits in a batch that is drawn at the end of the frame, over the inventory: draw it now, under what comes next
        g.flush();
    }

    @SubscribeEvent
    public static void onInventoryRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || inventoryPanel == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        if (inventoryPanelVisible()) {
            float eased = 1.0F - (1.0F - inventoryOpenProgress()) * (1.0F - inventoryOpenProgress());
            SpeechPanel.Layout layout = inventoryLayout(screen);
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 300.0F);
            inventoryPanel.render(g, layout, event.getMouseX(), event.getMouseY(), -Math.round(layout.width() * (1.0F - eased)));
            g.pose().popPose();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInventoryMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || inventoryPanel == null) {
            return;
        }
        int[] b = buttonBounds(screen);
        double mx = event.getMouseX();
        double my = event.getMouseY();
        // the button is under the inventory while the chat is open (Esc closes it)
        if (inventoryChatSlide() <= 0.0F && mx >= b[0] && mx < b[0] + SpeechPanel.BUTTON_SIZE && my >= b[1] && my < b[1] + SpeechPanel.BUTTON_SIZE) {
            if (inventoryPanelOpen) {
                closeInventoryPanel();
            } else {
                openInventoryPanel("");
            }
            event.setCanceled(true);
            return;
        }
        if (inventoryPanelActive() && inventoryPanel.mouseClicked(inventoryLayout(screen), mx, my, event.getButton())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInventoryMouseDrag(ScreenEvent.MouseDragged.Pre event) {
        if (event.getScreen() instanceof InventoryScreen screen && inventoryPanelActive()
                && inventoryPanel.mouseDragged(inventoryLayout(screen), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInventoryMouseRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getScreen() instanceof InventoryScreen && inventoryPanel != null) {
            inventoryPanel.stopDragging();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInventoryScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScreen() instanceof InventoryScreen screen && inventoryPanelActive()
                && inventoryPanel.mouseScrolled(inventoryLayout(screen), event.getMouseX(), event.getMouseY(), event.getScrollDelta())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInventoryKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen) || inventoryPanel == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int key = event.getKeyCode();
        boolean typing = inventoryPanelActive() && inventoryPanel.isFocused();
        if (!typing) {
            if (mc.options.keyChat.matches(key, event.getScanCode())) {
                openInventoryPanel("");
                event.setCanceled(true);
            } else if (mc.options.keyCommand.matches(key, event.getScanCode())) {
                openInventoryPanel("/");
                event.setCanceled(true);
            }
            return;
        }
        // while typing, no key reaches the inventory (hotbar swaps, the close key, other hotkeys)
        event.setCanceled(true);
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            closeInventoryPanel();
        } else if (SpeechPanel.isEnter(key)) {
            inventoryPanel.send();
        } else {
            inventoryPanel.keyPressed(key, event.getScanCode(), event.getModifiers());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInventoryChar(ScreenEvent.CharacterTyped.Pre event) {
        if (event.getScreen() instanceof InventoryScreen && inventoryPanelActive() && inventoryPanel.isFocused()) {
            inventoryPanel.charTyped(event.getCodePoint(), event.getModifiers());
            event.setCanceled(true);
        }
    }
}
