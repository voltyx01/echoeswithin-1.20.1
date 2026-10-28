package com.voltyx.efw.speech.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.voltyx.efw.EFW;
import com.voltyx.efw.speech.SpeechConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** The speech bubbles above the heads of the speakers (mwccf SpeechWorldRenderer): a stretched vanilla button frame with typed text. */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class SpeechWorldRenderer {
    private static final ResourceLocation BUTTONS = new ResourceLocation("textures/gui/widgets.png");
    private static final int FULL_BRIGHT = 15728880;

    private SpeechWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float pt = event.getPartialTick();
        long now = System.currentTimeMillis();
        for (SpeechClientManager.SpeechBubble bubble : SpeechClientManager.getBubbles()) {
            Entity entity = mc.level.getEntity(bubble.entityId);
            if (!(entity instanceof LivingEntity) || entity.isRemoved()) {
                continue;
            }
            float distance = mc.player.distanceTo(entity);
            float hidden = SpeechConfig.publicHiddenDistance();
            if (distance >= hidden) {
                continue;
            }
            float full = SpeechConfig.publicFullAlphaDistance();
            float distanceAlpha = distance <= full ? 1.0F : 1.0F - (distance - full) / Math.max(0.001F, hidden - full);
            float alpha = Mth.clamp(distanceAlpha * getLifeAlpha(bubble, now), 0.0F, 1.0F);
            if (alpha <= 0.005F) {
                continue;
            }
            double x = Mth.lerp(pt, entity.xo, entity.getX()) - cam.x;
            double y = Mth.lerp(pt, entity.yo, entity.getY()) + entity.getBbHeight() + SpeechConfig.publicBubbleHeight()
                    + getStackLevel(bubble, entity, mc.level) * 0.75D - cam.y;
            double z = Mth.lerp(pt, entity.zo, entity.getZ()) - cam.z;
            renderBubble(mc, bubble, event.getPoseStack(), camera, x, y, z, alpha, now);
        }
    }

    private static int getStackLevel(SpeechClientManager.SpeechBubble bubble, Entity entity, Level level) {
        int stack = 0;
        for (SpeechClientManager.SpeechBubble other : SpeechClientManager.getBubbles()) {
            if (other.entityId >= bubble.entityId) {
                continue;
            }
            Entity otherEntity = level.getEntity(other.entityId);
            if (otherEntity == null || otherEntity.isRemoved()) {
                continue;
            }
            double dx = entity.getX() - otherEntity.getX();
            double dz = entity.getZ() - otherEntity.getZ();
            if (dx * dx + dz * dz < 2.25D) {
                stack++;
            }
        }
        return Math.min(4, stack);
    }

    private static float getLifeAlpha(SpeechClientManager.SpeechBubble bubble, long now) {
        if (bubble.isTyping()) {
            return 1.0F;
        }
        int fade = SpeechConfig.publicFadeMs();
        long fadeStart = bubble.expiresAt - fade;
        if (now <= fadeStart) {
            return 1.0F;
        }
        return Mth.clamp((bubble.expiresAt - now) / (float) Math.max(1, fade), 0.0F, 1.0F);
    }

    private static void renderBubble(Minecraft mc, SpeechClientManager.SpeechBubble bubble, PoseStack pose, Camera camera,
                                     double x, double y, double z, float alpha, long now) {
        Font font = mc.font;
        int maxTextWidth = SpeechConfig.publicMaxTextWidth();
        int maxVisualLines = Math.max(1, (SpeechConfig.publicMaxBubbleHeight() - 8) / 10);
        List<String> fullLines = limitLines(wrapText(font, bubble.getFullText(), maxTextWidth), maxVisualLines);
        List<String> visibleLines = limitLines(wrapText(font, bubble.getVisibleText(), maxTextWidth), maxVisualLines);
        int targetWidth = Math.max(38, Math.min(maxTextWidth + 12, maxLineWidth(font, fullLines, maxTextWidth) + 12));
        int targetHeight = Math.min(SpeechConfig.publicMaxBubbleHeight(), Math.max(20, fullLines.size() * 10 + 8));
        float widthProgress = bubble.getWidthProgress(now);
        float heightProgress = bubble.heightAnimationEnd <= bubble.heightAnimationStart || now >= bubble.heightAnimationEnd ? 1.0F
                : Mth.clamp((now - bubble.heightAnimationStart) / (float) (bubble.heightAnimationEnd - bubble.heightAnimationStart), 0.0F, 1.0F);
        int width = Math.max(1, Math.round(bubble.widthAnimationFrom + (targetWidth - bubble.widthAnimationFrom) * widthProgress));
        int height = Math.max(12, Math.round(bubble.heightAnimationFrom + (targetHeight - bubble.heightAnimationFrom) * heightProgress));
        bubble.renderedWidth = width;
        bubble.renderedHeight = height;

        float scale = SpeechConfig.publicBubbleScale();
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(camera.rotation());
        pose.translate(SpeechConfig.publicBubbleXOffset(), SpeechConfig.publicBubbleYOffset(), 0.0F);
        pose.scale(-scale, -scale, scale);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, BUTTONS);
        drawNineSlice(pose.last().pose(), -width / 2, -height, width, height, alpha);

        if (alpha >= 0.25F) {
            pose.translate(0.0F, 0.0F, 0.3F); // a hair toward the viewer, over the frame
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            int textColor = (Math.max(0, Math.min(255, (int) (alpha * 255.0F))) << 24) | 0x00FFFF55;
            int textY = -height + 4;
            for (String line : visibleLines) {
                String clean = ChatFormatting.stripFormatting(line);
                font.drawInBatch(clean == null ? "" : clean, -width / 2 + 6, textY, textColor, false, pose.last().pose(), buffers,
                        Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
                textY += 10;
            }
            buffers.endBatch();
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void drawNineSlice(Matrix4f m, int x, int y, int width, int height, float alpha) {
        int edgeX = Math.min(4, width / 2);
        int edgeY = Math.min(4, height / 2);
        int middleWidth = Math.max(0, width - edgeX * 2);
        int middleHeight = Math.max(0, height - edgeY * 2);
        int sourceMiddleWidth = 200 - 8;
        int sourceMiddleHeight = 20 - 8;
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        patch(buf, m, alpha, x, y, edgeX, edgeY, 0, 46, 4, 4);
        patch(buf, m, alpha, x + edgeX, y, middleWidth, edgeY, 4, 46, sourceMiddleWidth, 4);
        patch(buf, m, alpha, x + edgeX + middleWidth, y, edgeX, edgeY, 196, 46, 4, 4);
        patch(buf, m, alpha, x, y + edgeY, edgeX, middleHeight, 0, 50, 4, sourceMiddleHeight);
        patch(buf, m, alpha, x + edgeX, y + edgeY, middleWidth, middleHeight, 4, 50, sourceMiddleWidth, sourceMiddleHeight);
        patch(buf, m, alpha, x + edgeX + middleWidth, y + edgeY, edgeX, middleHeight, 196, 50, 4, sourceMiddleHeight);
        patch(buf, m, alpha, x, y + edgeY + middleHeight, edgeX, edgeY, 0, 62, 4, 4);
        patch(buf, m, alpha, x + edgeX, y + edgeY + middleHeight, middleWidth, edgeY, 4, 62, sourceMiddleWidth, 4);
        patch(buf, m, alpha, x + edgeX + middleWidth, y + edgeY + middleHeight, edgeX, edgeY, 196, 62, 4, 4);
        BufferUploader.drawWithShader(buf.end());
    }

    private static void patch(BufferBuilder buf, Matrix4f m, float alpha, int x, int y, int width, int height,
                              int sourceX, int sourceY, int sourceWidth, int sourceHeight) {
        if (width <= 0 || height <= 0) {
            return;
        }
        float minU = sourceX / 256.0F;
        float maxU = (sourceX + sourceWidth) / 256.0F;
        float minV = sourceY / 256.0F;
        float maxV = (sourceY + sourceHeight) / 256.0F;
        buf.vertex(m, x, y + height, 0.0F).uv(minU, maxV).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        buf.vertex(m, x + width, y + height, 0.0F).uv(maxU, maxV).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        buf.vertex(m, x + width, y, 0.0F).uv(maxU, minV).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        buf.vertex(m, x, y, 0.0F).uv(minU, minV).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
    }

    private static int maxLineWidth(Font font, List<String> lines, int limit) {
        int max = 0;
        for (String line : lines) {
            max = Math.max(max, font.width(line));
        }
        return Math.min(limit, max);
    }

    private static List<String> wrapText(Font font, String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        for (String paragraph : text.split("\\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
            } else {
                font.getSplitter().splitLines(paragraph, width, net.minecraft.network.chat.Style.EMPTY)
                        .forEach(part -> lines.add(part.getString()));
            }
        }
        return lines;
    }

    private static List<String> limitLines(List<String> lines, int maxLines) {
        if (lines.size() <= maxLines) {
            return lines;
        }
        return new ArrayList<>(lines.subList(lines.size() - maxLines, lines.size()));
    }
}
