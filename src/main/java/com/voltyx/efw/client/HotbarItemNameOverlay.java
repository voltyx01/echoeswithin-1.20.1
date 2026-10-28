package com.voltyx.efw.client;

import com.voltyx.efw.EFW;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * No item name above the hotbar: Forge draws it as its own overlay, which is cancelled here (the Gui mixin alone does not reach it).
 * No status effect icons on the HUD either (they stay in the inventory). Stylish Effects, which replaces both, has its HUD
 * renderer set to NONE in its config; this covers the vanilla icons and mods that draw through the same overlay.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class HotbarItemNameOverlay {
    private HotbarItemNameOverlay() {
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.ITEM_NAME.id()) || id.equals(VanillaGuiOverlay.POTION_ICONS.id())) {
            event.setCanceled(true);
        }
    }
}
