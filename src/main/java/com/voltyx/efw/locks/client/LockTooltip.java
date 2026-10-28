package com.voltyx.efw.locks.client;

import melonslise.locks.client.event.LocksClientForgeEvents;
import melonslise.locks.client.util.LocksClientUtil;
import melonslise.locks.common.util.Lockable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.List;

/**
 * The tooltip of the lock looked at with a pick in hand, in place of the Locks Reforged one: only the lock's name and how hard it is to
 * pick (by its pin count: wood 5, copper 6, iron 7, steel 9), drawn as a normal tooltip so the dark GUI theme colours it. Runs before
 * Locks' own overlay handler and clears the lock it found, so that one draws nothing. Registered only with Locks installed.
 */
public final class LockTooltip {
    private LockTooltip() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        if (e.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        Lockable lockable = LocksClientForgeEvents.tooltipLockable;
        if (lockable == null) {
            return;
        }
        LocksClientForgeEvents.tooltipLockable = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !LocksClientForgeEvents.holdingPick(mc.player)) {
            return;
        }
        Lockable.State state = lockable.getLockState(mc.level);
        if (state == null) {
            return;
        }
        Vector3f at = LocksClientUtil.worldToScreen(state.pos, e.getPartialTick());
        if (at.z >= 0.0f) {
            return; // behind the camera
        }
        List<Component> lines = List.of(lockable.stack.getHoverName(),
                Component.translatable("tooltip.efw.lock_difficulty", Component.translatable(difficulty(lockable.lock.getLength()))));
        e.getGuiGraphics().renderComponentTooltip(mc.font, lines, (int) at.x, (int) at.y);
    }

    private static String difficulty(int pins) {
        if (pins <= 5) {
            return "tooltip.efw.lock_difficulty.easy";
        }
        if (pins == 6) {
            return "tooltip.efw.lock_difficulty.medium";
        }
        return pins <= 8 ? "tooltip.efw.lock_difficulty.hard" : "tooltip.efw.lock_difficulty.very_hard";
    }
}
