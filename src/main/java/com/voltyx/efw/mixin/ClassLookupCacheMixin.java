package com.voltyx.efw.mixin;

import com.voltyx.efw.compat.ReflectCache;
import java.lang.reflect.Method;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Per-frame class lookups that miss: Camera Inertia probes a Better Combat API class that this Better Combat version does not
 * have (a ClassNotFoundException with a stack trace on every frame), FancyMenu validates screen identifiers by loading them.
 */
@Pseudo
@Mixin(targets = {"starlight_lnk.camerainertia.client.CameraFirstPersonBody",
        "de.keksuccino.fancymenu.customization.screen.identifier.ScreenIdentifierHandler"}, remap = false)
public abstract class ClassLookupCacheMixin {
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Ljava/lang/Class;forName(Ljava/lang/String;)Ljava/lang/Class;"),
            remap = false, require = 0)
    private static Class<?> efw$cachedClass(String name) throws ClassNotFoundException {
        return ReflectCache.forName(name);
    }

    @Redirect(method = "*", at = @At(value = "INVOKE",
            target = "Ljava/lang/Class;forName(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;"), remap = false, require = 0)
    private static Class<?> efw$cachedClass(String name, boolean init, ClassLoader loader) throws ClassNotFoundException {
        return ReflectCache.forName(name, init, loader);
    }

    @Redirect(method = "*", at = @At(value = "INVOKE",
            target = "Ljava/lang/Class;getMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"), remap = false, require = 0)
    private static Method efw$cachedMethod(Class<?> owner, String name, Class<?>[] params) throws NoSuchMethodException {
        return ReflectCache.getMethod(owner, name, params);
    }
}
