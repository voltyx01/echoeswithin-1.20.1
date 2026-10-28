package com.voltyx.efw.placed;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * One item lying in the world (TileEntityPlacedItem): the stack with its position inside the block, the rotation, the scale and the
 * flags. It is an ordinary block entity, so structure blocks save it with the rest of the build.
 */
public class PlacedItemBlockEntity extends BlockEntity {
    private ItemStack stack = ItemStack.EMPTY;
    private float offsetX = 0.5f;
    private float offsetY = 0.05f;
    private float offsetZ = 0.5f;
    private float yaw;
    private float pitch;
    private float roll;
    private float scale = 1.0f;
    private boolean locked;
    private float iconX;
    private float iconY;
    private float iconZ;

    public PlacedItemBlockEntity(BlockPos pos, BlockState state) {
        super(PlacedContent.PLACED_ITEM_BE.get(), pos, state);
    }

    public ItemStack getStack() {
        return stack;
    }

    public void setStack(ItemStack stack) {
        this.stack = stack == null ? ItemStack.EMPTY : stack;
        changed();
    }

    public float offsetX() {
        return offsetX;
    }

    public float offsetY() {
        return offsetY;
    }

    public float offsetZ() {
        return offsetZ;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float roll() {
        return roll;
    }

    public float scale() {
        return scale;
    }

    public boolean isLocked() {
        return locked;
    }

    public float iconX() {
        return iconX;
    }

    public float iconY() {
        return iconY;
    }

    public float iconZ() {
        return iconZ;
    }

    /** Sets the whole layout at once; the values are limited so that a packet cannot throw the item across the world. */
    public void setLayout(float offsetX, float offsetY, float offsetZ, float yaw, float pitch, float roll, float scale, boolean locked,
                          float iconX, float iconY, float iconZ) {
        this.offsetX = clamp(offsetX, -1.0f, 2.0f);
        this.offsetY = clamp(offsetY, -1.0f, 2.0f);
        this.offsetZ = clamp(offsetZ, -1.0f, 2.0f);
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.scale = clamp(scale, 0.1f, 5.0f);
        this.locked = locked;
        this.iconX = clamp(iconX, -2.0f, 2.0f);
        this.iconY = clamp(iconY, -2.0f, 2.0f);
        this.iconZ = clamp(iconZ, -2.0f, 2.0f);
        changed();
    }

    private static float clamp(float v, float min, float max) {
        return Float.isNaN(v) ? 0.0f : Math.max(min, Math.min(max, v));
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!stack.isEmpty()) {
            tag.put("Item", stack.save(new CompoundTag()));
        }
        tag.putFloat("OffsetX", offsetX);
        tag.putFloat("OffsetY", offsetY);
        tag.putFloat("OffsetZ", offsetZ);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        tag.putFloat("Roll", roll);
        tag.putFloat("Scale", scale);
        tag.putBoolean("Locked", locked);
        tag.putFloat("IconOffsetX", iconX);
        tag.putFloat("IconOffsetY", iconY);
        tag.putFloat("IconOffsetZ", iconZ);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stack = tag.contains("Item", 10) ? ItemStack.of(tag.getCompound("Item")) : ItemStack.EMPTY;
        offsetX = tag.contains("OffsetX") ? tag.getFloat("OffsetX") : 0.5f;
        offsetY = tag.contains("OffsetY") ? tag.getFloat("OffsetY") : 0.05f;
        offsetZ = tag.contains("OffsetZ") ? tag.getFloat("OffsetZ") : 0.5f;
        yaw = tag.getFloat("Yaw");
        pitch = tag.getFloat("Pitch");
        roll = tag.getFloat("Roll");
        scale = tag.contains("Scale") ? tag.getFloat("Scale") : 1.0f;
        locked = tag.getBoolean("Locked");
        iconX = tag.getFloat("IconOffsetX");
        iconY = tag.getFloat("IconOffsetY");
        iconZ = tag.getFloat("IconOffsetZ");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0);
    }
}
