package com.voltyx.efw.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The parts of one searchable object, so that searching it once counts for all of them: the two halves of a double chest, and the
 * blocks of a Doomsday Decoration vehicle or vending machine. Doomsday builds those from separate blocks with nothing linking them
 * ({@code green_hummer_1..9}, {@code policecar_1..4}, {@code front_red_sedan} + {@code redsedan} + {@code rear_red_sedan}), so the
 * parts are the touching blocks of the same object name that face the same way.
 */
public final class LootParts {
    private static final String DOOMSDAY = "doomsday_decoration";
    private static final int MAX_PARTS = 12;
    /** Object names (see {@link #objectName}) whose blocks form one object; the rest of Doomsday stands alone. */
    private static final String[] GROUPED = {"ambulance", "jeep", "van", "hummer", "policecar", "sedan", "car",
            "vendingmachine"};
    private static final Map<ResourceLocation, String> NAMES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> OPENS = new ConcurrentHashMap<>();

    private LootParts() {
    }

    /** The searchable container at a clicked position, or null. */
    public static BlockPos lootPos(Level level, BlockPos pos) {
        return LootContainers.isLootContainer(level.getBlockEntity(pos)) ? pos : null;
    }

    /** Every part of the object at {@code pos}, the position itself first. */
    public static List<BlockPos> parts(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        List<BlockPos> out = new ArrayList<>();
        out.add(pos.immutable());
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            out.add(pos.relative(ChestBlock.getConnectedDirection(state)).immutable());
            return out;
        }
        String name = objectName(state);
        if (name == null) {
            return out;
        }
        Direction facing = facing(state);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(out);
        Set<BlockPos> seen = new java.util.HashSet<>(out);
        while (!queue.isEmpty() && out.size() < MAX_PARTS) {
            BlockPos at = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos next = at.relative(d);
                if (!seen.add(next) || !level.isLoaded(next)) {
                    continue;
                }
                BlockState ns = level.getBlockState(next);
                if (name.equals(objectName(ns)) && facing(ns) == facing && LootContainers.isLootContainer(level.getBlockEntity(next))) {
                    out.add(next.immutable());
                    queue.add(next);
                    if (out.size() >= MAX_PARTS) {
                        break;
                    }
                }
            }
        }
        return out;
    }

    /** A Doomsday vehicle (for its door sound), by the same object name as the grouping. */
    public static boolean isVehicle(BlockState state) {
        String name = objectName(state);
        return name != null && !name.endsWith("vendingmachine");
    }

    /**
     * The object a Doomsday block belongs to: its name without the part number, the front/rear prefix, the "discard" (wrecked)
     * prefix and the underscores, e.g. {@code front_red_sedan} and {@code redsedan} are both "redsedan". Null for blocks that
     * stand alone.
     */
    static String objectName(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null || !id.getNamespace().equals(DOOMSDAY)) {
            return null;
        }
        String name = NAMES.computeIfAbsent(id, LootParts::normalise);
        return name.isEmpty() ? null : name;
    }

    private static String normalise(ResourceLocation id) {
        String n = id.getPath().replaceAll("_\\d+$", "");
        n = n.replaceFirst("^(front|rear)_?", "").replace("_", "");
        if (n.equals("whitehumme")) {
            n = "whitehummer";
        }
        for (String g : GROUPED) {
            if (n.endsWith(g)) {
                return n;
            }
        }
        return "";
    }

    private static Direction facing(BlockState state) {
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : null;
    }

    /**
     * Containers that open by themselves when a player starts using them (lid, doors, their own sound) through the openers
     * count: the vanilla chests, barrels and shulker boxes and the storage of Refurbished Furniture.
     */
    public static boolean opensItself(Container c) {
        if (c instanceof ChestBlockEntity || c instanceof BarrelBlockEntity || c instanceof ShulkerBoxBlockEntity) {
            return true;
        }
        return c instanceof BlockEntity && OPENS.computeIfAbsent(c.getClass(), LootParts::isRefurbished);
    }

    private static boolean isRefurbished(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (c.getName().equals("com.mrcrayfish.furniture.refurbished.blockentity.BasicLootBlockEntity")) {
                return true;
            }
        }
        return false;
    }
}
