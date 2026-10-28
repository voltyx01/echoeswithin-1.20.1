package com.voltyx.efw.tech;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * Body camera (port of ItemBodycam): worn in the Curios body slot or the chest slot, it streams to terminals with the
 * bodycam driver while powered and charged. Sneak + use or X in the inventory toggles the power.
 */
public class BodycamItem extends Item {
    private static final Random RANDOM = new Random();

    public BodycamItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static boolean hasCamId(ItemStack stack) {
        CompoundTag tag = stack.isEmpty() ? null : stack.getTag();
        return tag != null && tag.contains("cam_id") && !tag.getString("cam_id").isEmpty();
    }

    public static String getCamId(ItemStack stack) {
        return hasCamId(stack) ? stack.getTag().getString("cam_id") : "CAM-0000";
    }

    public static String assignUniqueCamId(ItemStack stack) {
        if (stack.isEmpty()) {
            return "CAM-0000";
        }
        String id = "CAM-" + (1000 + RANDOM.nextInt(9000));
        stack.getOrCreateTag().putString("cam_id", id);
        return id;
    }

    public static boolean isPowerEnabled(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains("cam_enabled") || tag.getBoolean("cam_enabled");
    }

    public static void setPowerEnabled(ItemStack stack, boolean enabled) {
        if (!stack.isEmpty()) {
            stack.getOrCreateTag().putBoolean("cam_enabled", enabled);
        }
    }

    public static boolean togglePower(ItemStack stack) {
        boolean newState = !isPowerEnabled(stack);
        setPowerEnabled(stack, newState);
        return newState;
    }

    public static int getCharge(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(BatteryHelper.NBT_CHARGE) ? tag.getInt(BatteryHelper.NBT_CHARGE) : 0;
    }

    /** Battery percent as the terminal shows it (charge of 48 000). */
    public static int terminalPercent(ItemStack stack) {
        return Math.min(100, Math.max(0, (int) (getCharge(stack) / 48000.0f * 100)));
    }

    /** Power state message of the toggle (chat), shared by sneak + use and the X key. */
    public static void sendStatus(Player player, ItemStack stack, boolean on) {
        Component status = Component.translatable(on ? "tooltip.efw.bodycam.online_raw" : "tooltip.efw.bodycam.offline_raw")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
        player.sendSystemMessage(Component.translatable("message.efw.bodycam.status_fmt", getCamId(stack), status));
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        if (!level.isClientSide && !hasCamId(stack)) {
            assignUniqueCamId(stack);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof Player && !hasCamId(stack)) {
            assignUniqueCamId(stack);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!level.isClientSide && !hasCamId(held)) {
            assignUniqueCamId(held);
        }
        if (player.isShiftKeyDown()) {
            boolean on = togglePower(held);
            if (level.isClientSide) {
                player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.7F, on ? 1.4F : 0.8F);
            } else {
                sendStatus(player, held, on);
            }
            return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
        }
        // plain use: wear it (Curios body slot, else the empty chest slot)
        if (!level.isClientSide) {
            if (CuriosCompat.tryEquip(player, held)) {
                held.shrink(1);
            } else if (player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
                player.setItemSlot(EquipmentSlot.CHEST, held.copy());
                held.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack carried, Slot slot, ClickAction action, Player player, SlotAccess access) {
        return BatteryHelper.handleClick(stack, carried, action, player, access);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return BatteryHelper.isBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return BatteryHelper.getBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BatteryHelper.getBarColor(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (hasCamId(stack)) {
            tooltip.add(Component.literal("§7ID: §b" + getCamId(stack)));
        } else {
            tooltip.add(Component.literal("§7ID: ").append(Component.translatable("tooltip.efw.bodycam.no_id").withStyle(ChatFormatting.DARK_GRAY)));
        }
        tooltip.add(Component.translatable(isPowerEnabled(stack) ? "tooltip.efw.bodycam.status_online" : "tooltip.efw.bodycam.status_offline"));

        int percent = BatteryHelper.getChargePercent(stack);
        ItemStack installed = BatteryHelper.getInstalledBattery(stack);
        if (installed.isEmpty() || percent <= 0) {
            tooltip.add(Component.translatable("tooltip.efw.battery.required").withStyle(ChatFormatting.RED));
        } else {
            ChatFormatting color = percent > 50 ? ChatFormatting.GREEN : (percent > 20 ? ChatFormatting.YELLOW : ChatFormatting.RED);
            tooltip.add(Component.translatable("tooltip.efw.battery.charge", percent).withStyle(color)
                    .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                    .append(installed.getHoverName().copy().withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY)));
        }
        tooltip.add(Component.translatable("tooltip.efw.bodycam.toggle_hint"));
        tooltip.add(Component.translatable("tooltip.efw.bodycam.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        if (FMLEnvironment.dist == Dist.CLIENT && Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.efw.bodycam.manual_title"));
            tooltip.add(Component.translatable("tooltip.efw.bodycam.manual_step1"));
            tooltip.add(Component.translatable("tooltip.efw.bodycam.manual_step2"));
            tooltip.add(Component.translatable("tooltip.efw.bodycam.manual_step3"));
            tooltip.add(Component.translatable("tooltip.efw.bodycam.manual_step4"));
        } else {
            tooltip.add(Component.translatable("tooltip.efw.bodycam.manual_shift"));
        }
    }

    /** The bodycam a player wears: Curios first, then the chest slot (BodycamLayer.getEquippedBodycam). */
    public static ItemStack getEquipped(LivingEntity entity) {
        if (entity == null) {
            return ItemStack.EMPTY;
        }
        ItemStack curio = CuriosCompat.findFirst(entity, s -> s.getItem() instanceof BodycamItem);
        if (!curio.isEmpty()) {
            return curio;
        }
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        return chest.getItem() instanceof BodycamItem ? chest : ItemStack.EMPTY;
    }

    /** Worn, else held: the camera a terminal can see (PacketRequestBodycamList). */
    public static ItemStack getStreaming(Player player) {
        ItemStack cam = getEquipped(player);
        if (cam.isEmpty()) {
            if (player.getMainHandItem().getItem() instanceof BodycamItem) {
                cam = player.getMainHandItem();
            } else if (player.getOffhandItem().getItem() instanceof BodycamItem) {
                cam = player.getOffhandItem();
            }
        }
        return cam;
    }

    /** Worn, powered and charged: the lit texture of the body layer. */
    public static boolean isActive(LivingEntity entity) {
        ItemStack stack = getEquipped(entity);
        return !stack.isEmpty() && isPowerEnabled(stack) && getCharge(stack) > 0;
    }

    /** Once a second a worn, powered camera drains 20 charge; an empty battery switches it off. */
    static void tickWorn(Player player) {
        ItemStack stack = getEquipped(player);
        if (stack.isEmpty()) {
            return;
        }
        if (!hasCamId(stack)) {
            assignUniqueCamId(stack);
        }
        if (isPowerEnabled(stack) && player.tickCount % 20 == 0) {
            if (BatteryHelper.consumeCharge(stack, 20) <= 0) {
                setPowerEnabled(stack, false);
            }
        }
    }

    /** Server side toggle of X in the inventory (PacketToggleBodycam). */
    static void toggleFromKey(Player player, int slotId) {
        ItemStack target = ItemStack.EMPTY;
        if (slotId >= 0 && slotId < player.containerMenu.slots.size()) {
            ItemStack inSlot = player.containerMenu.getSlot(slotId).getItem();
            if (inSlot.getItem() instanceof BodycamItem) {
                target = inSlot;
            }
        }
        if (target.isEmpty()) {
            target = getStreaming(player);
        }
        if (target.getItem() instanceof BodycamItem) {
            boolean on = togglePower(target);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(),
                    SoundSource.PLAYERS, 0.7F, on ? 1.4F : 0.8F);
            sendStatus(player, target, on);
            player.containerMenu.broadcastChanges();
        }
    }

    /** The worn camera is drawn: its Curios slot is not hidden (worn any other way, it is always drawn). */
    public static boolean shown(LivingEntity entity) {
        boolean inCurio = !CuriosCompat.findFirst(entity, st -> st.getItem() instanceof BodycamItem).isEmpty();
        return !inCurio || !CuriosCompat.findVisible(entity, st -> st.getItem() instanceof BodycamItem).isEmpty();
    }
}
