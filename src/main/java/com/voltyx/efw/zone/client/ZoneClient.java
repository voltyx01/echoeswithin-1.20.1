package com.voltyx.efw.zone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.voltyx.efw.EFW;
import com.voltyx.efw.zone.QuestZone;
import com.voltyx.efw.zone.ZoneToolItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Zones known to the client, drawn while the marking tool is held (blue: zones, green: the current selection). */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class ZoneClient {
    private static List<QuestZone> zones = new ArrayList<>();

    private ZoneClient() {
    }

    public static synchronized void setZones(List<QuestZone> newZones) {
        zones = newZones != null ? new ArrayList<>(newZones) : new ArrayList<>();
    }

    public static synchronized List<QuestZone> getZones() {
        return Collections.unmodifiableList(zones);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }
        ItemStack tool = player.getMainHandItem().getItem() instanceof ZoneToolItem ? player.getMainHandItem()
                : player.getOffhandItem().getItem() instanceof ZoneToolItem ? player.getOffhandItem() : ItemStack.EMPTY;
        if (tool.isEmpty()) {
            return;
        }
        Vec3 cam = e.getCamera().getPosition();
        PoseStack pose = e.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        String dim = QuestZone.dimensionOf(player.level());

        for (QuestZone zone : getZones()) {
            if (zone.dimension().equals(dim)) {
                box(pose, buffers, zone.aabb(), 0.2F, 0.7F, 1.0F, 0.8F, 0.12F);
            }
        }
        BlockPos p1 = ZoneToolItem.getPos(tool, "Pos1");
        BlockPos p2 = ZoneToolItem.getPos(tool, "Pos2");
        if (p1 != null && p2 != null) {
            AABB sel = new AABB(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()),
                    Math.max(p1.getX(), p2.getX()) + 1.0, Math.max(p1.getY(), p2.getY()) + 1.0, Math.max(p1.getZ(), p2.getZ()) + 1.0);
            box(pose, buffers, sel, 0.1F, 1.0F, 0.3F, 0.9F, 0.18F);
        } else if (p1 != null) {
            LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), new AABB(p1), 1.0F, 0.8F, 0.1F, 0.9F);
        } else if (p2 != null) {
            LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), new AABB(p2), 0.1F, 0.8F, 1.0F, 0.9F);
        }
        buffers.endBatch(RenderType.lines());
        buffers.endBatch(RenderType.debugQuads());
        pose.popPose();
    }

    private static void box(PoseStack pose, MultiBufferSource buffers, AABB bb, float r, float g, float b, float lineA, float fillA) {
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), bb, r, g, b, lineA);
        VertexConsumer q = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f m = pose.last().pose();
        float x0 = (float) bb.minX, y0 = (float) bb.minY, z0 = (float) bb.minZ;
        float x1 = (float) bb.maxX, y1 = (float) bb.maxY, z1 = (float) bb.maxZ;
        quad(q, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, fillA); // bottom
        quad(q, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, fillA); // top
        quad(q, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, fillA); // north
        quad(q, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, fillA); // south
        quad(q, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, fillA); // west
        quad(q, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, fillA); // east
    }

    private static void quad(VertexConsumer q, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float r, float g, float b, float a) {
        q.vertex(m, ax, ay, az).color(r, g, b, a).endVertex();
        q.vertex(m, bx, by, bz).color(r, g, b, a).endVertex();
        q.vertex(m, cx, cy, cz).color(r, g, b, a).endVertex();
        q.vertex(m, dx, dy, dz).color(r, g, b, a).endVertex();
    }
}
