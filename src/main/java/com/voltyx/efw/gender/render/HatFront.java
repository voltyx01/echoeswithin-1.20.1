package com.voltyx.efw.gender.render;

import com.voltyx.efw.mixin.ModelPartCubesAccessor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

import java.util.EnumSet;
import java.util.List;

/**
 * The front face of the hat layer of the skin, taken away for one drawing: while the eyes are set up in the Seven screen the face
 * itself has to be seen (the hat layer over it would hide the pixels being picked). The hat cube of the player model (skin u 32,
 * v 0, 8x8x8 grown by 0.5) is swapped for the same cube without its front (north) face and put back right after.
 */
public final class HatFront {
    private static ModelPart.Cube noFront;

    private HatFront() {
    }

    /** Swaps the cube of the hat for one without the front; the returned action puts it back (does nothing for an unknown hat). */
    public static Runnable hide(ModelPart hat) {
        ModelPartCubesAccessor part = (ModelPartCubesAccessor) (Object) hat;
        List<ModelPart.Cube> cubes = part.efw$cubes();
        if (cubes.size() != 1) {
            return () -> {
            };
        }
        ModelPart.Cube cube = cubes.get(0);
        if (cube.minX != -4.0f || cube.minY != -8.0f || cube.minZ != -4.0f || cube.maxX != 4.0f || cube.maxY != 0.0f || cube.maxZ != 4.0f) {
            return () -> {
            };
        }
        if (noFront == null) {
            EnumSet<Direction> faces = EnumSet.allOf(Direction.class);
            faces.remove(Direction.NORTH);
            noFront = new ModelPart.Cube(32, 0, -4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f, 0.5f, 0.5f, 0.5f, false, 64.0f, 64.0f, faces);
        }
        part.efw$setCubes(List.of(noFront));
        return () -> part.efw$setCubes(cubes);
    }
}
