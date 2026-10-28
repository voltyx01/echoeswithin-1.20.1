package com.voltyx.efw.mixin;

import com.voltyx.efw.search.EnglishNames;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.lang.reflect.Method;

/**
 * REI (the item list, like JEI in 1.12): the text its search matches an entry against is extended with the English name of the item
 * (the 1.12 MixinJEIIngredientFilter). Reached by name, so efw needs no REI on the compile path.
 */
@Pseudo
@Mixin(targets = "me.shedaniel.rei.impl.client.search.argument.type.TextArgumentType", remap = false)
public abstract class ReiEnglishSearchMixin {
    @Inject(method = "cacheData(Lme/shedaniel/rei/api/common/entry/EntryStack;)Ljava/lang/String;", at = @At("RETURN"), cancellable = true, remap = false)
    private void efw$english(@Coerce Object entry, CallbackInfoReturnable<String> cir) {
        try {
            Method getValue = entry.getClass().getMethod("getValue");
            Object value = getValue.invoke(entry);
            if (value instanceof ItemStack stack) {
                String english = EnglishNames.of(stack);
                String shown = cir.getReturnValue();
                if (!english.isEmpty() && (shown == null || !shown.contains(english))) {
                    cir.setReturnValue((shown == null ? "" : shown) + " " + english);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // not an item entry or a different REI build: the normal text is kept
        }
    }
}
