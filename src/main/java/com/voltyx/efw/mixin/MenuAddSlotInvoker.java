package com.voltyx.efw.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Opens the protected addSlot of every menu (used for the charging slots of the generator). */
@Mixin(AbstractContainerMenu.class)
public interface MenuAddSlotInvoker {
    @Invoker("addSlot")
    Slot efw$addSlot(Slot slot);
}
