package com.voltyx.efw.sins.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.voltyx.efw.EFW;
import com.voltyx.efw.sins.SinContent;
import com.voltyx.efw.sins.SinData;
import com.voltyx.efw.sins.SevenConfig;
import com.voltyx.efw.sins.SinNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Client side of SEVEN: packet handlers, the inventory button and studying manuals with X. */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class SinClient {
    private static final ResourceLocation BUTTON_TEXTURE = new ResourceLocation(EFW.MOD_ID, "textures/gui/wardrobe_button.png");

    static {
        // the heartbeat lives here: SEVEN reads it for pulse conditions and moves it with pulse.shift
        com.voltyx.efw.sins.stat.SinRuntime.localPulse = p -> com.voltyx.efw.heartbeat.HeartbeatManager.currentBPM;
        com.voltyx.efw.sins.stat.SinStats.pulseShift = d -> com.voltyx.efw.heartbeat.HeartbeatManager.currentBPM =
                Math.max(58.0f, Math.min(com.voltyx.efw.heartbeat.HeartbeatManager.MAX_BPM,
                        com.voltyx.efw.heartbeat.HeartbeatManager.currentBPM + d));
    }

    private SinClient() {
    }

    /** A trigger fired on the server: the same windows open here (and the pulse moves). */
    public static void triggerFired(com.voltyx.efw.sins.stat.Trigger trigger) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            com.voltyx.efw.sins.stat.SinStats.fire(mc.player, trigger);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) {
        com.voltyx.efw.sins.stat.SinRuntime.clearClient();
    }

    public static void receiveData(CompoundTag nbt) {
        Minecraft mc = Minecraft.getInstance();
        SinData data = mc.player == null ? null : SinData.get(mc.player);
        if (data != null && nbt != null) {
            data.deserializeNBT(nbt);
            if (mc.screen instanceof SevenScreen seven) {
                seven.onDataSynced();
            }
        }
    }

    public static void receiveCards(List<String> cards) {
        if (Minecraft.getInstance().screen instanceof SevenScreen seven) {
            seven.receiveLevelUpCards(cards);
        }
    }

    public static void open(net.minecraft.client.gui.screens.Screen parent) {
        ScreenTransition.to(new SevenScreen(parent));
    }

    /** The Form tab (gender and body settings) of the SEVEN screen, straight from the game. */
    public static void openForm() {
        SevenScreen screen = new SevenScreen(null);
        screen.setTab(SevenScreen.TAB_APPEARANCE);
        ScreenTransition.to(screen);
    }

    /** The small button at the top of the player model in the inventory (1.12 GuiWardrobeButton). */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post e) {
        if (e.getScreen() instanceof AbstractContainerScreen<?> screen
                && (screen instanceof InventoryScreen || com.voltyx.efw.client.InventoryMirror.isMirrorTarget(screen))) {
            e.addListener(new SevenButton(screen));
        }
    }

    /** X over a manual in the player's inventory studies it. */
    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre e) {
        if (e.getKeyCode() != GLFW.GLFW_KEY_X || !(e.getScreen() instanceof AbstractContainerScreen<?> screen)
                || com.voltyx.efw.notes.client.NotesClient.typing(screen)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Slot slot = screen.getSlotUnderMouse();
        if (mc.player == null || slot == null || slot.container != mc.player.getInventory()) {
            return;
        }
        ItemStack stack = slot.getItem();
        if (stack.getItem() instanceof SinContent.ManualItem manual) {
            SinNetwork.sendToServer(new SinNetwork.UseManual(manual.type.categoryIndex));
            e.setCanceled(true);
        }
    }

    /** The "Character" button; also shown on the expanded Curios / Cosmetic Armor screens. */
    public static final class SevenButton extends AbstractButton {
        private final AbstractContainerScreen<?> screen;

        SevenButton(AbstractContainerScreen<?> screen) {
            super(screen.getGuiLeft() + SevenConfig.BUTTON_X.get(), screen.getGuiTop() + SevenConfig.BUTTON_Y.get(), 10, 10, Component.empty());
            this.screen = screen;
        }

        @Override
        public void onPress() {
            open(screen);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            // follow the recipe book shifting the inventory
            setX(screen.getGuiLeft() + SevenConfig.BUTTON_X.get());
            setY(screen.getGuiTop() + SevenConfig.BUTTON_Y.get());
            isHovered = mouseX >= getX() && mouseY >= getY() && mouseX < getX() + width && mouseY < getY() + height;
            RenderSystem.enableBlend();
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.blit(BUTTON_TEXTURE, getX(), getY(), isHovered ? 10 : 0, 0, width, height, 20, 10);
            g.pose().popPose();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
