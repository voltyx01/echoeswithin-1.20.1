package com.voltyx.efw.stamina;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.INBTSerializable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Per-player stamina and roll state. The 1.12 code kept stamina in the entity data and roll state in a capability; here both
 * live in one capability. The server owns the values, the client gets them through the sync packets.
 */
public class PlayerData implements INBTSerializable<CompoundTag> {
    public static final Capability<PlayerData> CAP = CapabilityManager.get(new CapabilityToken<>() {
    });
    public static final ResourceLocation KEY = new ResourceLocation("efw", "player_data");

    // stamina
    public boolean initialized;
    public double stamina;
    public int regenDelay;
    public int tickCounter;
    public boolean jumpedLastTick;
    public double foodLimit;
    // roll
    public boolean dashing;
    public Vec3 dashDir = Vec3.ZERO;
    public int cooldown;
    public int postDashTimer;

    // server side values last sent to the client
    public double lastSentStamina = -1;
    public int lastSentRegenDelay = -1;

    public static PlayerData get(Player player) {
        return player.getCapability(CAP).orElse(null);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Initialized", initialized);
        tag.putDouble("Stamina", stamina);
        tag.putInt("RegenDelay", regenDelay);
        tag.putInt("TickCounter", tickCounter);
        tag.putDouble("FoodLimit", foodLimit);
        tag.putInt("Cooldown", cooldown);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        initialized = tag.getBoolean("Initialized");
        stamina = tag.getDouble("Stamina");
        regenDelay = tag.getInt("RegenDelay");
        tickCounter = tag.getInt("TickCounter");
        foodLimit = tag.getDouble("FoodLimit");
        cooldown = tag.getInt("Cooldown");
    }

    public void copyFrom(PlayerData other) {
        deserializeNBT(other.serializeNBT());
    }

    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        private final PlayerData data = new PlayerData();
        private final LazyOptional<PlayerData> optional = LazyOptional.of(() -> data);

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return CAP.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            data.deserializeNBT(nbt);
        }
    }
}
