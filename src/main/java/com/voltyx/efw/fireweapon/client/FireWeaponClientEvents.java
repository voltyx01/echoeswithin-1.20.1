package com.voltyx.efw.fireweapon.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.fireweapon.FireWeaponActions;
import com.voltyx.efw.fireweapon.FireWeaponEvents;
import com.voltyx.efw.fireweapon.FireWeaponHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Client side of the fire weapons: clicks in inventories, strike sparks and the tooltips. */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class FireWeaponClientEvents {
    private static int swallowRelease = -1;

    private FireWeaponClientEvents() {
    }

    /** Cloth, flint or alcohol on the cursor clicked onto a weapon: the server wraps, lights or soaks it. */
    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> container) || event.getButton() > 1) {
            return;
        }
        Slot slot = container.getSlotUnderMouse();
        ItemStack carried = container.getMenu().getCarried();
        if (slot == null || !slot.hasItem() || carried.isEmpty() || !FireWeaponActions.canAffect(slot.getItem(), carried)) {
            return;
        }
        int invSlot = -1;
        if (screen instanceof CreativeModeInventoryScreen) {
            // the creative screen lists items too: only slots of the player's own inventory can be weapons to dress
            if (!(slot.container instanceof Inventory)) {
                return;
            }
            invSlot = slot.getContainerSlot();
        }
        FireWeaponEvents.sendSlotClick(container.getMenu().containerId, slot.index, invSlot, carried);
        event.setCanceled(true);
        swallowRelease = event.getButton();
    }

    /** The press was taken, so its release must not reach the screen: it would put the cursor stack into the slot (swap). */
    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (swallowRelease >= 0 && event.getButton() == swallowRelease) {
            swallowRelease = -1;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        swallowRelease = -1;
    }

    /** Strike with an ignited weapon: a burst of sparks flying off the target and a hiss. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!FireWeaponHelper.isIgnited(stack)) {
            return;
        }
        Entity target = event.getTarget();
        if (target != null) {
            Vec3 eye = player.getEyePosition();
            Vec3 impact = target.position().add(0, target.getBbHeight() * 0.5, 0);
            Vec3 dir = impact.subtract(eye);
            if (dir.lengthSqr() > 1.0E-4) {
                dir = dir.normalize();
                boolean soaked = FireWeaponHelper.isSoaked(stack);
                FireSparks.impactBurst(impact, (float) dir.x, (float) dir.y, (float) dir.z, soaked ? 70 : 45, soaked ? 3.5F : 2.5F);
            }
        }
        player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.45F, 1.6F, false);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> tip = event.getToolTip();
        if (stack.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        boolean shift = Screen.hasShiftDown();

        if (FireWeaponHelper.isCloth(stack)) {
            tip.add(Component.translatable("tooltip.efw.fireweapon.cloth_hint"));
            if (shift) {
                tip.add(Component.translatable("tooltip.efw.fireweapon.cloth_instructions"));
            }
            return;
        }
        if (!FireWeaponHelper.isWeapon(stack)) {
            return;
        }
        if (FireWeaponHelper.isIgnited(stack)) {
            boolean soaked = FireWeaponHelper.isSoaked(stack);
            tip.add(Component.translatable(soaked ? "tooltip.efw.fireweapon.soaked" : "tooltip.efw.fireweapon.burning",
                    "§7" + Math.round(FireWeaponHelper.getFireDamageBonus(stack)))); // an argument does not take the colour before it
            if (soaked) {
                float prog = FireWeaponHelper.getBurnProgress(stack, now);
                int sec = FireWeaponHelper.getRemainingSeconds(stack, now);
                tip.add(Component.literal(bar("§c", (int) Math.ceil((1.0F - prog) * 10))
                        + " " + String.format("§e%d:%02d", sec / 60, sec % 60)));
            } else {
                int pct = FireWeaponHelper.getClothPct(stack, now);
                tip.add(Component.literal(bar("§e", (int) Math.ceil(pct / 10.0F)) + " §f" + pct + "%"));
            }
        } else if (FireWeaponHelper.isInCharPhase(stack)) {
            tip.add(Component.translatable("tooltip.efw.fireweapon.charred"));
        } else if (FireWeaponHelper.isSoakedReady(stack)) {
            tip.add(Component.translatable("tooltip.efw.fireweapon.soaked_ready"));
        } else if (FireWeaponHelper.isWrapped(stack)) {
            int pct = FireWeaponHelper.getClothPct(stack, now);
            tip.add(pct < 100 ? Component.translatable("tooltip.efw.fireweapon.wrapped_percent", "§8" + pct)
                    : Component.translatable("tooltip.efw.fireweapon.wrapped"));
        }
        if (FireWeaponHelper.hasVisibleCloth(stack)) {
            tip.add(Component.translatable("tooltip.efw.fireweapon.hint"));
            if (shift) {
                tip.add(Component.translatable("tooltip.efw.fireweapon.instructions"));
                tip.add(Component.translatable("tooltip.efw.fireweapon.enhancers"));
            }
        }
    }

    private static String bar(String color, int filled) {
        filled = Math.max(1, Math.min(10, filled));
        StringBuilder b = new StringBuilder(color);
        for (int i = 0; i < 10; i++) {
            if (i == filled) {
                b.append("§8");
            }
            b.append('■');
        }
        return b.toString();
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onReload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> FireWeaponTextures.clearCache());
        }
    }
}
