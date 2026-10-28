package com.voltyx.efw.gear.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.TaczCompat;
import com.voltyx.efw.gear.WornRingItem;
import com.voltyx.efw.headshot.HeadshotManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The worn ring's spark: while its wearer aims a TACZ gun at a parasite or another player, a gold spark lights up on the target's
 * head (with a quiet chime when it appears) and fades out once the aim leaves it.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class WornRingGlints {
    private static final ResourceLocation SPARK = new ResourceLocation(EFW.MOD_ID, "textures/misc/head_spark.png");
    private static final double RANGE = 96.0D;
    private static final float FADE_IN = 0.2F;
    private static final float FADE_OUT = 0.12F;

    /** Entity id -> {fade of the last tick, fade now}, 0..1. */
    private static final Map<Integer, float[]> SPARKS = new HashMap<>();
    private static boolean wearing;

    private WornRingGlints() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || mc.isPaused()) {
            return;
        }
        Player player = mc.player;
        if (player == null || mc.level == null) {
            SPARKS.clear();
            wearing = false;
            return;
        }
        if (mc.level.getGameTime() % 10L == 0L) {
            wearing = WornRingItem.wears(player);
        }
        LivingEntity aimed = wearing && TaczCompat.isAiming(player) ? aimedTarget(player) : null;
        if (aimed != null && !SPARKS.containsKey(aimed.getId())) {
            SPARKS.put(aimed.getId(), new float[2]);
            Vec3 head = HeadshotManager.getHeadBox(aimed).getCenter();
            mc.level.playLocalSound(head.x, head.y, head.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F,
                    1.7F + player.getRandom().nextFloat() * 0.2F, false);
        }
        Iterator<Map.Entry<Integer, float[]>> it = SPARKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, float[]> entry = it.next();
            float[] fade = entry.getValue();
            fade[0] = fade[1];
            boolean on = aimed != null && aimed.getId() == entry.getKey();
            fade[1] = Mth.clamp(fade[1] + (on ? FADE_IN : -FADE_OUT), 0.0F, 1.0F);
            Entity entity = mc.level.getEntity(entry.getKey());
            if ((fade[0] <= 0.0F && fade[1] <= 0.0F && !on) || !(entity instanceof LivingEntity living) || !living.isAlive()) {
                it.remove();
            }
        }
    }

    /** The parasite or the other player under the crosshair, not behind a wall; null when there is none. */
    private static LivingEntity aimedTarget(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        HitResult block = player.level().clip(new ClipContext(eye, eye.add(look.scale(RANGE)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        double reach = block.getType() == HitResult.Type.MISS ? RANGE : block.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(reach));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D),
                en -> en instanceof LivingEntity living && living.isAlive() && isTarget(living), reach * reach);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static boolean isTarget(LivingEntity entity) {
        return WornRingItem.isParasite(entity) || (entity instanceof Player other && !other.isSpectator());
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || SPARKS.isEmpty() || mc.level == null) {
            return;
        }
        float partial = e.getPartialTick();
        float time = mc.level.getGameTime() + partial;
        Camera camera = e.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = e.getPoseStack();
        BufferBuilder buf = null;
        for (Map.Entry<Integer, float[]> entry : SPARKS.entrySet()) {
            float fade = Mth.lerp(partial, entry.getValue()[0], entry.getValue()[1]);
            if (fade <= 0.0F || !(mc.level.getEntity(entry.getKey()) instanceof LivingEntity living)) {
                continue;
            }
            if (buf == null) {
                buf = Tesselator.getInstance().getBuilder();
                buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            }
            // the head box follows the current position; move it to where the mob is drawn this frame
            Vec3 drawn = living.getPosition(partial);
            Vec3 head = HeadshotManager.getHeadBox(living).getCenter().add(drawn.subtract(living.position()));
            float alpha = fade * (0.8F + 0.2F * Mth.sin(time * 0.35F));
            // keeps a readable size far away; grows in as it appears
            float size = (float) Math.max(0.16D, head.distanceTo(cam) * 0.009D) * (0.6F + 0.4F * fade);
            pose.pushPose();
            pose.translate(head.x - cam.x, head.y - cam.y, head.z - cam.z);
            pose.mulPose(camera.rotation());
            pose.mulPose(Axis.ZP.rotationDegrees(time * 2.0F));
            quad(buf, pose.last().pose(), size * 2.2F, alpha * 0.35F);
            quad(buf, pose.last().pose(), size, alpha);
            pose.popPose();
        }
        if (buf == null) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SPARK);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        // seen through the target's own head; the spark itself only appears on a target in sight
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder buf, Matrix4f m, float s, float alpha) {
        buf.vertex(m, -s, -s, 0.0F).uv(0.0F, 1.0F).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        buf.vertex(m, -s, s, 0.0F).uv(0.0F, 0.0F).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        buf.vertex(m, s, s, 0.0F).uv(1.0F, 0.0F).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        buf.vertex(m, s, -s, 0.0F).uv(1.0F, 1.0F).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
    }
}
