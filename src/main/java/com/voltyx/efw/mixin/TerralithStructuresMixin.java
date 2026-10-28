package com.voltyx.efw.mixin;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Terralith brings its own houses, huts, villages, towers and ruins. None of them is wanted in this world, so every structure of the
 * terralith namespace fails to start: the chunk is generated as if the structure set had not picked it.
 */
@Mixin(ChunkGenerator.class)
public abstract class TerralithStructuresMixin {
    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    private void efw$noTerralithStructures(StructureSet.StructureSelectionEntry entry, StructureManager structures, RegistryAccess access,
                                           RandomState random, StructureTemplateManager templates, long seed, ChunkAccess chunk, ChunkPos pos,
                                           SectionPos section, CallbackInfoReturnable<Boolean> cir) {
        ResourceLocation key = access.registryOrThrow(Registries.STRUCTURE).getKey(entry.structure().value());
        if (key != null && key.getNamespace().equals("terralith")) {
            cir.setReturnValue(false);
        }
    }
}
