package com.voltyx.efw.notes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Armour in the inspect screen as the piece itself, the way it sits on a body (its armour model and texture, modded models through
 * Forge), instead of its flat inventory icon. Centred and sized like an item model (its largest side one unit), facing the viewer.
 * Armour that draws its own 3D item (a custom item renderer, like the Tagilla mask) keeps that.
 */
final class InspectArmor {
    private static HumanoidModel<LivingEntity> inner;
    private static HumanoidModel<LivingEntity> outer;
    private static ItemStack measuredStack;
    private static InspectBounds.Box measured;

    private InspectArmor() {
    }

    static boolean isArmor(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem)) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return !mc.getItemRenderer().getModel(stack, mc.level, null, 0).isCustomRenderer();
    }

    /** Draws the armour piece centred on the origin, one unit across. */
    static void render(ItemStack stack, PoseStack pose, MultiBufferSource buffers) {
        if (measuredStack != stack) {
            measuredStack = stack;
            InspectBounds recorder = new InspectBounds();
            PoseStack facing = new PoseStack();
            facing.mulPose(Axis.YP.rotationDegrees(180.0f));
            draw(stack, facing, recorder);
            measured = recorder.box();
        }
        if (measured == null) {
            return;
        }
        pose.pushPose();
        float f = 1.0f / measured.maxSize();
        pose.scale(f, f, f);
        pose.translate(-measured.centerX(), -measured.centerY(), -measured.centerZ());
        // the model's front is -Z: turned to the viewer
        pose.mulPose(Axis.YP.rotationDegrees(180.0f));
        draw(stack, pose, buffers);
        pose.popPose();
    }

    private static void draw(ItemStack stack, PoseStack pose, MultiBufferSource buffers) {
        ArmorItem armor = (ArmorItem) stack.getItem();
        EquipmentSlot slot = armor.getEquipmentSlot();
        Minecraft mc = Minecraft.getInstance();
        HumanoidModel<LivingEntity> base = base(slot == EquipmentSlot.LEGS);
        LivingEntity entity = mc.player;
        Model model = entity != null ? ForgeHooksClient.getArmorModel(entity, stack, slot, base) : base;
        if (model instanceof HumanoidModel<?> humanoid) {
            pose(humanoid, slot);
        }
        int light = 0xF000F0;
        if (armor instanceof DyeableLeatherItem dyeable) {
            int c = dyeable.getColor(stack);
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(texture(entity, stack, slot, null))), light,
                    OverlayTexture.NO_OVERLAY, (c >> 16 & 255) / 255.0f, (c >> 8 & 255) / 255.0f, (c & 255) / 255.0f, 1.0f);
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(texture(entity, stack, slot, "overlay"))), light,
                    OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(texture(entity, stack, slot, null))), light,
                    OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private static HumanoidModel<LivingEntity> base(boolean legs) {
        Minecraft mc = Minecraft.getInstance();
        if (legs) {
            if (inner == null) {
                inner = new HumanoidModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
            }
            return inner;
        }
        if (outer == null) {
            outer = new HumanoidModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        }
        return outer;
    }

    /** Standing straight, only the parts the slot covers (as HumanoidArmorLayer shows them). */
    private static void pose(HumanoidModel<?> m, EquipmentSlot slot) {
        m.young = false;
        m.crouching = false;
        m.riding = false;
        m.head.resetPose();
        m.hat.resetPose();
        m.body.resetPose();
        m.rightArm.resetPose();
        m.leftArm.resetPose();
        m.rightLeg.resetPose();
        m.leftLeg.resetPose();
        m.setAllVisible(false);
        switch (slot) {
            case HEAD -> {
                m.head.visible = true;
                m.hat.visible = true;
            }
            case CHEST -> {
                m.body.visible = true;
                m.rightArm.visible = true;
                m.leftArm.visible = true;
            }
            case LEGS -> {
                m.body.visible = true;
                m.rightLeg.visible = true;
                m.leftLeg.visible = true;
            }
            case FEET -> {
                m.rightLeg.visible = true;
                m.leftLeg.visible = true;
            }
            default -> {
            }
        }
    }

    /** The armour texture as HumanoidArmorLayer finds it, items may change it through Forge. */
    private static ResourceLocation texture(@Nullable LivingEntity entity, ItemStack stack, EquipmentSlot slot, @Nullable String type) {
        ArmorItem armor = (ArmorItem) stack.getItem();
        String name = armor.getMaterial().getName();
        String domain = "minecraft";
        int colon = name.indexOf(':');
        if (colon != -1) {
            domain = name.substring(0, colon);
            name = name.substring(colon + 1);
        }
        String path = String.format(Locale.ROOT, "%s:textures/models/armor/%s_layer_%d%s.png", domain, name,
                slot == EquipmentSlot.LEGS ? 2 : 1, type == null ? "" : "_" + type);
        if (entity != null) {
            path = ForgeHooksClient.getArmorTexture(entity, stack, path, slot, type);
        }
        return new ResourceLocation(path);
    }
}
