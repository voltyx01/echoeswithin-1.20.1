package com.voltyx.efw.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.pojo.display.gun.LayerGunShow;
import com.tacz.guns.util.math.MathUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Guns worn on the body in third person, in place of TACZ's own (which shows a gun only when it lies in the hotbar slot its display names,
 * and hangs it from the root of the model, so it stays put while the torso bends). Here the hotbar gives one pistol (holster) and one
 * other gun (back), the first of each kind outside the selected slot, wherever they lie; they and the gun in the off hand are carried by
 * the torso, so they follow crouching and the animations.
 */
public final class BodyGuns {
    /** Where a gun goes when its display says nothing for the hotbar: TACZ's glock (hip) and AK (back). */
    private static final Vector3f[] PISTOL = {new Vector3f(-5, 13, -1), new Vector3f(-90, -35, 90), new Vector3f(0.5f, 0.5f, 0.5f)};
    private static final Vector3f[] LONG = {new Vector3f(-1, 20, 3), new Vector3f(-180, 0, 120), new Vector3f(0.5f, 0.5f, 0.5f)};

    private BodyGuns() {
    }

    public static void render(LivingEntity entity, PoseStack pose, MultiBufferSource buffer, int light) {
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player && mc.options.getCameraType().isFirstPerson()) {
            return; // Better Combat draws the whole body in first person during its swings: the guns on it would hang in the view
        }
        pose.pushPose();
        ModelPart body = body(entity);
        if (body != null) {
            // the shows are placed in the model's space; the torso moves them only by how far it bends and sinks (crouching), about its
            // pivot at the neck. Its other turns are left out: they would turn the guns over
            pose.translate(0.0f, body.y / 16.0f, body.z / 16.0f);
            if (Math.abs(body.xRot) < 1.2f) {
                pose.mulPose(new Quaternionf().rotationX(body.xRot));
            }
        }
        ItemStack offhand = entity.getOffhandItem();
        if (IGun.getIGunOrNull(offhand) != null) {
            TimelessAPI.getGunDisplay(offhand).ifPresent(display -> {
                LayerGunShow show = display.getOffhandShow();
                if (show != null) {
                    draw(entity, pose, buffer, light, offhand, show.getPos(), show.getRotate(), show.getScale());
                }
            });
        }
        if (entity instanceof Player player) {
            Inventory inventory = player.getInventory();
            ItemStack pistol = ItemStack.EMPTY;
            int pistolSlot = 0;
            int otherSlot = 0;
            ItemStack other = ItemStack.EMPTY;
            for (int i = 0; i < 9; i++) {
                if (i == inventory.selected) {
                    continue;
                }
                ItemStack stack = inventory.getItem(i);
                IGun gun = IGun.getIGunOrNull(stack);
                if (gun == null) {
                    continue;
                }
                if (isPistol(gun, stack)) {
                    if (pistol.isEmpty()) {
                        pistol = stack;
                        pistolSlot = i;
                    }
                } else if (other.isEmpty()) {
                    other = stack;
                    otherSlot = i;
                }
            }
            hotbar(entity, pose, buffer, light, pistol, pistolSlot, PISTOL);
            hotbar(entity, pose, buffer, light, other, otherSlot, LONG);
        }
        pose.popPose();
    }

    private static void hotbar(LivingEntity entity, PoseStack pose, MultiBufferSource buffer, int light, ItemStack stack, int slot, Vector3f[] fallback) {
        if (stack.isEmpty()) {
            return;
        }
        TimelessAPI.getGunDisplay(stack).ifPresent(display -> {
            // the show of its own slot, else of the lowest slot the display names, else the stock place of its kind
            LayerGunShow show = null;
            Int2ObjectMap<LayerGunShow> shows = display.getHotbarShow();
            if (shows != null && !shows.isEmpty()) {
                show = shows.get(slot);
                if (show == null) {
                    int best = Integer.MAX_VALUE;
                    for (Int2ObjectMap.Entry<LayerGunShow> e : shows.int2ObjectEntrySet()) {
                        if (e.getIntKey() < best && e.getValue() != null) {
                            best = e.getIntKey();
                            show = e.getValue();
                        }
                    }
                }
            }
            if (show != null) {
                draw(entity, pose, buffer, light, stack, show.getPos(), show.getRotate(), show.getScale());
            } else {
                draw(entity, pose, buffer, light, stack, fallback[0], fallback[1], fallback[2]);
            }
        });
    }

    /** TACZ's HumanoidOffhandRender.renderGunItem. */
    private static void draw(LivingEntity entity, PoseStack pose, MultiBufferSource buffer, int light, ItemStack stack, Vector3f pos,
            Vector3f rotate, Vector3f scale) {
        pose.pushPose();
        pose.translate(-pos.x() / 16.0f, 1.5 - pos.y() / 16.0f, pos.z() / 16.0f);
        pose.scale(-scale.x(), -scale.y(), scale.z());
        Quaternionf rotation = new Quaternionf();
        MathUtil.toQuaternion((float) Math.toRadians(rotate.x()), (float) Math.toRadians(rotate.y()), (float) Math.toRadians(rotate.z()), rotation);
        pose.mulPose(rotation);
        if (turnedAround(stack)) {
            pose.mulPose(new Quaternionf().rotationY((float) Math.PI));
            // and their fixed node sits 2 model pixels (times the fixed scale of 1.2) further to the side: the gun moves away from the
            // place, back along the muzzle
            pose.translate(0.0f, 0.0f, 2.0f * 1.2f / 16.0f);
        }
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffer,
                entity.level(), entity.getId());
        pose.popPose();
    }

    /**
     * The models of the CIBR pack are made for the item frame the other way round: the "fixed" node of their models turns 90 degrees
     * the opposite way (and sits on the other side) than in TACZ's own, so with the same place they show the muzzle where the stock
     * ones show the butt.
     */
    private static boolean turnedAround(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun != null && gun.getGunId(stack).getNamespace().startsWith("cib");
    }

    private static boolean isPistol(IGun gun, ItemStack stack) {
        return TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(index -> "pistol".equals(index.getType())).orElse(false);
    }

    /** The torso of the entity's model, already posed for this frame (the layers are drawn after the model is set up). */
    private static ModelPart body(LivingEntity entity) {
        EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (renderer instanceof RenderLayerParent<?, ?> parent && parent.getModel() instanceof HumanoidModel<?> model) {
            return model.body;
        }
        return null;
    }
}
