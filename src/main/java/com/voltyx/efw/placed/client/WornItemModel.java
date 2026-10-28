package com.voltyx.efw.placed.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Armour (and elytras) have only a flat icon as an item model; their real look is the worn one. Such an item is drawn worn by an
 * invisible armour stand, so vanilla, Forge and GeckoLib armour models all show, centred on the piece.
 */
final class WornItemModel {
    private static ArmorStand stand;

    private WornItemModel() {
    }

    /** The slot the item is worn in, or null when it is drawn as an ordinary item. */
    static EquipmentSlot wornSlot(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem) && !(stack.getItem() instanceof ElytraItem)) {
            return null;
        }
        EquipmentSlot slot = Mob.getEquipmentSlotForItem(stack);
        return slot.getType() == EquipmentSlot.Type.ARMOR ? slot : null;
    }

    /** Height of the middle of the piece on the stand, so that it sits where an item would. */
    private static double centre(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 1.75;
            case CHEST -> 1.15;
            case LEGS -> 0.65;
            default -> 0.15;
        };
    }

    static void render(ItemStack stack, EquipmentSlot slot, Level level, PoseStack pose, MultiBufferSource buffers, int light, float partial) {
        if (stand == null || stand.level() != level) {
            stand = new ArmorStand(EntityType.ARMOR_STAND, level);
            stand.setInvisible(true);
        }
        for (EquipmentSlot s : EquipmentSlot.values()) {
            stand.setItemSlot(s, s == slot ? stack : ItemStack.EMPTY);
        }
        stand.setYRot(0.0f);
        stand.yRotO = 0.0f;
        stand.yBodyRot = 0.0f;
        stand.yBodyRotO = 0.0f;
        stand.yHeadRot = 0.0f;
        stand.yHeadRotO = 0.0f;

        pose.pushPose();
        pose.translate(0.0, -centre(slot), 0.0);
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        // as vanilla's inventory player preview does: no shadow for this one, then back on (the option is checked on its own)
        dispatcher.setRenderShadow(false);
        try {
            dispatcher.render(stand, 0.0, 0.0, 0.0, 0.0f, partial, pose, buffers, light);
        } finally {
            dispatcher.setRenderShadow(true);
            pose.popPose();
        }
    }
}
