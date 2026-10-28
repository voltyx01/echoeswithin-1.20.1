package com.voltyx.efw.tech;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Old radio state (port of TileEntityOldRadio): whether its board was taken out. */
public class OldRadioBlockEntity extends BlockEntity {
    private boolean isDisassembled;

    public OldRadioBlockEntity(BlockPos pos, BlockState state) {
        super(TechContent.OLD_RADIO_BE.get(), pos, state);
    }

    public boolean isDisassembled() {
        return isDisassembled;
    }

    public void setDisassembled(boolean disassembled) {
        if (isDisassembled != disassembled) {
            isDisassembled = disassembled;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("IsDisassembled", isDisassembled);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        isDisassembled = tag.getBoolean("IsDisassembled");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
