package com.voltyx.efw.mixin;

import com.voltyx.efw.tech.ChargingSlots;
import com.voltyx.efw.tech.DeviceConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The electricity generator of Refurbished Furniture charges the Li-ion batteries and devices put into two extra slots while it
 * burns fuel (the 1.12 TileEntityElectricityGenerator). The slots are an extra container on the block entity, saved with it.
 * The names with m_ are the SRG names of the game methods the generator overrides.
 */
@Pseudo
@Mixin(targets = "com.mrcrayfish.furniture.refurbished.blockentity.ElectricityGeneratorBlockEntity", remap = false)
public abstract class GeneratorBlockEntityMixin implements ChargingSlots.Holder {
    @Unique
    private final SimpleContainer efw$charge = new SimpleContainer(ChargingSlots.COUNT);

    @Shadow(remap = false)
    protected int energy;
    @Shadow(remap = false)
    protected boolean enabled;

    @Unique
    private boolean efw$listening;

    @Override
    public SimpleContainer efw$chargeSlots() {
        if (!efw$listening) {
            efw$listening = true;
            efw$charge.addListener(c -> ((BlockEntity) (Object) this).setChanged());
        }
        return efw$charge;
    }

    @Inject(method = "earlyNodeTick", at = @At("TAIL"), remap = false)
    private void efw$charge(Level level, CallbackInfo ci) {
        if (level.isClientSide || !enabled || energy <= 0 || !ChargingSlots.hasWork(efw$charge)) {
            return;
        }
        if (ChargingSlots.charge(efw$charge, DeviceConfig.GENERATOR_CHARGE_PER_TICK.get())) {
            ((BlockEntity) (Object) this).setChanged();
        }
    }

    @Inject(method = "m_183515_", at = @At("TAIL"), remap = false)
    private void efw$save(CompoundTag tag, CallbackInfo ci) {
        tag.put("EfwCharge", efw$charge.createTag());
    }

    @Inject(method = "m_142466_", at = @At("TAIL"), remap = false)
    private void efw$load(CompoundTag tag, CallbackInfo ci) {
        efw$charge.clearContent();
        if (tag.contains("EfwCharge", 9)) {
            efw$charge.fromTag(tag.getList("EfwCharge", 10));
        }
    }
}
