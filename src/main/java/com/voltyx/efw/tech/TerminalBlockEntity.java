package com.voltyx.efw.tech;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** State of a wall terminal (port of TileEntityTerminal): open/closed keyboard animation, internet module, bodycam driver. */
public class TerminalBlockEntity extends BlockEntity {
    public static final float ANIM_MAX_TIME = 0.5f;

    private boolean isOpen;
    private boolean hasInternetModule;
    private boolean hasBodycamDriver;
    private final List<String> moduleUsers = new ArrayList<>();
    private float animTime;
    private float prevAnimTime;
    private UUID activePlayerUUID;

    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(TechContent.TERMINAL_BE.get(), pos, state);
    }

    public boolean isOpen() {
        return isOpen;
    }

    public boolean hasInternetModule() {
        return hasInternetModule;
    }

    public boolean hasBodycamDriver() {
        return hasBodycamDriver;
    }

    public List<String> getModuleUsers() {
        return moduleUsers;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void installBodycamDriver() {
        hasBodycamDriver = true;
        sync();
    }

    public void installInternetModule(String playerName) {
        hasInternetModule = true;
        if (playerName != null && !moduleUsers.contains(playerName)) {
            moduleUsers.add(playerName);
        }
        sync();
    }

    public void setOpen(boolean open) {
        if (isOpen != open) {
            isOpen = open;
            sync();
        }
    }

    public void toggleOpen(Player player) {
        if (isOpen) {
            if (activePlayerUUID == null || activePlayerUUID.equals(player.getUUID())) {
                setOpen(false);
                activePlayerUUID = null;
            }
        } else {
            setOpen(true);
            activePlayerUUID = player.getUUID();
        }
    }

    public void close() {
        if (isOpen) {
            setOpen(false);
            activePlayerUUID = null;
        }
    }

    void tick() {
        prevAnimTime = animTime;
        if (isOpen) {
            animTime = Math.min(ANIM_MAX_TIME, animTime + 0.05f);
        } else {
            animTime = Math.max(0.0f, animTime - 0.05f);
        }
        // server: the user walked away or logged off
        if (level != null && !level.isClientSide && isOpen && activePlayerUUID != null) {
            Player player = level.getPlayerByUUID(activePlayerUUID);
            if (player == null || !player.isAlive() || player.distanceToSqr(worldPosition.getCenter()) > 16.0) {
                close();
            }
        }
    }

    public float getInterpolatedAnimTime(float partialTicks) {
        return prevAnimTime + (animTime - prevAnimTime) * partialTicks;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("IsOpen", isOpen);
        tag.putBoolean("HasInternetModule", hasInternetModule);
        tag.putBoolean("HasBodycamDriver", hasBodycamDriver);
        tag.putFloat("AnimTime", animTime);
        if (activePlayerUUID != null) {
            tag.putUUID("ActivePlayer", activePlayerUUID);
        }
        ListTag users = new ListTag();
        for (String user : moduleUsers) {
            users.add(StringTag.valueOf(user));
        }
        tag.put("ModuleUsers", users);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        isOpen = tag.getBoolean("IsOpen");
        hasInternetModule = tag.getBoolean("HasInternetModule");
        hasBodycamDriver = tag.getBoolean("HasBodycamDriver");
        animTime = tag.getFloat("AnimTime");
        prevAnimTime = animTime;
        activePlayerUUID = tag.hasUUID("ActivePlayer") ? tag.getUUID("ActivePlayer") : null;
        moduleUsers.clear();
        ListTag users = tag.getList("ModuleUsers", Tag.TAG_STRING);
        for (int i = 0; i < users.size(); i++) {
            moduleUsers.add(users.getString(i));
        }
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
