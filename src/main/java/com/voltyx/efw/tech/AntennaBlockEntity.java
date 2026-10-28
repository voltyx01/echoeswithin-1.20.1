package com.voltyx.efw.tech;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** State of the code safe (port of TileEntityAntenna): door animation, code, looted flag and keypad press animations. */
public class AntennaBlockEntity extends BlockEntity {
    public static final float DOOR_ANIM_MAX_TIME = 0.5833f;
    public static final float BUTTON_ANIM_MAX_TIME = 0.3333f;
    public static final String DEFAULT_PIN = "3608";

    private boolean isOpen;
    private boolean isUnlocked;
    private boolean isLooted;
    private float doorAnimTime;
    private float prevDoorAnimTime;
    private String pinCode = DEFAULT_PIN;
    private UUID activePlayerUUID;
    /** button name -> remaining press time (client visuals) */
    private final Map<String, Float> buttonTimers = new HashMap<>();

    public AntennaBlockEntity(BlockPos pos, BlockState state) {
        super(TechContent.ANTENNA_BE.get(), pos, state);
    }

    public boolean isOpen() {
        return isOpen;
    }

    public boolean isUnlocked() {
        return isUnlocked;
    }

    public boolean isLooted() {
        return isLooted;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void setLooted(boolean looted) {
        if (isLooted != looted) {
            isLooted = looted;
            sync();
        }
    }

    public void setOpen(boolean open) {
        if (isOpen != open) {
            isOpen = open;
            sync();
        }
    }

    public void setUnlocked(boolean unlocked) {
        if (isUnlocked != unlocked) {
            isUnlocked = unlocked;
            sync();
        }
    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(String pinCode) {
        this.pinCode = pinCode;
        setChanged();
    }

    public void triggerButtonPress(String buttonName) {
        buttonTimers.put(buttonName, BUTTON_ANIM_MAX_TIME);
    }

    /** Keyframe time of a button press, 0..0.3333. */
    public float getButtonAnimTime(String buttonName) {
        Float t = buttonTimers.get(buttonName);
        return t == null || t <= 0.0f ? 0.0f : BUTTON_ANIM_MAX_TIME - t;
    }

    public void setActivePlayer(Player player) {
        activePlayerUUID = player != null ? player.getUUID() : null;
    }

    public void close() {
        activePlayerUUID = null;
    }

    void tick() {
        prevDoorAnimTime = doorAnimTime;
        if (isOpen) {
            doorAnimTime = Math.min(DOOR_ANIM_MAX_TIME, doorAnimTime + 0.05f);
        } else {
            doorAnimTime = Math.max(0.0f, doorAnimTime - 0.05f);
        }
        if (!buttonTimers.isEmpty()) {
            buttonTimers.entrySet().removeIf(entry -> {
                float remaining = entry.getValue() - 0.05f;
                if (remaining <= 0.0f) {
                    return true;
                }
                entry.setValue(remaining);
                return false;
            });
        }
        if (level != null && !level.isClientSide && activePlayerUUID != null) {
            Player player = level.getPlayerByUUID(activePlayerUUID);
            if (player == null || !player.isAlive() || player.distanceToSqr(worldPosition.getCenter()) > 16.0) {
                close();
            }
        }
    }

    public float getInterpolatedDoorAnimTime(float partialTicks) {
        return prevDoorAnimTime + (doorAnimTime - prevDoorAnimTime) * partialTicks;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("IsOpen", isOpen);
        tag.putBoolean("IsUnlocked", isUnlocked);
        tag.putBoolean("IsLooted", isLooted);
        tag.putFloat("DoorAnimTime", doorAnimTime);
        tag.putString("PinCode", pinCode);
        if (activePlayerUUID != null) {
            tag.putUUID("ActivePlayer", activePlayerUUID);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        isOpen = tag.getBoolean("IsOpen");
        isUnlocked = tag.getBoolean("IsUnlocked");
        isLooted = tag.getBoolean("IsLooted");
        doorAnimTime = tag.getFloat("DoorAnimTime");
        prevDoorAnimTime = doorAnimTime;
        pinCode = tag.contains("PinCode") ? tag.getString("PinCode") : DEFAULT_PIN;
        if ("1234".equals(pinCode)) {
            pinCode = DEFAULT_PIN;
        }
        activePlayerUUID = tag.hasUUID("ActivePlayer") ? tag.getUUID("ActivePlayer") : null;
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
