package com.voltyx.efw.mixin;

import com.voltyx.efw.tacz.client.BulletFireClient;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * No flames drawn on an entity set on fire by an incendiary TACZ bullet (it still burns). Only the flame model is skipped:
 * displayFireAnimation itself stays true, so the sparks and smoke other mods give burning entities are still there.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityFireHideMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;displayFireAnimation()Z"))
    private boolean efw$hideBulletFire(Entity entity) {
        return entity.displayFireAnimation() && !BulletFireClient.isHidden(entity);
    }
}
