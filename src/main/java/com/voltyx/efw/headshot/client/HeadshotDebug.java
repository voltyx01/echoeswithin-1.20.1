package com.voltyx.efw.headshot.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voltyx.efw.EFW;
import com.voltyx.efw.headshot.HeadshotConfig;
import com.voltyx.efw.headshot.HeadshotManager;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Head box debugging (ports of HeadHitboxDebugger and HitboxTunerTool), active with debugHitboxes = true: red boxes on every
 * mob; sneak + right click a mob with a stick to tune it live (green box), arrows move it, +/- resize, Shift = big steps,
 * Enter prints the config line, Esc drops the mob.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class HeadshotDebug {
    public static LivingEntity activeTarget;
    public static double tuneY = 1.0;
    public static double tuneForward = 0.0;
    public static double tuneRadius = 0.25;

    private HeadshotDebug() {
    }

    private static boolean enabled() {
        return HeadshotConfig.DEBUG_HITBOXES.get();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !enabled() || mc.level == null) {
            return;
        }
        Vec3 cam = e.getCamera().getPosition();
        PoseStack pose = e.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
                continue; // not in the face
            }
            if (living == activeTarget) {
                LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), liveTunedBox(), 0.0F, 1.0F, 0.0F, 1.0F);
            } else {
                LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), HeadshotManager.getHeadBox(living), 1.0F, 0.0F, 0.0F, 1.0F);
            }
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!e.getLevel().isClientSide || !enabled() || !e.getEntity().isShiftKeyDown() || !e.getItemStack().is(Items.STICK)
                || !(e.getTarget() instanceof LivingEntity living)) {
            return;
        }
        activeTarget = living;
        tuneY = living.getBbHeight() / 2.0;
        tuneForward = 0.0;
        tuneRadius = 0.25;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(living.getType());
        say("§d[Тюнер] §aМоб выбран: §e" + (id != null ? id : "Неизвестно"));
        say("§7Используй СТРЕЛОЧКИ для перемещения. ПЛЮС/МИНУС для размера. ENTER для сохранения.");
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        if (activeTarget == null || !activeTarget.isAlive() || e.getAction() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) {
            return;
        }
        double step = (e.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 0.1 : 0.02;
        switch (e.getKey()) {
            case GLFW.GLFW_KEY_UP -> tuneY += step;
            case GLFW.GLFW_KEY_DOWN -> tuneY -= step;
            case GLFW.GLFW_KEY_RIGHT -> tuneForward += step;
            case GLFW.GLFW_KEY_LEFT -> tuneForward -= step;
            case GLFW.GLFW_KEY_KP_ADD, GLFW.GLFW_KEY_EQUAL -> tuneRadius += step;
            case GLFW.GLFW_KEY_KP_SUBTRACT, GLFW.GLFW_KEY_MINUS -> tuneRadius = Math.max(0.05, tuneRadius - step);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(activeTarget.getType());
                String line = String.format(Locale.US, "%s, %.2f, %.2f, %.2f", id != null ? id : "unknown:mob", tuneForward, tuneY, tuneRadius);
                say("§a[КОНФИГ СТРОКА] §f(Скопируй из консоли логов):");
                say("§e" + line);
                EFW.LOGGER.info("====== СКОПИРУЙ ЭТУ СТРОКУ В КОНФИГ ======");
                EFW.LOGGER.info(line);
                EFW.LOGGER.info("==========================================");
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                activeTarget = null;
                say("§c[Тюнер] Выделение сброшено.");
            }
            default -> {
            }
        }
    }

    public static AABB liveTunedBox() {
        return HeadshotManager.box(activeTarget, tuneForward, tuneY, tuneRadius);
    }

    private static void say(String text) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(text));
        }
    }
}
