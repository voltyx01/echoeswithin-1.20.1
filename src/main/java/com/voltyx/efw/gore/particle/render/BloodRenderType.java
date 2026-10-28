package com.voltyx.efw.gore.particle.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.voltyx.efw.gore.particle.LegacyParticle;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;


import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.blocksupport.PistonSupport;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.render.BloodRender;
import com.voltyx.efw.gore.particle.render.parts.BloodLavaRendering;
import com.voltyx.efw.gore.particle.state.BloodHotBlocks;
import com.voltyx.efw.gore.particle.state.BloodMagic;
import com.voltyx.efw.gore.particle.state.liquid.lava.BloodLava;
import com.voltyx.efw.gore.particle.state.liquid.water.BloodWater;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.voltyx.efw.gore.particle.common.GoreMaterial;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import com.voltyx.efw.gore.particle.render.BufferBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
public final class BloodRenderType {
    private static final Set<ParticleBlood> DECALS = Collections.newSetFromMap(new IdentityHashMap());
    private static final Set<BloodWater> WATER_TRACKED = Collections.newSetFromMap(new IdentityHashMap());
    private static final Set<BloodWater> WATER_DROPLET_QUEUE = Collections.newSetFromMap(new IdentityHashMap());
    private static final Set<BloodLava> LAVA_TRACKED = Collections.newSetFromMap(new IdentityHashMap());
    private static final Set<BloodLava> LAVA_BILLBOARD_QUEUE = Collections.newSetFromMap(new IdentityHashMap());
    private static final Set<ParticleBlood> MAGIC_BILLBOARD_QUEUE = Collections.newSetFromMap(new IdentityHashMap());
    private static final Set<ParticleBlood> BLOOD_BILLBOARD_QUEUE = Collections.newSetFromMap(new IdentityHashMap());
    private static final float ALPHA_REF_DEFAULT = 0.003921569f;
    private static final float ALPHA_REF_SOFT = 4.8828125E-4f;
    private static final float MAGIC_BASE_DARK_MUL = 0.35f;
    private static final float MAGIC_OVERLAY_ALPHA_MUL = 0.85f;
    private static final float MAGIC_ALPHA_EPS = 1.0E-6f;
    private static final float VISIBLE_ALPHA_EPS = 0.001f;

    public static void track(@Nullable ParticleBlood p) {
        if (p != null) {
            DECALS.add(p);
        }
    }

    public static void trackWater(@Nullable BloodWater p) {
        if (p != null) {
            WATER_TRACKED.add(p);
        }
    }

    public static void untrackWater(@Nullable BloodWater p) {
        if (p != null) {
            WATER_TRACKED.remove((Object)p);
        }
    }

    public static void trackLava(@Nullable BloodLava p) {
        if (p != null) {
            LAVA_TRACKED.add(p);
        }
    }

    public static void untrackLava(@Nullable BloodLava p) {
        if (p != null) {
            LAVA_TRACKED.remove((Object)p);
        }
    }

    public static void queueWaterDroplet(@Nullable BloodWater p) {
        if (p != null) {
            WATER_DROPLET_QUEUE.add(p);
        }
    }

    public static void queueMagicBillboard(@Nullable ParticleBlood p) {
        if (p != null) {
            MAGIC_BILLBOARD_QUEUE.add(p);
        }
    }

    public static void queueLavaBillboard(@Nullable BloodLava p) {
        if (p != null) {
            LAVA_BILLBOARD_QUEUE.add(p);
        }
    }

    public static void queueBloodBillboard(@Nullable ParticleBlood p) {
        if (p != null) {
            BLOOD_BILLBOARD_QUEUE.add(p);
        }
    }

    /**
     * 1.12 drew the decals, the queued billboards, the water and lava blood after the world (RenderWorldLastEvent); here right after
     * the particles, in one sorted batch: first what the particles drew themselves during the particle pass, then the batch.
     */
    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel world = mc.level;
        Camera camera = event.getCamera();
        Entity view = camera.getEntity();
        if (world == null || view == null) {
            BufferBuilder.PARTICLES.clear();
            BloodRenderType.clearFrameQueues();
            return;
        }
        boolean particlePass = !BufferBuilder.PARTICLES.isEmpty();
        if (!particlePass && !BloodRenderType.hasPendingWorldLastWork()) {
            BufferBuilder.PARTICLES.clear();
            return;
        }
        double oldInterpX = ParticleBlood.getInterpX();
        double oldInterpY = ParticleBlood.getInterpY();
        double oldInterpZ = ParticleBlood.getInterpZ();
        Matrix4f pose = event.getPoseStack().last().pose();
        try {
            float partialTicks = event.getPartialTick();
            Vec3 cam = camera.getPosition();
            double camX = cam.x;
            double camY = cam.y;
            double camZ = cam.z;
            boolean viewUnderwater = camera.getFluidInCamera() == FogType.WATER;
            LegacyParticle.setCamera(camera);
            BloodRenderType.setSharedParticleInterp(camX, camY, camZ);
            BloodRenderType.applyWorldLastState(mc);
            try {
                if (particlePass) {
                    BufferBuilder.PARTICLES.draw(pose);
                }
                ArrayList<RenderEntry> entries = BloodRenderType.collectRenderEntries((Level)world, camX, camY, camZ, viewUnderwater, partialTicks, view);
                if (!entries.isEmpty()) {
                    BloodRenderType.sortRenderEntries(entries);
                    float[] r = LegacyParticle.billboard(camera);
                    BillboardRotations fallbackRotations = new BillboardRotations(r[0], r[1], r[2], r[3], r[4]);
                    BufferBuilder buffer = BufferBuilder.WORLD_LAST;
                    buffer.clear();
                    BloodRenderType.renderEntries(entries, buffer, view, partialTicks, viewUnderwater, camX, camY, camZ, fallbackRotations);
                    buffer.draw(pose);
                }
            }
            finally {
                BloodRenderType.restoreWorldLastState(mc);
            }
        }
        finally {
            BufferBuilder.PARTICLES.clear();
            BloodRenderType.clearFrameQueues();
            BloodRenderType.setSharedParticleInterp(oldInterpX, oldInterpY, oldInterpZ);
        }
    }



    private static void clearFrameQueues() {
        for (ParticleBlood blood : BLOOD_BILLBOARD_QUEUE) {
            if (blood == null) continue;
            blood.clearQueuedBillboard();
        }
        for (BloodWater water : WATER_TRACKED) {
            if (water == null) continue;
            water.clearQueuedBillboard();
        }
        for (BloodWater water : WATER_DROPLET_QUEUE) {
            if (water == null) continue;
            water.clearQueuedBillboard();
        }
        for (BloodLava lava : LAVA_TRACKED) {
            if (lava == null) continue;
            lava.clearQueuedBillboard();
        }
        for (BloodLava lava : LAVA_BILLBOARD_QUEUE) {
            if (lava == null) continue;
            lava.clearQueuedBillboard();
        }
        for (ParticleBlood magic : MAGIC_BILLBOARD_QUEUE) {
            if (magic == null) continue;
            magic.clearQueuedBillboard();
        }
        BLOOD_BILLBOARD_QUEUE.clear();
        WATER_DROPLET_QUEUE.clear();
        LAVA_BILLBOARD_QUEUE.clear();
        MAGIC_BILLBOARD_QUEUE.clear();
    }

    private static boolean hasPendingWorldLastWork() {
        return !DECALS.isEmpty() || !BLOOD_BILLBOARD_QUEUE.isEmpty() || !WATER_TRACKED.isEmpty() || !WATER_DROPLET_QUEUE.isEmpty() || !LAVA_TRACKED.isEmpty() || !LAVA_BILLBOARD_QUEUE.isEmpty() || !MAGIC_BILLBOARD_QUEUE.isEmpty();
    }

    private static double interp(double prev, double current, float partialTicks) {
        return prev + (current - prev) * (double)partialTicks;
    }

    private static void setSharedParticleInterp(double x, double y, double z) {
        ParticleBlood.setInterp(x, y, z);
        BloodWater.setCamera(x, y, z);
    }



    /** Blindness: nothing further than the fog of it (1.12 drew the fog by hand; 1.20's shaders have the fog already). */
    private static double getBlindnessMaxDistSq(@Nullable Entity view) {
        if (view instanceof LivingEntity living) {
            MobEffectInstance effect = living.getEffect(MobEffects.BLINDNESS);
            if (effect != null) {
                float f1 = 5.0F;
                if (effect.getDuration() < 20) {
                    float far = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F;
                    f1 = 5.0F + (far - 5.0F) * (1.0F - (float) effect.getDuration() / 20.0F);
                }
                return (double) (f1 * f1);
            }
        }
        return Double.MAX_VALUE;
    }

    @Nonnull
    private static ArrayList<RenderEntry> collectRenderEntries(@Nonnull Level world, double camX, double camY, double camZ, boolean viewUnderwater, float partialTicks, @Nullable Entity view) {
        int estimate = DECALS.size() + (viewUnderwater ? 0 : BLOOD_BILLBOARD_QUEUE.size()) + WATER_TRACKED.size() + LAVA_TRACKED.size() + LAVA_BILLBOARD_QUEUE.size() + (viewUnderwater ? 0 : MAGIC_BILLBOARD_QUEUE.size()) + (viewUnderwater ? WATER_DROPLET_QUEUE.size() : 0);
        ArrayList<RenderEntry> out = new ArrayList<RenderEntry>(estimate);
        double maxDistSq = getBlindnessMaxDistSq(view);
        BloodRenderType.collectDecalEntries(out, world, camX, camY, camZ, partialTicks, maxDistSq);
        if (!viewUnderwater) {
            BloodRenderType.collectBloodBillboardEntries(out, world, camX, camY, camZ, maxDistSq);
            BloodRenderType.collectMagicEntries(out, world, camX, camY, camZ, maxDistSq);
        }
        BloodRenderType.collectWaterEntries(out, world, camX, camY, camZ, viewUnderwater, maxDistSq);
        BloodRenderType.collectLavaEntries(out, world, camX, camY, camZ, maxDistSq);
        return out;
    }

    private static void collectBloodBillboardEntries(@Nonnull List<RenderEntry> out, @Nonnull Level world, double camX, double camY, double camZ, double maxDistSq) {
        for (ParticleBlood p : BLOOD_BILLBOARD_QUEUE) {
            if (!BloodRenderType.isBloodBillboardQueuedValid(p, world)) continue;
            double distSq = p.getDistanceSqTo(camX, camY, camZ);
            if (distSq > maxDistSq) continue;
            out.add(RenderEntry.bloodBillboard(distSq, p));
        }
    }

    private static boolean isBloodBillboardQueuedValid(@Nullable ParticleBlood p, @Nonnull Level world) {
        return p != null && p.isAlive() && p.getParticleWorld() == world && !p.isStuck && p.getAlpha() > 0.001f;
    }

    private static void collectDecalEntries(@Nonnull List<RenderEntry> out, @Nonnull Level world, double camX, double camY, double camZ, float partialTicks, double maxDistSq) {
        Iterator<ParticleBlood> it = DECALS.iterator();
        while (it.hasNext()) {
            ParticleBlood p = it.next();
            if (!BloodRenderType.isTrackedDecalValid(p, world)) {
                it.remove();
                continue;
            }
            double sortDistSq = p.isStuck && p.stuckFace == Direction.DOWN ? BloodRenderType.computeCeilingDecalSortDistanceSq(p, camX, camY, camZ, partialTicks) : p.getDistanceSqTo(camX, camY, camZ);
            if (sortDistSq > maxDistSq) continue;
            out.add(RenderEntry.decal(sortDistSq, p));
        }
    }

    private static double computeCeilingDecalSortDistanceSq(@Nonnull ParticleBlood p, double camX, double camY, double camZ, float partialTicks) {
        PistonSupport.MovingInfo miNow;
        if (p.cache.shape.polys == null || p.cache.shape.polys.isEmpty()) {
            return p.getDistanceSqTo(camX, camY, camZ);
        }
        double ix = p.prevPosX + (p.posX - p.prevPosX) * (double)partialTicks;
        double iy = p.prevPosY + (p.posY - p.prevPosY) * (double)partialTicks;
        double iz = p.prevPosZ + (p.posZ - p.prevPosZ) * (double)partialTicks;
        double baseX = Double.isNaN(p.cache.view.x) ? p.posX : p.cache.view.x;
        double baseY = Double.isNaN(p.cache.view.y) ? p.posY : p.cache.view.y;
        double baseZ = Double.isNaN(p.cache.view.z) ? p.posZ : p.cache.view.z;
        double dx = ix - baseX;
        double dy = iy - baseY;
        double dz = iz - baseZ;
        Level world = p.getParticleWorld();
        if (p.isStuck && p.stuckPos != null && world != null && (miNow = PistonSupport.getMovingInfo(world, p.stuckPos, partialTicks, p)) != null && miNow.offset != null && !miNow.staticBaseNoOffset) {
            PistonSupport.MovingInfo miBuild = PistonSupport.getMovingInfo(world, p.stuckPos, 1.0f, p);
            Vec3 offNow = miNow.offset;
            Vec3 offBuild = miBuild != null && miBuild.offset != null ? miBuild.offset : Util.ZERO;
            dx = offNow.x - offBuild.x;
            dy = offNow.y - offBuild.y;
            dz = offNow.z - offBuild.z;
        }
        double best = Double.POSITIVE_INFINITY;
        for (List<Util.Vertex> poly : p.cache.shape.polys) {
            if (poly == null) continue;
            for (Util.Vertex v : poly) {
                double vz;
                double vy;
                double vx;
                double dsq;
                if (v == null || !((dsq = (vx = v.x + dx - camX) * vx + (vy = v.y + dy - camY) * vy + (vz = v.z + dz - camZ) * vz) < best)) continue;
                best = dsq;
            }
        }
        return best != Double.POSITIVE_INFINITY ? best : p.getDistanceSqTo(camX, camY, camZ);
    }

    private static void collectWaterEntries(@Nonnull List<RenderEntry> out, @Nonnull Level world, double camX, double camY, double camZ, boolean viewUnderwater, double maxDistSq) {
        Iterator<BloodWater> it = WATER_TRACKED.iterator();
        while (it.hasNext()) {
            BloodWater water = it.next();
            if (!BloodRenderType.isWaterTrackedValid(water, world)) {
                it.remove();
                continue;
            }
            double distSq = water.getDistanceSqTo(camX, camY, camZ);
            if (distSq > maxDistSq) continue;
            if (water.isSurfaceDecal()) {
                out.add(RenderEntry.water(distSq, water, RenderKind.WATER_SURFACE));
                continue;
            }
            if (!viewUnderwater || !water.isFloorDecal()) continue;
            out.add(RenderEntry.water(distSq, water, RenderKind.WATER_FLOOR));
        }
        if (!viewUnderwater) {
            return;
        }
        for (BloodWater water : WATER_DROPLET_QUEUE) {
            if (!BloodRenderType.isWaterDropletQueuedValid(water, world)) continue;
            double distSq = water.getDistanceSqTo(camX, camY, camZ);
            if (distSq > maxDistSq) continue;
            out.add(RenderEntry.water(distSq, water, RenderKind.WATER_DROPLET));
        }
    }

    private static void collectLavaEntries(@Nonnull List<RenderEntry> out, @Nonnull Level world, double camX, double camY, double camZ, double maxDistSq) {
        Iterator<BloodLava> it = LAVA_TRACKED.iterator();
        while (it.hasNext()) {
            BloodLava lava = it.next();
            if (!BloodRenderType.isLavaTrackedValid(lava, world)) {
                it.remove();
                continue;
            }
            double distSq = lava.getDistanceSqTo(camX, camY, camZ);
            if (distSq > maxDistSq) continue;
            if (!lava.isSurfaceDecal()) continue;
            out.add(RenderEntry.lava(distSq, lava, RenderKind.LAVA_SURFACE));
        }
        for (BloodLava lava : LAVA_BILLBOARD_QUEUE) {
            if (!BloodRenderType.isLavaBillboardQueuedValid(lava, world)) continue;
            double distSq = lava.getDistanceSqTo(camX, camY, camZ);
            if (distSq > maxDistSq) continue;
            out.add(RenderEntry.lava(distSq, lava, RenderKind.LAVA_BILLBOARD));
        }
    }

    private static void collectMagicEntries(@Nonnull List<RenderEntry> out, @Nonnull Level world, double camX, double camY, double camZ, double maxDistSq) {
        for (ParticleBlood p : MAGIC_BILLBOARD_QUEUE) {
            if (!BloodRenderType.isMagicBillboardQueuedValid(p, world)) continue;
            double distSq = p.getDistanceSqTo(camX, camY, camZ);
            if (distSq > maxDistSq) continue;
            out.add(RenderEntry.magic(distSq, p));
        }
    }

    private static boolean isTrackedDecalValid(@Nullable ParticleBlood p, @Nonnull Level world) {
        return p != null && p.isAlive() && p.getParticleWorld() == world && p.isStuck;
    }

    private static boolean isWaterTrackedValid(@Nullable BloodWater p, @Nonnull Level world) {
        return p != null && p.isAlive() && p.getParticleWorld() == world;
    }

    private static boolean isWaterDropletQueuedValid(@Nullable BloodWater p, @Nonnull Level world) {
        return p != null && p.isAlive() && p.getParticleWorld() == world && !p.isSurfaceDecal() && !p.isFloorDecal();
    }

    private static boolean isLavaTrackedValid(@Nullable BloodLava p, @Nonnull Level world) {
        return p != null && p.isAlive() && p.getParticleWorld() == world;
    }

    private static boolean isLavaBillboardQueuedValid(@Nullable BloodLava p, @Nonnull Level world) {
        return p != null && p.isAlive() && p.getParticleWorld() == world && p.getParticleAlpha() > 0.001f;
    }

    private static boolean isMagicBillboardQueuedValid(@Nullable ParticleBlood p, @Nonnull Level world) {
        return p != null && p.isAlive() && p.getParticleWorld() == world && !p.isStuckDecal() && BloodMagic.isMagic(p) && p.getAlpha() > 0.001f;
    }

    private static void sortRenderEntries(@Nonnull ArrayList<RenderEntry> entries) {
        entries.sort((a, b) -> {
            int distCmp = Double.compare(b.distSq, a.distSq);
            if (distCmp != 0) {
                return distCmp;
            }
            return Integer.compare(a.kind.sortKey, b.kind.sortKey);
        });
    }



    /**
     * The state of 1.12's batch: alpha blending, depth tested without writing, both sides, the block atlas, the light map. The
     * translucent terrain shader takes the block format with the light map and the fog and does not cut low alpha (1.12 had the
     * alpha test almost off).
     */
    private static void applyWorldLastState(@Nonnull Minecraft mc) {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.setShader(GameRenderer::getRendertypeTranslucentShader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        ShaderInstance shader = RenderSystem.getShader();
        if (shader != null && shader.CHUNK_OFFSET != null) {
            shader.CHUNK_OFFSET.set(0.0f, 0.0f, 0.0f);
        }
    }

    private static void restoreWorldLastState(@Nonnull Minecraft mc) {
        mc.gameRenderer.lightTexture().turnOffLightLayer();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void renderEntries(@Nonnull List<RenderEntry> entries, @Nonnull BufferBuilder buffer, @Nonnull Entity view, float partialTicks, boolean viewUnderwater, double camX, double camY, double camZ, @Nonnull BillboardRotations fallbackRotations) {
        block9: for (RenderEntry entry : entries) {
            if (entry == null) continue;
            BloodRenderType.setSharedParticleInterp(camX, camY, camZ);
            switch (entry.kind) {
                case WATER_SURFACE: {
                    BloodRenderType.renderWaterSurfaceEntry(entry.water, buffer, view, partialTicks, viewUnderwater);
                    continue block9;
                }
                case WATER_FLOOR: {
                    BloodRenderType.renderWaterFloorEntry(entry.water, buffer, view, partialTicks, viewUnderwater);
                    continue block9;
                }
                case WATER_DROPLET: {
                    if (entry.water == null) continue block9;
                    entry.water.renderQueuedDroplet(buffer, view, partialTicks);
                    continue block9;
                }
                case LAVA_SURFACE: {
                    if (entry.lava == null) continue block9;
                    entry.lava.renderSurfaceDecal(buffer, partialTicks);
                    continue block9;
                }
                case LAVA_BILLBOARD: {
                    if (entry.lava == null) continue block9;
                    BloodRenderType.renderQueuedLavaBillboard(entry.lava, buffer, view, partialTicks, camX, camY, camZ, fallbackRotations);
                    continue block9;
                }
                case BLOOD_BILLBOARD: {
                    if (entry.blood == null) continue block9;
                    BloodRenderType.renderQueuedBloodBillboard(entry.blood, buffer, view, partialTicks, camX, camY, camZ, fallbackRotations);
                    continue block9;
                }
                case MAGIC_BILLBOARD: {
                    if (entry.blood == null) continue block9;
                    BloodRenderType.renderQueuedMagicBillboard(entry.blood, buffer, view, partialTicks, camX, camY, camZ, fallbackRotations);
                    continue block9;
                }
            }
            if (entry.blood == null) continue;
            entry.blood.renderStuckDecal(buffer, partialTicks);
        }
    }

    private static void renderQueuedBloodBillboard(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, @Nonnull Entity view, float partialTicks, double camX, double camY, double camZ, @Nonnull BillboardRotations fallbackRotations) {
        if (!p.isAlive() || p.getAlpha() <= 0.001f || p.isStuck) {
            p.clearQueuedBillboard();
            return;
        }
        if (p.hasQueuedBillboard()) {
            BloodRenderType.setSharedParticleInterp(p.getQueuedInterpX(), p.getQueuedInterpY(), p.getQueuedInterpZ());
            BloodRender.renderQueuedBloodParticle(p, buffer, view, partialTicks, p.getQueuedRotX(), p.getQueuedRotZ(), p.getQueuedRotYZ(), p.getQueuedRotXY(), p.getQueuedRotXZ());
            p.clearQueuedBillboard();
            return;
        }
        BloodRenderType.setSharedParticleInterp(camX, camY, camZ);
        BloodRender.renderQueuedBloodParticle(p, buffer, view, partialTicks, fallbackRotations.rotationX, fallbackRotations.rotationZ, fallbackRotations.rotationYZ, fallbackRotations.rotationXY, fallbackRotations.rotationXZ);
        p.clearQueuedBillboard();
    }

    private static void renderWaterSurfaceEntry(@Nullable BloodWater water, @Nonnull BufferBuilder buffer, @Nonnull Entity view, float partialTicks, boolean viewUnderwater) {
        if (water == null) {
            return;
        }
        if (viewUnderwater) {
            water.renderQueuedDroplet(buffer, view, partialTicks);
        }
        water.renderSurfaceDecal(buffer, partialTicks);
    }

    private static void renderWaterFloorEntry(@Nullable BloodWater water, @Nonnull BufferBuilder buffer, @Nonnull Entity view, float partialTicks, boolean viewUnderwater) {
        if (water == null) {
            return;
        }
        if (viewUnderwater) {
            water.renderQueuedDroplet(buffer, view, partialTicks);
        }
        water.renderFloorDecal(buffer, partialTicks);
    }

    private static void renderQueuedMagicBillboard(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, @Nonnull Entity view, float partialTicks, double camX, double camY, double camZ, @Nonnull BillboardRotations fallbackRotations) {
        if (!p.isAlive() || !BloodMagic.isMagic(p) || p.getAlpha() <= 0.001f) {
            p.clearQueuedBillboard();
            return;
        }
        if (p.hasQueuedBillboard()) {
            BloodRenderType.setSharedParticleInterp(p.getQueuedInterpX(), p.getQueuedInterpY(), p.getQueuedInterpZ());
            BloodRenderType.renderMagicBillboard(p, buffer, view, partialTicks, p.getQueuedRotX(), p.getQueuedRotZ(), p.getQueuedRotYZ(), p.getQueuedRotXY(), p.getQueuedRotXZ());
            p.clearQueuedBillboard();
            return;
        }
        BloodRenderType.setSharedParticleInterp(camX, camY, camZ);
        BloodRenderType.renderMagicBillboard(p, buffer, view, partialTicks, fallbackRotations.rotationX, fallbackRotations.rotationZ, fallbackRotations.rotationYZ, fallbackRotations.rotationXY, fallbackRotations.rotationXZ);
        p.clearQueuedBillboard();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static void renderMagicBillboard(@Nonnull ParticleBlood p, @Nonnull BufferBuilder buffer, @Nonnull Entity view, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        if (!p.isAlive() || !BloodMagic.isMagic(p)) {
            return;
        }
        if (BloodHotBlocks.shouldRenderHotAirBillboard(p)) {
            BloodLavaRendering.renderHotBillboardFromBlood(p, buffer, view, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
            return;
        }
        float oldR = p.getRed();
        float oldG = p.getGreen();
        float oldB = p.getBlue();
        float oldA = p.getAlpha();
        float targetAlpha = Util.clamp01(oldA);
        if (targetAlpha <= 1.0E-6f) {
            return;
        }
        float tintR = p.getTintR();
        float tintG = p.getTintG();
        float tintB = p.getTintB();
        float passA0 = BloodRenderType.solveBasePassAlphaForTarget(targetAlpha, 0.85f);
        float passA1 = Util.clamp01(passA0 * 0.85f);
        try {
            p.setRGBA(Util.clamp01(tintR * 0.35f), Util.clamp01(tintG * 0.35f), Util.clamp01(tintB * 0.35f), passA0);
            p.vanillaRenderParticle(buffer, view, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
            if (passA1 > 1.0E-6f) {
                p.setRGBA(Util.clamp01(tintR), Util.clamp01(tintG), Util.clamp01(tintB), passA1);
                p.vanillaRenderParticle(buffer, view, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
            }
        }
        finally {
            p.setRGBA(oldR, oldG, oldB, oldA);
        }
    }

    private static void renderQueuedLavaBillboard(@Nonnull BloodLava p, @Nonnull BufferBuilder buffer, @Nonnull Entity view, float partialTicks, double camX, double camY, double camZ, @Nonnull BillboardRotations fallbackRotations) {
        if (!p.isAlive() || p.getParticleAlpha() <= 0.001f) {
            p.clearQueuedBillboard();
            return;
        }
        if (p.hasQueuedBillboard()) {
            BloodRenderType.setSharedParticleInterp(p.getQueuedInterpX(), p.getQueuedInterpY(), p.getQueuedInterpZ());
            BloodLavaRendering.renderParticle(p, buffer, view, partialTicks, p.getQueuedRotX(), p.getQueuedRotZ(), p.getQueuedRotYZ(), p.getQueuedRotXY(), p.getQueuedRotXZ());
            p.clearQueuedBillboard();
            return;
        }
        BloodRenderType.setSharedParticleInterp(camX, camY, camZ);
        BloodLavaRendering.renderParticle(p, buffer, view, partialTicks, fallbackRotations.rotationX, fallbackRotations.rotationZ, fallbackRotations.rotationYZ, fallbackRotations.rotationXY, fallbackRotations.rotationXZ);
        p.clearQueuedBillboard();
    }

    private static float solveBasePassAlphaForTarget(float targetAlpha, float overlayAlphaMul) {
        double x;
        targetAlpha = Util.clamp01(targetAlpha);
        float overlay = Util.clamp01(overlayAlphaMul);
        if (overlay <= 1.0E-6f) {
            return targetAlpha;
        }
        double b = -(1.0 + (double)overlay);
        double a = overlay;
        double c = targetAlpha;
        double disc = b * b - 4.0 * a * c;
        if (disc <= 0.0) {
            return targetAlpha;
        }
        double sqrt = Math.sqrt(disc);
        double x1 = (-b - sqrt) / (2.0 * a);
        double x2 = (-b + sqrt) / (2.0 * a);
        double d = x = x1 >= 0.0 && x1 <= 1.0 ? x1 : x2;
        if (x < 0.0) {
            x = 0.0;
        }
        if (x > 1.0) {
            x = 1.0;
        }
        return (float)x;
    }





    private static final class BillboardRotations {
        final float rotationX;
        final float rotationZ;
        final float rotationYZ;
        final float rotationXY;
        final float rotationXZ;

        private BillboardRotations(float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
            this.rotationX = rotationX;
            this.rotationZ = rotationZ;
            this.rotationYZ = rotationYZ;
            this.rotationXY = rotationXY;
            this.rotationXZ = rotationXZ;
        }
    }

    private static final class RenderEntry {
        final double distSq;
        @Nonnull
        final RenderKind kind;
        @Nullable
        final ParticleBlood blood;
        @Nullable
        final BloodWater water;
        @Nullable
        final BloodLava lava;

        private RenderEntry(double distSq, @Nonnull RenderKind kind, @Nullable ParticleBlood blood, @Nullable BloodWater water, @Nullable BloodLava lava) {
            this.distSq = distSq;
            this.kind = kind;
            this.blood = blood;
            this.water = water;
            this.lava = lava;
        }

        static RenderEntry decal(double distSq, @Nonnull ParticleBlood decal) {
            return new RenderEntry(distSq, RenderKind.DECAL, decal, null, null);
        }

        static RenderEntry bloodBillboard(double distSq, @Nonnull ParticleBlood blood) {
            return new RenderEntry(distSq, RenderKind.BLOOD_BILLBOARD, blood, null, null);
        }

        static RenderEntry magic(double distSq, @Nonnull ParticleBlood magic) {
            return new RenderEntry(distSq, RenderKind.MAGIC_BILLBOARD, magic, null, null);
        }

        static RenderEntry water(double distSq, @Nonnull BloodWater water, @Nonnull RenderKind kind) {
            return new RenderEntry(distSq, kind, null, water, null);
        }

        static RenderEntry lava(double distSq, @Nonnull BloodLava lava, @Nonnull RenderKind kind) {
            return new RenderEntry(distSq, kind, null, null, lava);
        }
    }

    private static enum RenderKind {
        DECAL(0),
        BLOOD_BILLBOARD(1),
        WATER_SURFACE(2),
        WATER_DROPLET(3),
        LAVA_SURFACE(4),
        LAVA_BILLBOARD(5),
        WATER_FLOOR(6),
        MAGIC_BILLBOARD(7);

        final int sortKey;

        private RenderKind(int sortKey) {
            this.sortKey = sortKey;
        }
    }
}

