package com.voltyx.efw.mixin;

import com.voltyx.efw.zone.StructureZoneHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Saves the quest zones inside a structure with it and recreates them where the structure is placed (1.12 MixinStructureTemplate). */
@Mixin(StructureTemplate.class)
public abstract class StructureTemplateZonesMixin {
    @Unique
    private ListTag efw$questZones = new ListTag();

    @Inject(method = "fillFromWorld", at = @At("TAIL"))
    private void efw$capture(Level level, BlockPos startPos, Vec3i size, boolean withEntities, @Nullable Block toIgnore, CallbackInfo ci) {
        if (!level.isClientSide) {
            efw$questZones = StructureZoneHelper.captureZones(level, startPos, size);
        }
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void efw$save(CompoundTag nbt, CallbackInfoReturnable<CompoundTag> cir) {
        if (efw$questZones != null && !efw$questZones.isEmpty()) {
            nbt.put(StructureZoneHelper.NBT_KEY, efw$questZones.copy());
        }
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void efw$load(HolderGetter<Block> blocks, CompoundTag nbt, CallbackInfo ci) {
        efw$questZones = nbt.contains(StructureZoneHelper.NBT_KEY, Tag.TAG_LIST)
                ? nbt.getList(StructureZoneHelper.NBT_KEY, Tag.TAG_COMPOUND).copy() : new ListTag();
    }

    @Inject(method = "placeInWorld", at = @At("RETURN"))
    private void efw$place(ServerLevelAccessor accessor, BlockPos pos, BlockPos pivot, StructurePlaceSettings settings, RandomSource random,
                           int flags, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && efw$questZones != null && !efw$questZones.isEmpty()) {
            ServerLevel level = accessor.getLevel();
            ListTag zones = efw$questZones.copy();
            BlockPos origin = pos.immutable();
            // structures also generate on worker threads: the zone data is only touched on the server thread
            level.getServer().execute(() -> StructureZoneHelper.applyZonesToWorld(level, origin, settings, zones));
        }
    }
}
