package com.voltyx.efw.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A press of the attack key made by the mod (the chain hook's blow on landing): the same path the key takes, Better Combat included. */
@Mixin(Minecraft.class)
public interface MinecraftAttackInvoker {
    @Invoker("startAttack")
    boolean efw$startAttack();

    @Accessor("missTime")
    void efw$setMissTime(int ticks);
}
