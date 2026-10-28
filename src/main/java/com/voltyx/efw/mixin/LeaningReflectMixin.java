package com.voltyx.efw.mixin;

import com.voltyx.efw.compat.ReflectCache;
import java.lang.reflect.Method;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Leaning Or Not looks its own player accessors up with Class.getMethod every frame; cache them (spark: ~1% of a frame). */
@Pseudo
@Mixin(targets = {"com.raiiiden.leaningornot.client.ClientLeaningData", "com.raiiiden.leaningornot.client.LeaningOrNotClientEvents"},
        remap = false)
public abstract class LeaningReflectMixin {
    @Redirect(method = "*", at = @At(value = "INVOKE",
            target = "Ljava/lang/Class;getMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"), remap = false, require = 0)
    private static Method efw$cachedMethod(Class<?> owner, String name, Class<?>[] params) throws NoSuchMethodException {
        return ReflectCache.getMethod(owner, name, params);
    }
}
