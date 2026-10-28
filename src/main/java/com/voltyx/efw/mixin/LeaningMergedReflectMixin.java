package com.voltyx.efw.mixin;

import com.voltyx.efw.compat.ReflectCache;
import java.lang.reflect.Method;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The same per-frame getMethod lookups of Leaning Or Not live in its own mixin handlers, merged into these vanilla classes
 * (Camera view offset, player hitbox and model offset). Vanilla never calls getMethod here, so a wildcard redirect applied
 * after that mod's mixins only touches its handlers.
 */
@Mixin(value = {Camera.class, LocalPlayer.class, PlayerRenderer.class}, priority = 1500)
public abstract class LeaningMergedReflectMixin {
    @Redirect(method = "*", at = @At(value = "INVOKE",
            target = "Ljava/lang/Class;getMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"), remap = false, require = 0)
    private Method efw$cachedMethod(Class<?> owner, String name, Class<?>[] params) throws NoSuchMethodException {
        return ReflectCache.getMethod(owner, name, params);
    }
}
