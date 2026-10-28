package com.voltyx.efw.tech;

import com.mojang.blaze3d.systems.RenderSystem;
import com.voltyx.efw.EFW;
import com.voltyx.efw.client.dark.DarkGui;
import com.voltyx.efw.client.dark.DarkGuiFilter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The two charging slots of the electricity generator of Refurbished Furniture (the 1.12 generator of mwccf charged the devices and
 * the Li-ion batteries put into slots 1 and 2). The slots live in an extra container on the block entity (see the generator
 * mixins), a charge slot takes rechargeable batteries and devices with one inside.
 */
public final class ChargingSlots {
    public static final int COUNT = 2;
    /** Positions in the generator window: under the fuel slot. */
    public static final int X = 16;
    public static final int Y = 63;
    public static final int STEP = 18;

    private ChargingSlots() {
    }

    /** Implemented by the generator block entity through a mixin. */
    public interface Holder {
        SimpleContainer efw$chargeSlots();
    }

    public static final class ChargeSlot extends Slot {
        public ChargeSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return BatteryHelper.isChargeableItem(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPickup(Player player) {
            return true;
        }
    }

    /** Charges what is in the slots; true when something changed. */
    public static boolean charge(SimpleContainer slots, int amount) {
        boolean changed = false;
        for (int i = 0; i < slots.getContainerSize(); i++) {
            ItemStack stack = slots.getItem(i);
            if (!stack.isEmpty() && BatteryHelper.chargeItem(stack, amount)) {
                changed = true;
            }
        }
        return changed;
    }

    public static boolean hasWork(SimpleContainer slots) {
        for (int i = 0; i < slots.getContainerSize(); i++) {
            if (BatteryHelper.canCharge(slots.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    /** The items in the slots fall out when a player breaks the generator. */
    @Mod.EventBusSubscriber(modid = EFW.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onBreak(BlockEvent.BreakEvent event) {
            if (event.getLevel().isClientSide()) {
                return;
            }
            BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
            if (be instanceof Holder holder && event.getLevel() instanceof net.minecraft.world.level.Level level) {
                Containers.dropContents(level, event.getPos(), holder.efw$chargeSlots());
                holder.efw$chargeSlots().clearContent();
            }
        }
    }

    /**
     * Draws the frames of the two slots in the generator window in the colours of the screen (the dark theme turns the vanilla
     * greys of the window texture into dark ones, so the frames take the same shades), and a faint battery in each empty slot.
     */
    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private static final ResourceLocation ICON = new ResourceLocation(EFW.MOD_ID, "textures/gui/charging_slot.png");

        private ClientEvents() {
        }

        private static int shade(int vanilla) {
            return DarkGui.isEnabled() ? DarkGuiFilter.shade(vanilla) : 0xFF000000 | (vanilla << 16) | (vanilla << 8) | vanilla;
        }

        @SubscribeEvent
        public static void onBackground(ContainerScreenEvent.Render.Background event) {
            if (!event.getContainerScreen().getClass().getName().equals("com.mrcrayfish.furniture.refurbished.client.gui.screen.ElectricityGeneratorScreen")) {
                return;
            }
            int left = event.getContainerScreen().getGuiLeft();
            int top = event.getContainerScreen().getGuiTop();
            var g = event.getGuiGraphics();
            int edge = shade(0x37);
            int face = shade(0x8B);
            int light = shade(0xFF);
            for (Slot slot : event.getContainerScreen().getMenu().slots) {
                if (!(slot instanceof ChargeSlot)) {
                    continue;
                }
                int x = left + slot.x - 1;
                int y = top + slot.y - 1;
                g.fill(x, y, x + 18, y + 18, edge);
                g.fill(x + 1, y + 1, x + 18, y + 18, light);
                g.fill(x + 1, y + 1, x + 17, y + 17, face);
                if (!slot.hasItem()) {
                    RenderSystem.enableBlend();
                    g.setColor(1.0F, 1.0F, 1.0F, 0.45F);
                    g.blit(ICON, left + slot.x, top + slot.y, 0, 0, 16, 16, 16, 16);
                    g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                }
            }
        }
    }
}
