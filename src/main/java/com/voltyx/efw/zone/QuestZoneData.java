package com.voltyx.efw.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** All quest zones of the world, saved with the overworld data. */
public class QuestZoneData extends SavedData {
    private static final String DATA_NAME = "efw_quest_zones";
    private final Map<String, QuestZone> zones = new LinkedHashMap<>();

    public static QuestZoneData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(QuestZoneData::load, QuestZoneData::new, DATA_NAME);
    }

    public Collection<QuestZone> getZones() {
        return Collections.unmodifiableCollection(zones.values());
    }

    public QuestZone findZoneAt(String dim, BlockPos pos) {
        for (QuestZone zone : zones.values()) {
            if (zone.contains(dim, pos)) {
                return zone;
            }
        }
        return null;
    }

    public QuestZone findZoneTouching(String dim, BlockPos pos, int radius) {
        for (QuestZone zone : zones.values()) {
            if (zone.intersectsOrTouches(dim, pos, radius)) {
                return zone;
            }
        }
        return null;
    }

    public boolean isProtected(String dim, BlockPos pos) {
        return findZoneAt(dim, pos) != null;
    }

    public void addZone(QuestZone zone) {
        zones.put(zone.id(), zone);
        setDirty();
    }

    public boolean removeZone(String id) {
        if (zones.remove(id) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public static QuestZoneData load(CompoundTag nbt) {
        QuestZoneData data = new QuestZoneData();
        ListTag list = nbt.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            QuestZone zone = QuestZone.load(list.getCompound(i));
            data.zones.put(zone.id(), zone);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag list = new ListTag();
        for (QuestZone zone : zones.values()) {
            list.add(zone.save());
        }
        nbt.put("Zones", list);
        return nbt;
    }
}
