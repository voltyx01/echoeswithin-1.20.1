package com.voltyx.efw.nvg.client;

import com.voltyx.efw.nvg.NvgHelmets;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.NoSuchElementException;

/**
 * Folds the visor parts (visor_r1 .. visor_r9) of the helmet model about the hinge of the visor (the 1.12 visorMount at
 * 0, -5.4775, -4.8897 of the head): the parts are separate children here, so each is turned about the hinge from its own
 * initial pose.
 */
public final class NvgVisor {
    private static final float HINGE_Y = -5.4775F;
    private static final float HINGE_Z = -4.8897F;

    private NvgVisor() {
    }

    public static void apply(HumanoidModel<?> model, LivingEntity entity) {
        ItemStack helmet = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (!NvgHelmets.isNvgHelmet(helmet)) {
            return;
        }
        float angle = NvgAnimation.visorAngle(entity, NvgHelmets.isActive(helmet));
        Matrix4f hinge = new Matrix4f().translation(0.0F, HINGE_Y, HINGE_Z).rotateX(angle).translate(0.0F, -HINGE_Y, -HINGE_Z);
        for (int i = 1; i <= 9; i++) {
            ModelPart part;
            try {
                part = model.head.getChild("visor_r" + i);
            } catch (NoSuchElementException e) {
                continue;
            }
            PartPose pose = part.getInitialPose();
            Matrix4f own = new Matrix4f().translation(pose.x, pose.y, pose.z).rotateZYX(pose.zRot, pose.yRot, pose.xRot);
            Matrix4f result = hinge.mul(own, new Matrix4f());
            Vector3f position = result.getTranslation(new Vector3f());
            Vector3f euler = new Matrix3f(result).getEulerAnglesZYX(new Vector3f());
            part.x = position.x;
            part.y = position.y;
            part.z = position.z;
            part.xRot = euler.x;
            part.yRot = euler.y;
            part.zRot = euler.z;
        }
    }
}
