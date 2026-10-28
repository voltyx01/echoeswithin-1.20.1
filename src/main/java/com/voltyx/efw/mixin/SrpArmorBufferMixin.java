package com.voltyx.efw.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.voltyx.efw.EFW;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * SRP's armour layer on infected players (SRPLayerBipedArmor) draws into ModelBase.ctxBuffer, but after the body that holds the
 * vertex buffer of some earlier draw, often finished already: "BufferBuilder not started" crashes near an infected wearing armour.
 * Each armour piece gets a live buffer of its own texture instead (SRP only sets the shader texture, which buffered drawing ignores).
 */
@Pseudo
@Mixin(targets = "com.dhanantry.scapeandrunparasites.client.renderer.SRPLayerBipedArmor", remap = false)
public abstract class SrpArmorBufferMixin {
    @Unique
    private static MethodHandle efw$bind;
    @Unique
    private static boolean efw$missing;
    @Unique
    private MultiBufferSource efw$buffers;
    @Unique
    private PoseStack efw$pose;
    @Unique
    private int efw$light;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), remap = false, require = 0)
    private void efw$context(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        efw$pose = pose;
        efw$buffers = buffers;
        efw$light = light;
    }

    @Redirect(method = "renderArmorLayer",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V",
                    remap = true),
            remap = false, require = 0)
    private void efw$armorBuffer(int unit, ResourceLocation texture) {
        RenderSystem.setShaderTexture(unit, texture);
        if (efw$buffers == null || efw$pose == null || texture == null) {
            return;
        }
        MethodHandle bind = efw$bind();
        if (bind == null) {
            return;
        }
        VertexConsumer buffer = efw$buffers.getBuffer(RenderType.armorCutoutNoCull(texture));
        try {
            bind.invoke(efw$pose, buffer, efw$light, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        } catch (Throwable t) {
            efw$missing = true;
            EFW.LOGGER.warn("[SRP armour] cannot bind the armour buffer: {}", t.toString());
        }
    }

    /** ModelBase.bindContext(PoseStack, VertexConsumer, light, overlay, r, g, b, a), looked up once. */
    @Unique
    private static MethodHandle efw$bind() {
        if (efw$bind == null && !efw$missing) {
            try {
                Class<?> base = Class.forName("com.dhanantry.scapeandrunparasites.client.legacy.ModelBase");
                efw$bind = MethodHandles.publicLookup().findStatic(base, "bindContext", MethodType.methodType(void.class, PoseStack.class,
                        VertexConsumer.class, int.class, int.class, float.class, float.class, float.class, float.class));
            } catch (Throwable t) {
                efw$missing = true;
                EFW.LOGGER.warn("[SRP armour] ModelBase.bindContext not found: {}", t.toString());
            }
        }
        return efw$bind;
    }
}
