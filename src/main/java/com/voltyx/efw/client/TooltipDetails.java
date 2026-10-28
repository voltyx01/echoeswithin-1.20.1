package com.voltyx.efw.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.TaczCompat;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Secondary tooltip info only with Shift: the Curios slot line (moved from the top to the bottom, right above the [I] Inspect hint),
 * and most of the TACZ gun tooltip (TaczGunTooltipMixin keeps its damage block). Runs last, after every mod has added its lines.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class TooltipDetails {
    private TooltipDetails() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTooltip(ItemTooltipEvent event) {
        List<Component> tooltip = event.getToolTip();
        List<Component> slots = new ArrayList<>();
        tooltip.removeIf(line -> isKey(line, "curios.tooltip.slot") && slots.add(line));
        if (Screen.hasShiftDown()) {
            int inspect = tooltip.size();
            for (int i = 0; i < tooltip.size(); i++) {
                if (isKey(tooltip.get(i), "tooltip.efw.inspect")) {
                    inspect = i;
                    break;
                }
            }
            tooltip.addAll(inspect, slots);
            return;
        }
        if ((!slots.isEmpty() || TaczCompat.isGun(event.getItemStack())) && !hasShiftHint(tooltip)) {
            tooltip.add(Component.translatable("tooltip.efw.hold_shift"));
        }
    }

    /** The item already says Shift shows more (the ring, the doll, the bodycam have their own "hold Shift" line). */
    private static boolean hasShiftHint(List<Component> tooltip) {
        for (Component line : tooltip) {
            if (line.getContents() instanceof TranslatableContents t && t.getKey().endsWith("_shift")
                    || line.getString().toLowerCase(java.util.Locale.ROOT).contains("shift")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKey(Component line, String key) {
        return line.getContents() instanceof TranslatableContents t && t.getKey().equals(key);
    }
}
