package com.voltyx.efw.loot;

import com.mojang.logging.LogUtils;
import com.voltyx.efw.tech.TechNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Doomsday Functionality opens its props (cars, cabinets, crates, remains) from a packet of its own, not through block use:
 * DoomsdayOpenPacketMixin stops that packet at the loot roll while the prop is still to be searched and starts the search instead;
 * when the search ends the prop is opened here the way the packet does it (roll the loot, "Empty" or the window).
 */
public final class DoomsdayLoot {
    public static final String BLOCK_ENTITY = "com.raiiiden.doomsdayfunctionality.blockentity.DoomsdayBlockEntity";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Method tryLoadLoot;
    private static Method getLootHandler;
    private static boolean broken;

    private DoomsdayLoot() {
    }

    public static boolean is(BlockEntity be) {
        return be != null && be.getClass().getName().equals(BLOCK_ENTITY);
    }

    /** From the packet: a prop that is still to be searched starts the search. @return true when the packet must stop here. */
    public static boolean interceptOpen(ServerPlayer player, BlockPos pos) {
        if (!LootContainers.isWildAt(player.level(), pos)) {
            return false;
        }
        LootContainers.beginSearch(player, pos);
        TechNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TechNetwork.StartContainerLoot(pos, false));
        return true;
    }

    /** Opens a searched prop. @return false when this is not a Doomsday prop. */
    public static boolean open(ServerPlayer player, BlockEntity be, BlockPos pos) {
        if (!is(be) || !(be instanceof MenuProvider menu)) {
            return false;
        }
        if (!resolve(be.getClass())) {
            return true;
        }
        try {
            tryLoadLoot.invoke(be, player);
            ItemStackHandler items = (ItemStackHandler) getLootHandler.invoke(be);
            boolean empty = true;
            for (int i = 0; i < items.getSlots() && empty; i++) {
                empty = items.getStackInSlot(i).isEmpty();
            }
            if (empty) {
                player.displayClientMessage(Component.translatable("gui.efw.loot_empty"), true);
            } else {
                NetworkHooks.openScreen(player, menu, pos);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Could not open Doomsday container at {}", pos, e);
        }
        return true;
    }

    private static synchronized boolean resolve(Class<?> type) {
        if (tryLoadLoot != null || broken) {
            return !broken;
        }
        try {
            tryLoadLoot = type.getMethod("tryLoadLoot", Player.class);
            getLootHandler = type.getMethod("getLootHandler");
        } catch (NoSuchMethodException e) {
            broken = true;
            LOGGER.error("Doomsday Functionality changed, its containers cannot be opened after a search", e);
        }
        return !broken;
    }
}
