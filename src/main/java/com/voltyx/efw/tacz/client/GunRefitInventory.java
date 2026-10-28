package com.voltyx.efw.tacz.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.TaczCompat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * X over a gun in an inventory opens the modification screen for that gun. The gun is not taken into the hand: the screen
 * addresses it by its inventory slot and draws it itself. When the screen is closed, the inventory it was opened from comes back.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class GunRefitInventory {
    public static final KeyMapping REFIT = new KeyMapping("key.efw.gun_refit", KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X,
            "key.categories.efw");

    /** The screen the gun was taken from: it is opened again when the modification screen closes. */
    private static AbstractContainerScreen<?> originScreen;

    private GunRefitInventory() {
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(REFIT);
        }
    }

    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (!TaczCompat.isLoaded() || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || !REFIT.matches(event.getKeyCode(), event.getScanCode())) {
            return;
        }
        if (screen.getFocused() instanceof EditBox box && box.canConsumeInput()) {
            return; // typing in a search field
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Slot slot = screen.getSlotUnderMouse();
        if (player == null || slot == null || !(slot.container instanceof Inventory) || !slot.hasItem() || !screen.getMenu().getCarried().isEmpty()) {
            return;
        }
        ItemStack gun = slot.getItem();
        if (!TaczCompat.isGun(gun) || TaczCompat.hasAttachmentLock(gun)) {
            return;
        }
        int invSlot = slot.getContainerSlot();
        if (invSlot < 0 || invSlot >= 36) {
            return;
        }
        EFW.LOGGER.info("Modification screen: gun {} from inventory slot {} (selected hotbar slot {})", gun.getItem(), invSlot, player.getInventory().selected);
        originScreen = screen;
        event.setCanceled(true);
        mc.setScreen(TaczCompat.newRefitScreen(invSlot));
    }

    /** The screen the modification screen was opened from, once: closing goes straight back to it, so the cursor stays where it is. */
    static AbstractContainerScreen<?> takeOrigin() {
        AbstractContainerScreen<?> origin = originScreen;
        originScreen = null;
        return origin;
    }

    /** The modification screen was left some other way (death, disconnect): nothing to come back to. */
    @SubscribeEvent
    public static void onClosing(ScreenEvent.Closing event) {
        if (TaczCompat.isRefitScreen(event.getScreen())) {
            originScreen = null;
        }
    }
}
