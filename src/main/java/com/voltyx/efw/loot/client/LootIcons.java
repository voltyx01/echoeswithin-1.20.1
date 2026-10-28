package com.voltyx.efw.loot.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.LocksCompat;
import com.voltyx.efw.loot.LootContainers;
import com.voltyx.efw.loot.LootParts;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The loupe of the placed items over containers that still have to be searched: one in the middle of the whole object (both
 * halves of a double chest, all the blocks of a Doomsday car), fully opaque up to a few blocks and fading out beyond, never through
 * walls, never over a container a closed lock hangs on (Locks Reforged).
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class LootIcons {
    private static final int RANGE = 6;
    private static final double FULL_DIST = 4.5;
    private static final double MAX_DIST = 6.0;
    private static final int SCAN_TICKS = 10;

    /** One object near the player: its parts and the spot of the loupe. */
    private record Group(List<BlockPos> parts, Vec3 icon) {
    }

    private static final List<Group> GROUPS = new ArrayList<>();
    private static int scanIn;

    private LootIcons() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || --scanIn > 0) {
            return;
        }
        scanIn = SCAN_TICKS;
        GROUPS.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Level level = mc.level;
        BlockPos centre = mc.player.blockPosition();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-RANGE, -RANGE, -RANGE), centre.offset(RANGE, RANGE, RANGE))) {
            if (seen.contains(pos) || !LootContainers.isLootContainer(level.getBlockEntity(pos))) {
                continue;
            }
            List<BlockPos> parts = LootParts.parts(level, pos);
            seen.addAll(parts);
            for (BlockPos part : parts) {
                ContainerLooting.ask(part);
            }
            GROUPS.add(new Group(parts, iconSpot(level, parts)));
        }
    }

    /** The middle of the object's outline (for a double chest the middle between its halves). */
    private static Vec3 iconSpot(Level level, List<BlockPos> parts) {
        AABB box = null;
        for (BlockPos part : parts) {
            VoxelShape shape = level.getBlockState(part).getShape(level, part);
            AABB b = shape.isEmpty() ? new AABB(part) : shape.bounds().move(part);
            box = box == null ? b : box.minmax(b);
        }
        Vec3 c = box.getCenter();
        return c;
    }

    private static boolean wild(Group g) {
        boolean any = false;
        for (BlockPos part : g.parts) {
            Boolean w = ContainerLooting.wild(part);
            if (Boolean.FALSE.equals(w)) {
                return false;
            }
            any |= Boolean.TRUE.equals(w);
        }
        return any;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || GROUPS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Entity view = mc.getCameraEntity();
        if (view == null || mc.level == null) {
            return;
        }
        Camera camera = e.getCamera();
        Vec3 cam = camera.getPosition();
        Vec3 eye = view.getEyePosition(e.getPartialTick());
        BlockPos looked = mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
        BlockPos searching = ContainerLooting.searching();
        PoseStack pose = e.getPoseStack();
        for (Group g : GROUPS) {
            if ((searching != null && g.parts.contains(searching)) || !wild(g) || locked(mc.level, g)) {
                continue;
            }
            boolean hovered = looked != null && g.parts.contains(looked);
            float alpha = hovered ? 1.0f : alpha(mc.level, view, eye, g);
            if (alpha < 0.02f) {
                continue;
            }
            pose.pushPose();
            pose.translate(g.icon.x - cam.x, g.icon.y - cam.y, g.icon.z - cam.z);
            pose.mulPose(camera.rotation());
            float s = hovered ? 0.018f : 0.015f;
            pose.scale(-s, -s, s);
            Loupe.draw(pose.last().pose(), hovered ? 1.0f : 217 / 255.0f, alpha);
            pose.popPose();
        }
    }

    private static boolean locked(Level level, Group g) {
        for (BlockPos part : g.parts) {
            if (LocksCompat.isLocked(level, part)) {
                return true;
            }
        }
        return false;
    }

    /** Fully opaque up to FULL_DIST and fading out to MAX_DIST like the placed items' loupe; nothing through walls. */
    private static float alpha(Level level, Entity view, Vec3 eye, Group g) {
        double distSq = eye.distanceToSqr(g.icon);
        if (distSq > MAX_DIST * MAX_DIST) {
            return 0.0f;
        }
        BlockHitResult los = level.clip(new ClipContext(eye, g.icon, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, view));
        if (los.getType() == HitResult.Type.BLOCK && !g.parts.contains(los.getBlockPos())) {
            return 0.0f;
        }
        double dist = Math.sqrt(distSq);
        return dist > FULL_DIST ? (float) Math.max(0.0, Math.min(1.0, (MAX_DIST - dist) / (MAX_DIST - FULL_DIST))) : 1.0f;
    }
}
