package com.voltyx.efw.tech;

import com.voltyx.efw.EFW;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Curios stands in for the 1.12 Baubles slot of the bodycam. Reached through reflection so that efw keeps no compile
 * dependency on Curios; every call quietly does nothing without it.
 */
public final class CuriosCompat {
    /** The Curios slot the bodycam goes into (added to players by data/efw/curios/entities/bodycam.json). */
    public static final String SLOT = "body";

    private static final boolean LOADED = ModList.get().isLoaded("curios");
    private static Method getInventory;
    private static Method findFirstCurio;
    private static Method getCurios;
    private static Method getStacks;
    private static Method slotResultStack;
    private static Method getRenders;
    private static boolean failed;

    private CuriosCompat() {
    }

    private static boolean init() {
        if (!LOADED || failed) {
            return false;
        }
        if (getInventory != null) {
            return true;
        }
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            Class<?> stacksHandler = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            Class<?> slotResult = Class.forName("top.theillusivec4.curios.api.SlotResult");
            getInventory = api.getMethod("getCuriosInventory", LivingEntity.class);
            findFirstCurio = handler.getMethod("findFirstCurio", Predicate.class);
            getCurios = handler.getMethod("getCurios");
            getStacks = stacksHandler.getMethod("getStacks");
            slotResultStack = slotResult.getMethod("stack");
            getRenders = stacksHandler.getMethod("getRenders");
            return true;
        } catch (ReflectiveOperationException e) {
            failed = true;
            EFW.LOGGER.warn("[Bodycam] Curios API not found: {}", e.toString());
            return false;
        }
    }

    private static Object handler(LivingEntity entity) throws ReflectiveOperationException {
        LazyOptional<?> inv = (LazyOptional<?>) getInventory.invoke(null, entity);
        return inv.resolve().orElse(null);
    }

    /** The first curio matching the filter, or EMPTY. */
    public static ItemStack findFirst(LivingEntity entity, Predicate<ItemStack> filter) {
        if (!init()) {
            return ItemStack.EMPTY;
        }
        try {
            Object handler = handler(entity);
            if (handler == null) {
                return ItemStack.EMPTY;
            }
            Optional<?> result = (Optional<?>) findFirstCurio.invoke(handler, filter);
            return result.isPresent() ? (ItemStack) slotResultStack.invoke(result.get()) : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * The first curio matching the filter in a slot that is shown (Curios' eye toggle on the slot), or EMPTY: for what is drawn on the
     * player. What a curio does keeps working with its slot hidden, use {@link #findFirst} for that.
     */
    public static ItemStack findVisible(LivingEntity entity, Predicate<ItemStack> filter) {
        if (!init()) {
            return ItemStack.EMPTY;
        }
        try {
            Object handler = handler(entity);
            if (handler == null) {
                return ItemStack.EMPTY;
            }
            for (Object stacksHandler : ((Map<?, ?>) getCurios.invoke(handler)).values()) {
                IItemHandlerModifiable stacks = (IItemHandlerModifiable) getStacks.invoke(stacksHandler);
                List<?> renders = (List<?>) getRenders.invoke(stacksHandler);
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    boolean shown = i >= renders.size() || !Boolean.FALSE.equals(renders.get(i));
                    if (shown && !stack.isEmpty() && filter.test(stack)) {
                        return stack;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Puts a copy of the stack into the first empty Curios slot of any kind; true when it went in. Only for items of the
     * curios:curio tag, which fits every slot (the 1.12 Baubles TRINKET).
     */
    public static boolean tryEquipAny(LivingEntity entity, ItemStack stack) {
        if (!init()) {
            return false;
        }
        try {
            Object handler = handler(entity);
            if (handler == null) {
                return false;
            }
            for (Object stacksHandler : ((Map<?, ?>) getCurios.invoke(handler)).values()) {
                IItemHandlerModifiable stacks = (IItemHandlerModifiable) getStacks.invoke(stacksHandler);
                for (int i = 0; i < stacks.getSlots(); i++) {
                    if (stacks.getStackInSlot(i).isEmpty()) {
                        stacks.setStackInSlot(i, stack.copy());
                        return true;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            EFW.LOGGER.debug("[Curios] equip failed: {}", e.toString());
        }
        return false;
    }

    /** Puts a copy of the stack into the first empty bodycam curio slot; true when it went in. */
    public static boolean tryEquip(LivingEntity entity, ItemStack stack) {
        if (!init()) {
            return false;
        }
        try {
            Object handler = handler(entity);
            if (handler == null) {
                return false;
            }
            Object stacksHandler = ((Map<?, ?>) getCurios.invoke(handler)).get(SLOT);
            if (stacksHandler == null) {
                return false;
            }
            IItemHandlerModifiable stacks = (IItemHandlerModifiable) getStacks.invoke(stacksHandler);
            for (int i = 0; i < stacks.getSlots(); i++) {
                if (stacks.getStackInSlot(i).isEmpty()) {
                    stacks.setStackInSlot(i, stack.copy());
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            EFW.LOGGER.debug("[Bodycam] curio equip failed: {}", e.toString());
        }
        return false;
    }
}
