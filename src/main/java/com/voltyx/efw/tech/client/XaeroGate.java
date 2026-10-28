package com.voltyx.efw.tech.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.tech.PortableMapItem;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Xaero's minimap and world map only work with a charged portable map in a Curios slot (mwccf MapDeviceState and
 * XaeroGuiBlockerHandler). The hud, the key handling and the waypoints are switched off by the Xaero*Mixin classes; here the
 * Xaero screens (world map, waypoint lists, settings) are refused in the world and closed when the charge runs out.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class XaeroGate {
    private XaeroGate() {
    }

    /** The local player carries a map with charge. */
    public static boolean hasActiveMap() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && PortableMapItem.hasActiveMap(mc.player);
    }

    /** Xaero is in use only while the player is in a world. */
    private static boolean inWorld() {
        return Minecraft.getInstance().player != null;
    }

    private static boolean isXaero(Object screen) {
        return screen != null && screen.getClass().getName().startsWith("xaero.");
    }

    @SubscribeEvent
    public static void onOpening(ScreenEvent.Opening event) {
        if (inWorld() && isXaero(event.getNewScreen()) && !hasActiveMap()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            Minecraft mc = Minecraft.getInstance();
            if (inWorld() && isXaero(mc.screen) && !hasActiveMap()) {
                mc.setScreen(null);
            }
        }
    }
}
