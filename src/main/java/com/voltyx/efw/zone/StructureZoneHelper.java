package com.voltyx.efw.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.UUID;

/**
 * Zones travel with structures: a structure block save keeps the zones inside the saved box (relative coordinates), and
 * placing the structure recreates them, rotated and mirrored like the blocks.
 */
public final class StructureZoneHelper {
    public static final String NBT_KEY = "efw_quest_zones";

    private StructureZoneHelper() {
    }

    public static ListTag captureZones(Level level, BlockPos startPos, Vec3i size) {
        ListTag list = new ListTag();
        if (!(level instanceof ServerLevel sl) || size.getX() < 1 || size.getY() < 1 || size.getZ() < 1) {
            return list;
        }
        String dim = QuestZone.dimensionOf(level);
        BlockPos min = startPos;
        BlockPos max = startPos.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        for (QuestZone zone : QuestZoneData.get(sl.getServer()).getZones()) {
            if (!zone.dimension().equals(dim)) {
                continue;
            }
            boolean intersects = zone.maxX() >= min.getX() && zone.minX() <= max.getX() && zone.maxY() >= min.getY()
                    && zone.minY() <= max.getY() && zone.maxZ() >= min.getZ() && zone.minZ() <= max.getZ();
            if (!intersects) {
                continue;
            }
            // relative coordinates, clipped to the structure box
            int relMinX = Math.max(0, zone.minX() - min.getX());
            int relMinY = Math.max(0, zone.minY() - min.getY());
            int relMinZ = Math.max(0, zone.minZ() - min.getZ());
            int relMaxX = Math.min(size.getX() - 1, zone.maxX() - min.getX());
            int relMaxY = Math.min(size.getY() - 1, zone.maxY() - min.getY());
            int relMaxZ = Math.min(size.getZ() - 1, zone.maxZ() - min.getZ());
            if (relMinX <= relMaxX && relMinY <= relMaxY && relMinZ <= relMaxZ) {
                CompoundTag t = new CompoundTag();
                t.putString("Name", zone.name());
                t.putInt("RelMinX", relMinX);
                t.putInt("RelMinY", relMinY);
                t.putInt("RelMinZ", relMinZ);
                t.putInt("RelMaxX", relMaxX);
                t.putInt("RelMaxY", relMaxY);
                t.putInt("RelMaxZ", relMaxZ);
                list.add(t);
            }
        }
        return list;
    }

    public static void applyZonesToWorld(ServerLevel level, BlockPos origin, StructurePlaceSettings settings, ListTag zonesList) {
        if (zonesList == null || zonesList.isEmpty()) {
            return;
        }
        QuestZoneData data = QuestZoneData.get(level.getServer());
        String dim = QuestZone.dimensionOf(level);
        for (int i = 0; i < zonesList.size(); i++) {
            CompoundTag t = zonesList.getCompound(i);
            String name = t.contains("Name") ? t.getString("Name") : "Комната";
            int[] xs = {t.getInt("RelMinX"), t.getInt("RelMaxX")};
            int[] ys = {t.getInt("RelMinY"), t.getInt("RelMaxY")};
            int[] zs = {t.getInt("RelMinZ"), t.getInt("RelMaxZ")};
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            // transform all 8 corners with the structure's mirror and rotation
            for (int x : xs) {
                for (int y : ys) {
                    for (int z : zs) {
                        BlockPos c = StructureTemplate.calculateRelativePosition(settings, new BlockPos(x, y, z)).offset(origin);
                        minX = Math.min(minX, c.getX());
                        minY = Math.min(minY, c.getY());
                        minZ = Math.min(minZ, c.getZ());
                        maxX = Math.max(maxX, c.getX());
                        maxY = Math.max(maxY, c.getY());
                        maxZ = Math.max(maxZ, c.getZ());
                    }
                }
            }
            String id = UUID.randomUUID().toString().substring(0, 8);
            data.addZone(new QuestZone(id, name, dim, minX, minY, minZ, maxX, maxY, maxZ));
        }
        QuestZones.syncToAll(level.getServer());
    }
}
