package com.voltyx.efw.gore.particle;

import com.voltyx.efw.gore.ModConfigurationClient;
import com.voltyx.efw.gore.json.BloodAmalgamationPolicy;
import com.voltyx.efw.gore.json.BloodBrightnessMode;
import com.voltyx.efw.gore.json.BloodEntityConfig;
import com.voltyx.efw.gore.json.BloodStyle;
import com.voltyx.efw.gore.particle.ClientSprites;
import com.voltyx.efw.gore.particle.common.BloodParticleHandler;
import com.voltyx.efw.gore.particle.common.BloodTuning;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.amalgamation.BloodAmalgamationGround;
import com.voltyx.efw.gore.particle.common.amalgamation.BloodAmalgamationWall;
import com.voltyx.efw.gore.particle.common.cache.BloodCaches;
import com.voltyx.efw.gore.particle.common.cache.BloodCachesParticle;
import com.voltyx.efw.gore.particle.common.motion.BloodMotionParticle;
import com.voltyx.efw.gore.particle.render.BloodRender;
import com.voltyx.efw.gore.particle.state.BloodHeavy;
import com.voltyx.efw.gore.particle.state.BloodMagic;
import com.voltyx.efw.gore.particle.state.BloodSlimy;
import com.voltyx.efw.gore.particle.state.liquid.lava.BloodLava;
import com.voltyx.efw.gore.particle.state.liquid.water.BloodWater;
import net.minecraft.util.RandomSource;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.LiquidBlock;
import com.voltyx.efw.gore.particle.common.GoreMaterial;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import com.voltyx.efw.gore.particle.LegacyParticle;
import com.voltyx.efw.gore.particle.render.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
public class ParticleBlood
extends LegacyParticle {
    public float amalgamConsumeStartDrip = 0.0f;
    private BloodBrightnessMode brightnessMode = BloodBrightnessMode.WORLD;
    private float dripChance01 = 0.6f;
    public final BloodCaches.State cache = new BloodCaches.State();
    public final float viscosity01;
    public int slimySurfaceAnimStartAge = -1;
    public int slimySurfaceAnimTicks = 0;
    public int slimyGroundBounceCount = 0;
    public int slimyGroundBounceMax = -1;
    public int slimyBounceAnimStartAge = -1;
    public int slimyBounceAnimTicks = 0;
    public float slimyBounceAnimStrength = 0.0f;
    public int hotBounceCooldownTicks = 0;
    public int hotVisualTicks = 0;
    public int hotVisualTotal = 0;
    public int hotBurstTicks = 0;
    public int hotBurstTotal = 0;
    public float hotBurstDirX = 1.0f;
    public float hotBurstDirZ = 0.0f;
    public float hotVisualPower = 1.0f;
    public int hotSurfaceStartAge = -1;
    public final double spawnX;
    public final double spawnY;
    public final double spawnZ;
    public final boolean magicFullbright;
    public float groundRot;
    public final boolean flipU;
    public final double surfaceOffset;
    public final float spawnScale;
    public final float dripSeed;
    public final float groundExtendMul;
    public final float wallExtendMul;
    public final float planCeilingRoll;
    public final int planCeilingDelayTicks;
    public final int planCeilingGrowTicks;
    public final float planCeilingBodyLen;
    public final float planCeilingShrinkAtFull;
    public final float planCeilingDropSpeed01;
    public final int planFallingShrinkTicks;
    public final int planCeilingHangTicks;
    public final float planWallDetachRoll;
    public final int planWallDetachDelayTicks;
    public final BloodStyle fluidWeight;
    public final BloodHeavy.Runtime heavy = new BloodHeavy.Runtime();
    public boolean isStuck = false;
    public StickMode stickMode = StickMode.MODEL;
    @Nullable
    public Direction stuckFace = null;
    @Nullable
    public BlockPos stuckPos = null;
    public double stuckPlane = 0.0;
    @Nullable
    public Vec3 stuckLocalOnPlane = null;
    public int stuckStartAge = -1;
    public int sideDetachAge = -1;
    public boolean ceilingDripEnabled = false;
    public boolean ceilingDripConsumed = false;
    public int ceilingNextDripAge = -1;
    public int ceilingDripStartAge = -1;
    public int ceilingDripBuildTicks = 0;
    public float ceilingDripTargetLen = 0.0f;
    public float dripAmount = 0.0f;
    public boolean fallingDripActive = false;
    public int fallingDripStartAge = -1;
    public int fallingDripShrinkTicks = 0;
    public float fallingDripStartLen = 0.0f;
    public float fallingDripLen = 0.0f;
    public boolean noAirFlutter = false;
    public int idleTicks = 0;
    public int detachWobbleTicks = 0;
    public int wallAttachCooldownTicks = 0;
    public int relocateGraceTicks = 0;
    public float tintR = 1.0f;
    public float tintG = 0.0f;
    public float tintB = 0.0f;
    public float alphaMonotonic = 1.0f;
    public float amalgamMass = 1.0f;
    public float amalgamScaleMul = 1.0f;
    public int amalgamLastMergeAge = -1;
    public float amalgamVisualMass = 1.0f;
    public int amalgamAnimTicks = 0;
    public int amalgamConsumeStartAge = -1;
    public int amalgamConsumeDurationTicks = 0;
    public float amalgamConsumeStartScale = 1.0f;
    private static final int WALL_DETACH_MIN_TICKS = 60;
    private static final int WALL_DETACH_MAX_TICKS = 200;
    private BloodAmalgamationPolicy amalgamationPolicy = BloodAmalgamationPolicy.BOTH;

    public static BloodStyle resolveFluidStyleSafe() {
        try {
            return BloodEntityConfig.getDefaultStyle();
        }
        catch (Throwable t) {
            return BloodStyle.LIGHT;
        }
    }

    public static BloodStyle normalizeWeight(@Nullable BloodStyle w) {
        return w != null ? w : BloodStyle.LIGHT;
    }

    public static boolean isMagicFluid(@Nullable BloodStyle w) {
        return ParticleBlood.normalizeWeight(w) == BloodStyle.MAGIC;
    }

    public static boolean isLightFluid(@Nullable BloodStyle w) {
        return ParticleBlood.normalizeWeight(w) == BloodStyle.LIGHT;
    }

    public static boolean isSlimyFluid(@Nullable BloodStyle w) {
        return ParticleBlood.normalizeWeight(w) == BloodStyle.SLIMY;
    }

    public static boolean isLightLikeFluid(@Nullable BloodStyle w) {
        BloodStyle n = ParticleBlood.normalizeWeight(w);
        return n == BloodStyle.LIGHT || n == BloodStyle.SLIMY;
    }

    public ParticleBlood(Level worldIn, double x, double y, double z, double motionX, double motionY, double motionZ, int variant, @Nullable BloodStyle weight) {
        this(worldIn, x, y, z, motionX, motionY, motionZ, variant, weight, BloodEntityConfig.getDefaultScaleMin(), BloodEntityConfig.getDefaultScaleMax(), BloodEntityConfig.getDefaultLifeMin(), BloodEntityConfig.getDefaultLifeMax(), BloodEntityConfig.getDefaultViscosity());
    }

    public ParticleBlood(Level worldIn, double x, double y, double z, double motionX, double motionY, double motionZ, int variant, @Nullable BloodStyle weight, float scaleMin, float scaleMax) {
        this(worldIn, x, y, z, motionX, motionY, motionZ, variant, weight, scaleMin, scaleMax, BloodEntityConfig.getDefaultLifeMin(), BloodEntityConfig.getDefaultLifeMax(), BloodEntityConfig.getDefaultViscosity());
    }

    public ParticleBlood(Level worldIn, double x, double y, double z, double motionX, double motionY, double motionZ, int variant, @Nullable BloodStyle weight, float scaleMin, float scaleMax, int lifeMin, int lifeMax) {
        this(worldIn, x, y, z, motionX, motionY, motionZ, variant, weight, scaleMin, scaleMax, lifeMin, lifeMax, BloodEntityConfig.getDefaultViscosity());
    }

    public ParticleBlood(Level worldIn, double x, double y, double z, double motionX, double motionY, double motionZ, int variant, @Nullable BloodStyle weight, float scaleMin, float scaleMax, int lifeMin, int lifeMax, float viscosity01) {
        super(worldIn, x, y, z);
        float baseScale;
        this.setParticleTexture(ClientSprites.getBloodSprite(variant));
        this.groundRot = this.rand.nextFloat() * ((float)Math.PI * 2);
        this.flipU = this.rand.nextBoolean();
        this.surfaceOffset = Util.SurfaceLayer.nextSurfaceOffset();
        BloodStyle requested = ParticleBlood.normalizeWeight(weight);
        this.magicFullbright = requested == BloodStyle.MAGIC;
        this.fluidWeight = BloodMagic.mixMagicWithLightOnSpawn(requested, this.rand);
        Util.RangeF scaleRange = BloodEntityConfig.sanitizeScaleRange(scaleMin, scaleMax);
        this.spawnScale = baseScale = Util.randBetween(this.rand, scaleRange.min, scaleRange.max);
        this.particleScale = BloodSlimy.isSlimyFluid(this.fluidWeight) ? BloodSlimy.airScale(baseScale) : baseScale;
        float cs = BloodTuning.collisionSizeForScale(this.particleScale);
        this.setSize(cs, cs);
        Util.RangeF groundSpreadRange = Util.RangeF.of((float)ModConfigurationClient.ground.spreadMin, (float)ModConfigurationClient.ground.spreadMax).clampMin(0.0f);
        float groundExtend = Util.randBetween(this.rand, groundSpreadRange.min, groundSpreadRange.max);
        this.groundExtendMul = groundExtend = BloodSlimy.styleGroundExtendMul(this.fluidWeight, groundExtend);
        Util.RangeF wallStretchRange = Util.RangeF.of((float)ModConfigurationClient.wall.stretchMin, (float)ModConfigurationClient.wall.stretchMax).clampMin(0.0f);
        float wallExtend = Util.randBetween(this.rand, wallStretchRange.min, wallStretchRange.max);
        this.wallExtendMul = wallExtend = BloodSlimy.styleWallStretchMul(this.fluidWeight, wallExtend);
        BloodEntityConfig.RangeI lr = BloodEntityConfig.sanitizeLifeRange(lifeMin, lifeMax);
        this.particleMaxAge = lr.max > lr.min ? lr.min + this.rand.nextInt(lr.max - lr.min + 1) : lr.min;
        this.particleGravity = 0.8f;
        this.canCollide = true;
        this.particleRed = 1.0f;
        this.particleGreen = 1.0f;
        this.particleBlue = 1.0f;
        this.particleAlpha = 1.0f;
        this.dripSeed = 0.75f + this.rand.nextFloat() * 0.5f;
        float viscRaw = this.viscosity01 = BloodEntityConfig.sanitizeViscosity(viscosity01, BloodEntityConfig.getDefaultViscosity());
        float visc = Util.smoothstep01(viscRaw);
        this.planCeilingRoll = this.rand.nextFloat();
        float sp = this.rand.nextFloat();
        sp += (1.0f - viscRaw) * 0.25f;
        this.planCeilingDropSpeed01 = Util.clamp01(sp -= viscRaw * 0.08f);
        this.planFallingShrinkTicks = 6 + this.rand.nextInt(4);
        int CD_MAX_WATERY = 6;
        int cdMin = Math.round(20.0f * visc);
        int cdMax = Math.round(6.0f + 74.0f * visc);
        if (cdMax < cdMin) {
            cdMax = cdMin;
        }
        int cd = cdMin + (cdMax > cdMin ? this.rand.nextInt(cdMax - cdMin + 1) : 0);
        this.planCeilingDelayTicks = cd > 0 ? this.rand.nextInt(cd + 1) : 0;
        float BODY_MAX_WATERY = 0.049999997f;
        float BODY_MAX_VISCOUS = 0.9375f;
        float maxLen = 0.049999997f + 0.8875f * visc;
        if (maxLen < 0.03f) {
            maxLen = 0.03f;
        }
        float base = Util.randBetween(this.rand, 0.03f, maxLen);
        float s = this.spawnScale;
        s = (float)Util.clamp(s, 0.0, 2.0);
        float scaleMul = 0.7f + 0.3f * Util.clamp01(s);
        float wallMul = this.wallExtendMul;
        float wallMulEff = (wallMul = (float)Util.clamp(wallMul, 0.5, 2.25)) * (0.65f + 0.35f * visc);
        float len = base * scaleMul * wallMulEff * this.dripSeed;
        if (len < 0.03f) {
            len = 0.03f;
        }
        if (len > maxLen) {
            len = maxLen;
        }
        this.planCeilingBodyLen = len;
        float denomLen = Math.max(1.0E-6f, 0.9075f);
        float len01 = (len - 0.03f) / denomLen;
        len01 = Util.clamp01(len01);
        int GROW_MIN_WATERY = 6;
        int GROW_MAX_WATERY = 24;
        int gMin = Math.round(6.0f + 34.0f * visc);
        int gMax = Math.round(24.0f + 136.0f * visc);
        if (gMax < gMin) {
            gMax = gMin;
        }
        int baseTicks = gMin + (gMax > gMin ? this.rand.nextInt(gMax - gMin + 1) : 0);
        float growMul = 0.85f + 0.45f * len01;
        int growTicks = (int)Math.round((double)baseTicks * (double)growMul);
        int MIN_GROW_TICKS_ANY = 6;
        this.planCeilingGrowTicks = Math.max(6, growTicks);
        boolean MIN_HANG_TICKS_ANY = true;
        int hangMax = Util.clampInt(2 + Math.round(78.0f * visc), 0, 80);
        float hangLenMul = 0.15f + 0.85f * len01;
        float hangRand = 0.75f + this.rand.nextFloat() * 0.5f;
        int hang = Math.round((float)hangMax * hangLenMul * hangRand);
        this.planCeilingHangTicks = Util.clampInt(hang, 1, 80);
        this.planCeilingShrinkAtFull = 0.2f + this.rand.nextFloat() * 0.35f;
        this.planWallDetachRoll = this.rand.nextFloat();
        int wdRange = 140;
        this.planWallDetachDelayTicks = 60 + (wdRange > 0 ? this.rand.nextInt(wdRange + 1) : 0);
        this.syncFromVanillaParticleFields();
        this.setExactMotion(motionX, motionY, motionZ);
        this.spawnX = this.posX;
        this.spawnY = this.posY;
        this.spawnZ = this.posZ;
    }

    public void setExactMotion(double x, double y, double z) {
        this.motionX = x;
        this.motionY = y;
        this.motionZ = z;
        this.syncToVanillaMotionOnly();
    }

    public void captureQueuedBillboard(float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        BloodCachesParticle.captureBillboard(this, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
    }

    public boolean hasQueuedBillboard() {
        return this.cache.billboard.valid;
    }

    public void clearQueuedBillboard() {
        this.cache.billboard.valid = false;
    }

    public float getQueuedRotX() {
        return this.cache.billboard.rotX;
    }

    public float getQueuedRotZ() {
        return this.cache.billboard.rotZ;
    }

    public float getQueuedRotYZ() {
        return this.cache.billboard.rotYZ;
    }

    public float getQueuedRotXY() {
        return this.cache.billboard.rotXY;
    }

    public float getQueuedRotXZ() {
        return this.cache.billboard.rotXZ;
    }

    public double getQueuedInterpX() {
        return this.cache.billboard.interpX;
    }

    public double getQueuedInterpY() {
        return this.cache.billboard.interpY;
    }

    public double getQueuedInterpZ() {
        return this.cache.billboard.interpZ;
    }

    public void setTintRGB(int rgb) {
        this.tintR = 1.0f;
        this.tintG = 1.0f;
        this.tintB = 1.0f;
    }

    public float getTintR() {
        return this.tintR;
    }

    public float getTintG() {
        return this.tintG;
    }

    public float getTintB() {
        return this.tintB;
    }

    public void setBrightnessMode(@Nullable BloodBrightnessMode mode) {
        this.brightnessMode = mode != null ? mode : BloodBrightnessMode.WORLD;
    }

    public BloodBrightnessMode getBrightnessMode() {
        return this.brightnessMode;
    }

    public int getBrightnessForRender(float partialTicks) {
        int base;
        try {
            if (this.isStuck && this.stuckPos != null && this.stuckFace != null && this.world != null) {
                BlockPos lightPos = this.stuckPos.relative(this.stuckFace);
                if (this.world.isLoaded(lightPos)) {
                    base = net.minecraft.client.renderer.LevelRenderer.getLightColor(this.world, lightPos);
                } else {
                    base = super.getBrightnessForRender(partialTicks);
                }
            } else {
                base = super.getBrightnessForRender(partialTicks);
            }
        }
        catch (Throwable t) {
            base = 0;
        }
        return this.brightnessMode.applyToPackedLight(base);
    }

    public void addAgeTicks(int ticks) {
        if (ticks <= 0) {
            return;
        }
        int max = this.particleMaxAge;
        if (max > 0) {
            long next = (long)this.particleAge + (long)ticks;
            if (next > (long)max) {
                next = max;
            }
            this.particleAge = (int)next;
            return;
        }
        long next = (long)this.particleAge + (long)ticks;
        if (next > Integer.MAX_VALUE) {
            next = Integer.MAX_VALUE;
        }
        this.particleAge = (int)next;
    }

    public void resetCeilingDripRuntime() {
        this.ceilingDripEnabled = false;
        this.ceilingNextDripAge = -1;
        this.ceilingDripStartAge = -1;
        this.ceilingDripBuildTicks = 0;
        this.ceilingDripTargetLen = 0.0f;
        this.dripAmount = 0.0f;
    }

    public void resetFallingDripRuntime() {
        this.fallingDripActive = false;
        this.fallingDripStartAge = -1;
        this.fallingDripShrinkTicks = 0;
        this.fallingDripStartLen = 0.0f;
        this.fallingDripLen = 0.0f;
    }

    /** 1.12 kept its own copies of the position; the legacy particle has one set only. */
    void syncFromVanillaParticleFields() {
    }

    public void syncToVanillaMotionAndPrev() {
    }

    public void syncToVanillaMotionOnly() {
    }

    public void vanillaUpdate() {
        super.onUpdate();
    }

    public boolean isExpiredSafe() {
        return this.removed;
    }

    public boolean isGroundTop() {
        return this.isStuck && this.stuckFace == Direction.UP;
    }

    public boolean isAmalgamConsuming() {
        return this.amalgamConsumeStartAge >= 0;
    }

    public void startAmalgamConsume(int ticks) {
        if (ticks <= 0) {
            ticks = 1;
        }
        if (this.amalgamConsumeStartAge >= 0) {
            return;
        }
        this.amalgamConsumeStartAge = this.getAge();
        this.amalgamConsumeDurationTicks = ticks;
        float s = this.getScale();
        if (s < 0.001f) {
            s = 0.001f;
        }
        this.amalgamConsumeStartScale = s;
        float d = this.dripAmount;
        if (d < 0.0f) {
            d = 0.0f;
        }
        this.amalgamConsumeStartDrip = d;
        int needAge = this.amalgamConsumeStartAge + ticks + 2;
        if (this.getMaxAge() < needAge) {
            this.setMaxAge(needAge);
        }
        this.sideDetachAge = -1;
        BloodCachesParticle.invalidateShape(this);
        BloodCachesParticle.invalidateView(this);
    }

    public void addAmalgamMass(float add) {
        boolean allow;
        boolean wall;
        if (add <= 0.0f) {
            return;
        }
        boolean ground = this.isGroundTop();
        boolean bl = wall = this.isStuck && BloodTuning.isWallFace(this.stuckFace);
        if (!ground && !wall) {
            return;
        }
        boolean bl2 = allow = ground ? BloodAmalgamationGround.allow(this.amalgamationPolicy, this.fluidWeight) : BloodAmalgamationWall.allow(this.amalgamationPolicy, this.fluidWeight);
        if (!allow) {
            return;
        }
        float maxMass = ground ? 20.0f : 12.0f;
        int massAnimTicks = ground ? 12 : 10;
        int extraLifePerAbsorbTicks = ground ? 12 : 10;
        float before = this.amalgamMass;
        float after = Math.min(maxMass, before + add);
        if (after <= before + 1.0E-5f) {
            this.amalgamLastMergeAge = this.getAge();
            return;
        }
        this.amalgamMass = after;
        this.amalgamAnimTicks = Math.max(this.amalgamAnimTicks, massAnimTicks);
        this.amalgamLastMergeAge = this.getAge();
        int extra = Math.round((float)extraLifePerAbsorbTicks * add);
        if (extra > 0) {
            int minNeed;
            int newMax = this.getMaxAge() + extra;
            if (newMax < (minNeed = this.getAge() + 20)) {
                newMax = minNeed;
            }
            this.setMaxAge(newMax);
        }
        BloodCachesParticle.invalidateShape(this);
        BloodCachesParticle.invalidateView(this);
    }

    public void resetAmalgamState(boolean resetScaleToSpawn) {
        this.amalgamMass = 1.0f;
        this.amalgamVisualMass = 1.0f;
        this.amalgamAnimTicks = 0;
        this.amalgamScaleMul = 1.0f;
        this.amalgamLastMergeAge = -1;
        this.amalgamConsumeStartAge = -1;
        this.amalgamConsumeDurationTicks = 0;
        this.amalgamConsumeStartScale = 1.0f;
        this.amalgamConsumeStartDrip = 0.0f;
        this.cache.shape.amalgam = Float.NaN;
        if (resetScaleToSpawn) {
            this.setScale(this.spawnScale);
        }
        BloodCachesParticle.invalidateShape(this);
        BloodCachesParticle.invalidateView(this);
    }

    public float getRed() {
        return this.particleRed;
    }

    public float getGreen() {
        return this.particleGreen;
    }

    public float getBlue() {
        return this.particleBlue;
    }

    public void setRGBA(float r, float g, float b, float a) {
        this.particleRed = r;
        this.particleGreen = g;
        this.particleBlue = b;
        this.particleAlpha = a;
    }

    public void vanillaRenderParticle(@Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        super.renderParticle(buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
    }

    public static double getInterpX() {
        return interpPosX;
    }

    public static double getInterpY() {
        return interpPosY;
    }

    public static double getInterpZ() {
        return interpPosZ;
    }

    public static void setInterp(double x, double y, double z) {
        interpPosX = x;
        interpPosY = y;
        interpPosZ = z;
    }

    public int getFXLayer() {
        return 1;
    }

    public void onUpdate() {
        BloodParticleHandler.onUpdate(this);
    }

    public boolean isInsideWaterNow() {
        if (BloodMagic.treatLiquidBlocksAsAir(this)) {
            return false;
        }
        return this.isInsideWaterVolumeAt(this.posX, this.posY, this.posZ);
    }

    private boolean wasInsideWaterPrev() {
        if (BloodMagic.treatLiquidBlocksAsAir(this)) {
            return false;
        }
        return this.isInsideWaterVolumeAt(this.prevPosX, this.prevPosY, this.prevPosZ);
    }

    private boolean isInsideWaterVolumeAt(double x, double y, double z) {
        BlockState st;
        Level w = this.world;
        if (w == null) {
            return false;
        }
        BlockPos bp = BlockPos.containing(x, y, z);
        if (!w.isLoaded(bp)) {
            return false;
        }
        try {
            st = w.getBlockState(bp);
        }
        catch (Throwable t) {
            return false;
        }
        if (st == null || GoreMaterial.of(st) != GoreMaterial.WATER) {
            return false;
        }
        BlockPos up = bp.above();
        if (!w.isLoaded(up)) {
            return true;
        }
        try {
            BlockState upState = w.getBlockState(up);
            if (upState != null && GoreMaterial.of(upState) == GoreMaterial.WATER) {
                return true;
            }
        }
        catch (Throwable ignored) {
            return true;
        }
        double surfaceY = this.getRenderedWaterSurfaceY(bp, st);
        return y <= surfaceY - 0.001;
    }

    private double getRenderedWaterSurfaceY(@Nonnull BlockPos bp, @Nonnull BlockState st) {
        try {
            if (!st.getFluidState().isEmpty()) {
                return (double) bp.getY() + (double) st.getFluidState().getHeight(this.world, bp);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return (double) bp.getY() + 1.0;
    }

    private int computeWaterEntrySinkBoostTicks() {
        if (!ParticleBlood.isLightLikeFluid(this.fluidWeight)) {
            return 0;
        }
        if (this.wasInsideWaterPrev()) {
            return 0;
        }
        double down = Math.max(0.0, -this.motionY);
        if (down <= 0.01) {
            return 0;
        }
        return Mth.clamp((int)(2 + (int)Math.round(down * 28.0)), (int)2, (int)6);
    }

    public void convertToWaterParticle() {
        Level w = this.getParticleWorld();
        if (w == null) {
            this.setExpired();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.particleEngine == null) {
            this.setExpired();
            return;
        }
        if (BloodMagic.treatLiquidBlocksAsAir(this)) {
            return;
        }
        int entrySinkBoostTicks = this.computeWaterEntrySinkBoostTicks();
        BloodWater pw = new BloodWater(w, this.posX, this.posY, this.posZ, this.motionX, this.motionY, this.motionZ, this.getSprite(), this.getScale(), this.groundRot, this.flipU, this.surfaceOffset, this.getAge(), this.getMaxAge(), this.getAlpha(), this.fluidWeight);
        pw.setPrevParticlePos(this.prevPosX, this.prevPosY, this.prevPosZ);
        pw.setParticleColor(this.getTintR(), this.getTintG(), this.getTintB());
        pw.setAmalgamationPolicy(this.getAmalgamationPolicy());
        pw.setBrightnessMode(this.getBrightnessMode());
        if (entrySinkBoostTicks > 0) {
            pw.setWaterTicks(-entrySinkBoostTicks);
        }
        mc.particleEngine.add(pw);
        this.setExpired();
    }

    public boolean isInsideLavaNow() {
        BlockState st;
        if (BloodMagic.treatLiquidBlocksAsAir(this)) {
            return false;
        }
        if (this.world == null) {
            return false;
        }
        BlockPos bp = BlockPos.containing(this.posX, this.posY, this.posZ);
        if (!this.world.isLoaded(bp)) {
            return false;
        }
        try {
            st = this.world.getBlockState(bp);
        }
        catch (Throwable t) {
            return false;
        }
        return st != null && GoreMaterial.of(st) == GoreMaterial.LAVA;
    }

    public void convertToLavaParticle() {
        Level w = this.getParticleWorld();
        if (w == null) {
            this.setExpired();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.particleEngine == null) {
            this.setExpired();
            return;
        }
        if (BloodMagic.treatLiquidBlocksAsAir(this)) {
            return;
        }
        BloodLava pl = new BloodLava(w, this.posX, this.posY, this.posZ, this.motionX, this.motionY, this.motionZ, this.getSprite(), this.getScale(), this.groundRot, this.flipU, this.surfaceOffset, this.getAge(), this.getMaxAge(), this.getAlpha(), this.fluidWeight);
        pl.setParticleColor(this.getTintR(), this.getTintG(), this.getTintB());
        pl.setAmalgamationPolicy(this.getAmalgamationPolicy());
        pl.setBrightnessMode(this.getBrightnessMode());
        mc.particleEngine.add(pl);
        this.setExpired();
    }

    public void setPosition(double x, double y, double z) {
        super.setPosition(x, y, z);
    }

    public void move(double x, double y, double z) {
        BloodMotionParticle.move(this, x, y, z);
    }

    public void vanillaMove(double x, double y, double z) {
        super.move(x, y, z);
    }

    public void renderParticle(@Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        BloodRender.renderParticle(this, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
    }

    public void setDripChance01(float chance01) {
        float def = BloodEntityConfig.getDefaultDripChance();
        this.dripChance01 = BloodEntityConfig.sanitizeDripChance(chance01, def);
    }

    public float getDripChance01() {
        return this.dripChance01;
    }

    public void setAmalgamationPolicy(@Nullable BloodAmalgamationPolicy p) {
        this.amalgamationPolicy = p != null ? p : BloodAmalgamationPolicy.BOTH;
    }

    public BloodAmalgamationPolicy getAmalgamationPolicy() {
        return this.amalgamationPolicy;
    }

    public void renderStuckDecal(@Nonnull BufferBuilder buffer, float partialTicks) {
        BloodRender.renderStuckDecal(this, buffer, partialTicks);
    }

    public boolean isStuckDecal() {
        return this.isStuck || this.fallingDripActive;
    }

    public Level getParticleWorld() {
        return this.world;
    }

    public double getDistanceSqTo(double x, double y, double z) {
        double dx = this.posX - x;
        double dy = this.posY - y;
        double dz = this.posZ - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public RandomSource getRand() {
        return this.rand;
    }

    public int getAge() {
        return this.particleAge;
    }

    public int getMaxAge() {
        return this.particleMaxAge;
    }

    public void setMaxAge(int v) {
        this.particleMaxAge = v;
    }

    public float getAlpha() {
        return this.particleAlpha;
    }

    public void setAlpha(float a) {
        this.particleAlpha = a;
    }

    public float getScale() {
        return this.particleScale;
    }

    public void setScale(float s) {
        this.particleScale = s;
    }

    public float getGravity() {
        return this.particleGravity;
    }

    public void setGravity(float g) {
        this.particleGravity = g;
    }

    public boolean getCanCollide() {
        return this.canCollide;
    }

    public void setCanCollide(boolean v) {
        this.canCollide = v;
    }

    public boolean isOnGroundFlag() {
        return this.onGround;
    }

    public void setOnGroundFlag(boolean v) {
        this.onGround = v;
    }

    public TextureAtlasSprite getSprite() {
        return this.particleTexture;
    }

    public void setSizeSafe(float w, float h) {
        this.setSize(w, h);
    }

    public void setPositionSafe(double x, double y, double z) {
        this.setPosition(x, y, z);
    }

    public static enum StickMode {
        MODEL,
        COLLISION;

    }
}

