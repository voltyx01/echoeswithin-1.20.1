package com.voltyx.efw.headshot;

import com.voltyx.efw.EFW;
import com.voltyx.efw.gear.WornRingItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** The virtual head box of a mob (port of AdvancedHeadshotManager, same formulas). */
public final class HeadshotManager {
    public record VirtualHead(double radius, double yOffset, double forwardOffset) {
    }

    /** "modid:entity" -> head. */
    private static final Map<String, VirtualHead> MODDED_HEADS = new HashMap<>();

    private HeadshotManager() {
    }

    /** Reads the customMobs lines; called when the config loads or changes. */
    public static synchronized void reloadConfig() {
        MODDED_HEADS.clear();
        for (String line : HeadshotConfig.CUSTOM_MOBS.get()) {
            try {
                String[] parts = line.split(",");
                if (parts.length == 4) {
                    double forward = Double.parseDouble(parts[1].trim());
                    double y = Double.parseDouble(parts[2].trim());
                    double radius = Double.parseDouble(parts[3].trim());
                    MODDED_HEADS.put(parts[0].trim(), new VirtualHead(radius, y, forward));
                }
            } catch (NumberFormatException e) {
                EFW.LOGGER.error("[Headshots] bad hitbox line in the config: {}", line);
            }
        }
    }

    public static synchronized VirtualHead customHead(String entityId) {
        return MODDED_HEADS.get(entityId);
    }

    /** The head box as the shooter sees it: the worn ring makes the heads of parasites bigger for its wearer. */
    public static AABB getHeadBox(LivingEntity target, @Nullable Entity shooter) {
        AABB box = getHeadBox(target);
        if (shooter instanceof Player player && WornRingItem.isParasite(target) && WornRingItem.wears(player)) {
            double grow = (HeadshotConfig.RING_HEAD_SCALE.get() - 1.0D) / 2.0D;
            box = box.inflate(box.getXsize() * grow, box.getYsize() * grow, box.getZsize() * grow);
        }
        return box;
    }

    public static AABB getHeadBox(LivingEntity target) {
        ResourceLocation regName = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        VirtualHead head = customHead(regName != null ? regName.toString() : "");
        if (head == null) {
            // automatic head for mobs that are not in the list
            float w = target.getBbWidth();
            float h = target.getBbHeight();
            double scale = ((w / 0.6D) + (h / 1.8D)) / 2.0D; // relative to the player's size
            if (scale < 0.5D) {
                scale = 0.5D;
            }
            double radius = HeadshotConfig.BASE_RADIUS.get() * Math.pow(scale, 0.6D);
            if (radius > w * 0.5D) {
                radius = w * 0.5D;
            }
            if (h >= w * 1.5F) {
                // humanoids
                head = new VirtualHead(radius, target.getEyeHeight() + HeadshotConfig.HUMANOID_Y_OFFSET.get(), 0.0D);
            } else {
                // quadrupeds
                double yOffset = h * HeadshotConfig.QUADRUPED_Y_OFFSET_MULT.get() + HeadshotConfig.QUADRUPED_Y_OFFSET_ADD.get();
                double forwardOffset = w * HeadshotConfig.QUADRUPED_FORWARD_OFFSET_MULT.get() + 0.1D * scale;
                head = new VirtualHead(radius, yOffset, forwardOffset);
            }
        }
        return box(target, head.forwardOffset, head.yOffset, head.radius);
    }

    /** A cube of the given radius, moved forward along the head look and up from the feet. */
    public static AABB box(LivingEntity target, double forward, double y, double radius) {
        Vec3 look = target.getViewVector(1.0F);
        double headX = target.getX() + look.x * forward;
        double headY = target.getY() + y + look.y * forward;
        double headZ = target.getZ() + look.z * forward;
        return new AABB(headX - radius, headY - radius, headZ - radius, headX + radius, headY + radius, headZ + radius);
    }
}
