package com.voltyx.efw.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.sins.client.SinClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Curios and Cosmetic Armor replace the inventory with their own screen when they are expanded, and every button other mods put
 * on the inventory disappears. After such a screen has been initialised, a throw-away inventory screen is initialised with the
 * same size (so every mod's button handler runs for it) and its buttons are moved over, shifted by the difference of the two
 * windows. The buttons of the screen's own mod and the vanilla recipe book button stay out, so the Cosmetic Armor button remains on the
 * Curios screen and the Curios button on the Cosmetic Armor screen.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class InventoryMirror {
    private static boolean building;

    private InventoryMirror() {
    }

    /** The expanded screens of Curios (both layouts) and Cosmetic Armor Reworked. */
    public static boolean isMirrorTarget(Screen screen) {
        if (screen instanceof InventoryScreen) {
            return false;
        }
        String name = screen.getClass().getName();
        return name.startsWith("top.theillusivec4.curios.client.gui.CuriosScreen")
                || name.equals("lain.mods.cos.impl.client.gui.GuiCosArmorInventory");
    }

    /** A screen already has its own mod's buttons; the other mod's button (Curios <-> Cosmetic Armor) is what has to stay reachable. */
    private static boolean isOwnedByScreenMod(Object widget, Screen screen) {
        String name = widget.getClass().getName();
        boolean screenIsCurios = screen.getClass().getName().startsWith("top.theillusivec4.curios.");
        return widget instanceof SinClient.SevenButton
                || name.startsWith(screenIsCurios ? "top.theillusivec4.curios." : "lain.mods.cos.");
    }

    /** The expanded Curios screen is wider than the inventory, so the active effects list would sit on top of it. */
    @SubscribeEvent
    public static void onMobEffects(ScreenEvent.RenderInventoryMobEffects e) {
        if (isCuriosScreen(e.getScreen())) {
            e.setCanceled(true);
        }
    }

    /** The expanded Curios screen (both layouts). */
    public static boolean isCuriosScreen(Screen screen) {
        return screen != null && screen.getClass().getName().startsWith("top.theillusivec4.curios.client.gui.CuriosScreen");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenInit(ScreenEvent.Init.Post e) {
        if (building || !isMirrorTarget(e.getScreen()) || !(e.getScreen() instanceof AbstractContainerScreen<?> real)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        building = true;
        // the recipe book of the throw-away inventory screen makes its menu the player's open menu: put the real one back, or every
        // click in the screen is ignored as "in a mismatching container"
        AbstractContainerMenu openMenu = mc.player.containerMenu;
        try {
            InventoryScreen fake = new InventoryScreen(mc.player);
            fake.init(mc, real.width, real.height);
            int dx = real.getGuiLeft() - fake.getGuiLeft();
            int dy = real.getGuiTop() - fake.getGuiTop();
            List<GuiEventListener> moved = new ArrayList<>();
            for (GuiEventListener child : fake.children()) {
                if (!(child instanceof AbstractWidget widget) || isOwnedByScreenMod(widget, real) || isRecipeBookButton(widget, fake)) {
                    continue;
                }
                widget.setX(widget.getX() + dx);
                widget.setY(widget.getY() + dy);
                moved.add(widget);
            }
            for (GuiEventListener widget : moved) {
                e.addListener(widget);
            }
        } catch (Throwable t) {
            EFW.LOGGER.warn("Could not mirror the inventory buttons onto {}: {}", e.getScreen().getClass().getName(), t.toString());
        } finally {
            mc.player.containerMenu = openMenu;
            building = false;
        }
    }

    private static boolean isRecipeBookButton(AbstractWidget widget, InventoryScreen fake) {
        return widget.getClass() == ImageButton.class && widget.getX() == fake.getGuiLeft() + 104 && widget.getY() == fake.getGuiTop() + 61;
    }
}
