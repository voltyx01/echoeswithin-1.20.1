package com.voltyx.efw.mixin;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The cubes of a model part, swapped for one drawing (the hat of the player model, see {@code HatFront}). */
@Mixin(ModelPart.class)
public interface ModelPartCubesAccessor {
    @Accessor("cubes")
    List<ModelPart.Cube> efw$cubes();

    @Mutable
    @Accessor("cubes")
    void efw$setCubes(List<ModelPart.Cube> cubes);
}
