package com.voltyx.efw.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** A protected box: nothing in it can be broken, built, blown up or griefed until a door into it is opened. */
public record QuestZone(String id, String name, String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static QuestZone of(String id, String name, String dimension, BlockPos a, BlockPos b) {
        return new QuestZone(id, name, dimension, Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public static String dimensionOf(Level level) {
        return level.dimension().location().toString();
    }

    public boolean contains(String dim, BlockPos pos) {
        return dimension.equals(dim) && pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public boolean intersectsOrTouches(String dim, BlockPos pos, int radius) {
        return dimension.equals(dim) && pos.getX() >= minX - radius && pos.getX() <= maxX + radius && pos.getY() >= minY - radius
                && pos.getY() <= maxY + radius && pos.getZ() >= minZ - radius && pos.getZ() <= maxZ + radius;
    }

    public AABB aabb() {
        return new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public CompoundTag save() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("Id", id);
        nbt.putString("Name", name);
        nbt.putString("Dim", dimension);
        nbt.putInt("MinX", minX);
        nbt.putInt("MinY", minY);
        nbt.putInt("MinZ", minZ);
        nbt.putInt("MaxX", maxX);
        nbt.putInt("MaxY", maxY);
        nbt.putInt("MaxZ", maxZ);
        return nbt;
    }

    public static QuestZone load(CompoundTag nbt) {
        return new QuestZone(nbt.getString("Id"), nbt.getString("Name"), nbt.getString("Dim"), nbt.getInt("MinX"), nbt.getInt("MinY"),
                nbt.getInt("MinZ"), nbt.getInt("MaxX"), nbt.getInt("MaxY"), nbt.getInt("MaxZ"));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(name);
        buf.writeUtf(dimension);
        buf.writeInt(minX);
        buf.writeInt(minY);
        buf.writeInt(minZ);
        buf.writeInt(maxX);
        buf.writeInt(maxY);
        buf.writeInt(maxZ);
    }

    public static QuestZone read(FriendlyByteBuf buf) {
        return new QuestZone(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt());
    }
}
