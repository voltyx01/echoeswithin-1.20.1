package com.voltyx.efw.gore.particle.state.liquid.water;

import com.voltyx.efw.gore.ModConfigurationClient;
import com.voltyx.efw.gore.json.BloodAmalgamationPolicy;
import com.voltyx.efw.gore.json.BloodBrightnessMode;
import com.voltyx.efw.gore.json.BloodStyle;
import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.BloodTuning;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaWater;
import com.voltyx.efw.gore.particle.common.amalgamation.BloodAmalgamationLiquid;
import com.voltyx.efw.gore.particle.common.amalgamation.BloodAmalgamationWater;
import com.voltyx.efw.gore.particle.common.cache.BloodCaches;
import com.voltyx.efw.gore.particle.common.cache.BloodCachesWater;
import com.voltyx.efw.gore.particle.common.motion.BloodMotionWater;
import com.voltyx.efw.gore.particle.render.BloodRenderType;
import com.voltyx.efw.gore.particle.render.parts.BloodWaterRendering;
import com.voltyx.efw.gore.particle.state.BloodHeavy;
import com.voltyx.efw.gore.particle.state.liquid.BloodLiquidUtil;
import com.voltyx.efw.gore.particle.state.liquid.water.BloodWaterCache;
import com.voltyx.efw.gore.particle.state.liquid.water.BloodWaterUtil;
import net.minecraft.util.RandomSource;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.voltyx.efw.gore.particle.LegacyParticle;
import com.voltyx.efw.gore.particle.render.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
public class BloodWater
extends LegacyParticle {
    public final BloodCaches.WaterState cache = new BloodCaches.WaterState();
    private boolean onSurface = false;
    private boolean onFloor = false;
    private final BloodWaterCache surfaceCache;
    private static final int TRACK_DECAL = 1;
    private BloodBrightnessMode brightnessMode = BloodBrightnessMode.WORLD;
    private int waterTicks = 0;
    private int surfaceGrowStartAge = -1;
    private final float groundRot;
    private final boolean flipU;
    private final double surfaceOffset;
    private final float driftSeed;
    private final float carriedAlphaMul;
    private final float baseScale;
    private final float landScale;
    private final double collHalfY;
    private float surfaceScale;
    private final float surfaceTargetScale;
    private final float surfaceRotSpeed;
    private float surfaceRot;
    private float prevSurfaceRot;
    private final BloodStyle fluidWeight;
    private final boolean heavy;
    private float prevAmalgMul = 1.0f;
    private float amalgMul = 1.0f;
    private float amalgTargetMul = 1.0f;
    private int amalgCooldownTicks = 0;
    @Nullable
    private BloodWater amalgMergeInto = null;
    private int amalgMergeStartAge = -1;
    private int amalgMergeTicks = 0;
    private float amalgGiveMul = 0.0f;
    private float prevAmalgOut01 = 0.0f;
    private float amalgOut01 = 0.0f;
    private int exitCoastTicks = 0;
    private BloodAmalgamationPolicy amalgamationPolicy = BloodAmalgamationPolicy.BOTH;

    public int getExitCoastTicks() {
        return this.exitCoastTicks;
    }

    public void setExitCoastTicks(int t) {
        this.exitCoastTicks = Math.max(0, t);
    }

    public void setPrevParticlePos(double x, double y, double z) {
        this.prevPosX = x;
        this.prevPosY = y;
        this.prevPosZ = z;
    }

    public BloodWater(Level worldIn, double x, double y, double z, double motionX, double motionY, double motionZ, @Nullable TextureAtlasSprite sprite, float scale, float groundRot, boolean flipU, double surfaceOffset, int age, int maxAge, float initialAlpha, @Nullable BloodStyle weight) {
        super(worldIn, x, y, z, motionX, motionY, motionZ);
        double maxSink;
        double desiredDown;
        this.surfaceCache = new BloodWaterCache(worldIn);
        if (sprite != null) {
            this.setParticleTexture(sprite);
        }
        this.groundRot = groundRot;
        this.flipU = flipU;
        this.surfaceOffset = surfaceOffset;
        this.driftSeed = this.rand.nextFloat();
        BloodStyle resolved = weight != null ? weight : ParticleBlood.resolveFluidStyleSafe();
        this.fluidWeight = BloodHeavy.normalizeWeight(resolved);
        this.heavy = BloodHeavy.isHeavyFluid(this.fluidWeight);
        this.landScale = Math.max(0.001f, scale);
        float baseMul = BloodHeavy.waterBaseScaleMul(this.heavy);
        this.baseScale = this.particleScale = Math.max(0.001f, this.landScale * baseMul);
        this.particleAge = Math.max(0, age);
        int srcMax = Math.max(this.particleAge + 1, maxAge);
        int waterTotal = (int)Math.ceil((double)srcMax * (double)0.7f);
        if (waterTotal < this.particleAge + 1) {
            waterTotal = this.particleAge + 1;
        }
        this.particleMaxAge = waterTotal;
        this.particleGravity = 0.0f;
        this.canCollide = true;
        float cs = BloodTuning.collisionSizeForScale(this.particleScale);
        float minCs = 0.2f * this.particleScale;
        cs = Math.max(cs, minCs);
        this.setSize(cs, cs);
        this.collHalfY = 0.5 * (double)cs;
        this.particleRed = 1.0f;
        this.particleGreen = 1.0f;
        this.particleBlue = 1.0f;
        float spreadMul = 1.0f;
        try {
            BloodWaterUtil.RangeF gRange = BloodWaterUtil.RangeF.of((float)ModConfigurationClient.ground.spreadMin, (float)ModConfigurationClient.ground.spreadMax).clampMin(0.0f);
            spreadMul = BloodLiquidUtil.randBetween(this.rand, gRange.min, gRange.max);
        }
        catch (Throwable gRange) {
            // empty catch block
        }
        float waterSpreadMul = BloodHeavy.waterSpreadMul(this.heavy, spreadMul);
        this.surfaceTargetScale = this.baseScale * waterSpreadMul;
        this.surfaceScale = this.baseScale;
        this.carriedAlphaMul = BloodAlphaWater.waterCarryAlpha(initialAlpha, this.particleAge, this.particleMaxAge);
        this.particleAlpha = BloodAlphaWater.waterBaseAlpha(this.carriedAlphaMul, this.particleAge, this.particleMaxAge);
        double d = desiredDown = this.heavy ? -0.035 - this.rand.nextDouble() * 0.025 : -0.02 - this.rand.nextDouble() * 0.015;
        if (this.motionY > desiredDown) {
            this.motionY = desiredDown;
        }
        double d2 = maxSink = this.heavy ? 0.1 : 0.045;
        if (this.motionY < -maxSink) {
            this.motionY = -maxSink;
        }
        float rs = 0.0f;
        if (this.rand.nextFloat() >= 0.25f) {
            float mag = 0.001f + this.rand.nextFloat() * 0.003f;
            rs = this.rand.nextBoolean() ? mag : -mag;
        }
        this.surfaceRotSpeed = rs;
        this.surfaceRot = this.groundRot;
        this.prevSurfaceRot = this.groundRot;
        this.particleAngle = this.groundRot;
        this.prevParticleAngle = this.groundRot;
    }

    @Deprecated
    public BloodWater(Level worldIn, double x, double y, double z, double motionX, double motionY, double motionZ, @Nullable TextureAtlasSprite sprite, float scale, float groundRot, boolean flipU, double surfaceOffset, int age, int maxAge, float initialAlpha) {
        this(worldIn, x, y, z, motionX, motionY, motionZ, sprite, scale, groundRot, flipU, surfaceOffset, age, maxAge, initialAlpha, ParticleBlood.resolveFluidStyleSafe());
    }

    public int getFXLayer() {
        return 1;
    }

    public BloodWaterCache getCache() {
        return this.surfaceCache;
    }

    public BloodStyle getFluidWeight() {
        return this.fluidWeight;
    }

    public void setBrightnessMode(@Nullable BloodBrightnessMode mode) {
        this.brightnessMode = mode != null ? mode : BloodBrightnessMode.WORLD;
    }

    public int getBrightnessForRender(float partialTicks) {
        int base;
        try {
            base = super.getBrightnessForRender(partialTicks);
        }
        catch (Throwable t) {
            base = 0;
        }
        return this.brightnessMode.applyToPackedLight(base);
    }

    private void setTrackReason(int bit, boolean enable) {
        BloodCaches.Track track = this.cache.track;
        int before = track.mask;
        track.mask = enable ? (track.mask |= bit) : (track.mask &= ~bit);
        if (before != track.mask) {
            this.refreshTrackingRegistration();
        }
    }

    private void refreshTrackingRegistration() {
        boolean wantTracked;
        BloodCaches.Track track = this.cache.track;
        boolean bl = wantTracked = track.mask != 0;
        if (wantTracked && !track.tracked) {
            BloodRenderType.trackWater(this);
            track.tracked = true;
        } else if (!wantTracked && track.tracked) {
            BloodRenderType.untrackWater(this);
            track.tracked = false;
        }
    }

    public BloodAmalgamationPolicy getAmalgamationPolicy() {
        return this.amalgamationPolicy;
    }

    public void setAmalgamationPolicy(@Nullable BloodAmalgamationPolicy p) {
        this.amalgamationPolicy = p != null ? p : BloodAmalgamationPolicy.BOTH;
    }

    public boolean isHeavyInWater() {
        return this.heavy;
    }

    public boolean isSurfaceDecal() {
        return this.onSurface && !this.heavy;
    }

    public boolean isFloorDecal() {
        return this.onFloor && this.heavy;
    }

    public boolean isOnSurface() {
        return this.onSurface;
    }

    public void setOnSurface(boolean v) {
        boolean bl = this.onSurface = v && !this.heavy;
        if (this.onSurface) {
            this.onFloor = false;
        }
        this.syncDecalTracking();
    }

    public boolean isOnFloor() {
        return this.onFloor;
    }

    public void setOnFloor(boolean v) {
        boolean bl = this.onFloor = v && this.heavy;
        if (this.onFloor) {
            this.onSurface = false;
        }
        this.syncDecalTracking();
    }

    public int getWaterTicks() {
        return this.waterTicks;
    }

    public void setWaterTicks(int t) {
        this.waterTicks = t;
    }

    public void incrementWaterTicks() {
        if (this.waterTicks < Integer.MAX_VALUE) {
            ++this.waterTicks;
        }
    }

    public int getSurfaceGrowStartAge() {
        return this.surfaceGrowStartAge;
    }

    public void setSurfaceGrowStartAge(int a) {
        this.surfaceGrowStartAge = a;
    }

    public float getSurfaceScale() {
        return this.surfaceScale;
    }

    public void setSurfaceScale(float s) {
        this.surfaceScale = s;
    }

    public float getSurfaceTargetScale() {
        return this.surfaceTargetScale;
    }

    public float getSurfaceRotSpeed() {
        return this.surfaceRotSpeed;
    }

    public float getSurfaceRot() {
        return this.surfaceRot;
    }

    public void setSurfaceRot(float r) {
        this.surfaceRot = r;
    }

    public float getPrevSurfaceRot() {
        return this.prevSurfaceRot;
    }

    public void setPrevSurfaceRot(float r) {
        this.prevSurfaceRot = r;
    }

    public float getGroundRot() {
        return this.groundRot;
    }

    public boolean isFlipU() {
        return this.flipU;
    }

    public double getSurfaceOffset() {
        return this.surfaceOffset;
    }

    public float getDriftSeed() {
        return this.driftSeed;
    }

    public float getCarriedAlphaMul() {
        return this.carriedAlphaMul;
    }

    private float clampAmalgMul(float v) {
        float cap;
        if (v < 1.0f) {
            v = 1.0f;
        }
        if ((cap = 2.4f) > 1.0f && v > cap) {
            v = cap;
        }
        return v;
    }

    public void amalgPreUpdate() {
        this.prevAmalgMul = this.amalgMul;
        this.prevAmalgOut01 = this.amalgOut01;
        if (this.amalgCooldownTicks > 0) {
            --this.amalgCooldownTicks;
        }
        if (this.amalgMergeInto != null && !this.amalgMergeInto.isAlive()) {
            this.cancelAmalgMergeOut();
        }
    }

    public void amalgPostUpdate() {
        if (!this.isAlive()) {
            return;
        }
        float k = BloodLiquidUtil.clamp01(0.3f);
        this.amalgTargetMul = this.clampAmalgMul(this.amalgTargetMul);
        this.amalgMul = this.clampAmalgMul(this.amalgMul + (this.amalgTargetMul - this.amalgMul) * k);
        if (this.amalgMergeInto != null) {
            float s;
            if (this.amalgMergeTicks <= 0) {
                this.amalgMergeTicks = Math.max(1, 10);
            }
            int dt = this.getAge() - this.amalgMergeStartAge;
            float t = (float)dt / (float)this.amalgMergeTicks;
            t = BloodLiquidUtil.clamp01(t);
            this.amalgOut01 = s = BloodLiquidUtil.smoothstep01(t);
            float delta = s - this.prevAmalgOut01;
            if (delta > 1.0E-6f && this.amalgGiveMul > 1.0E-6f) {
                BloodWater into = this.amalgMergeInto;
                if (into != null && into.isAlive()) {
                    into.addAmalgTargetMul(this.amalgGiveMul * delta);
                } else {
                    this.cancelAmalgMergeOut();
                    return;
                }
            }
            if (dt >= this.amalgMergeTicks) {
                this.expireAndUntrack();
                return;
            }
            return;
        }
        if (this.isOnSurface() && BloodAmalgamationLiquid.allow(this.amalgamationPolicy, this.getFluidWeight())) {
            BloodAmalgamationWater.tryMerge(this);
        }
    }

    public float getAmalgMul() {
        return this.amalgMul;
    }

    public float getAmalgTargetMul() {
        return this.amalgTargetMul;
    }

    public void addAmalgTargetMul(float add) {
        if (add > 0.0f) {
            this.amalgTargetMul = this.clampAmalgMul(this.amalgTargetMul + add);
        }
    }

    public boolean isAmalgMergingOut() {
        return this.amalgMergeInto != null;
    }

    public boolean canAcceptAmalgIn() {
        if (!BloodAmalgamationLiquid.allow(this.amalgamationPolicy, this.getFluidWeight())) {
            return false;
        }
        if (!this.isOnSurface()) {
            return false;
        }
        if (!this.isAlive()) {
            return false;
        }
        if (this.isAmalgMergingOut()) {
            return false;
        }
        if (this.amalgCooldownTicks > 0) {
            return false;
        }
        return this.amalgTargetMul < 2.3990002f;
    }

    public void startAmalgMergeOut(@Nonnull BloodWater into, float giveMul) {
        if (into == this) {
            return;
        }
        if (!BloodAmalgamationLiquid.allow(this.amalgamationPolicy, this.getFluidWeight())) {
            return;
        }
        if (!BloodAmalgamationLiquid.allow(into.getAmalgamationPolicy(), into.getFluidWeight())) {
            return;
        }
        if (!this.isAlive() || !into.isAlive()) {
            return;
        }
        if (giveMul <= 1.0E-6f) {
            return;
        }
        this.amalgMergeInto = into;
        this.amalgMergeStartAge = this.getAge();
        this.amalgMergeTicks = Math.max(1, 10);
        this.prevAmalgOut01 = 0.0f;
        this.amalgOut01 = 0.0f;
        this.amalgGiveMul = giveMul;
        this.amalgCooldownTicks = this.amalgMergeTicks;
        into.amalgCooldownTicks = Math.max(into.amalgCooldownTicks, 10);
    }

    private void cancelAmalgMergeOut() {
        this.amalgMergeInto = null;
        this.amalgMergeStartAge = -1;
        this.amalgMergeTicks = 0;
        this.amalgGiveMul = 0.0f;
        this.prevAmalgOut01 = 0.0f;
        this.amalgOut01 = 0.0f;
    }

    public float getInterpAmalgMul(float partialTicks) {
        return this.prevAmalgMul + (this.amalgMul - this.prevAmalgMul) * partialTicks;
    }

    public float getInterpAmalgOut01(float partialTicks) {
        return this.prevAmalgOut01 + (this.amalgOut01 - this.prevAmalgOut01) * partialTicks;
    }

    public float getBaseScale() {
        return this.baseScale;
    }

    public float getLandScale() {
        return this.landScale;
    }

    public double getCollHalfY() {
        return this.collHalfY;
    }

    public void captureQueuedBillboard(float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        BloodCachesWater.captureBillboard(this, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
    }

    public void clearQueuedBillboard() {
        BloodCachesWater.clearBillboard(this);
    }

    public boolean hasQueuedBillboard() {
        return BloodCachesWater.hasBillboard(this);
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

    public double getDistanceSqTo(double x, double y, double z) {
        double dx = this.getPosX() - x;
        double dy = this.getPosY() - y;
        double dz = this.getPosZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public void onUpdate() {
        this.amalgPreUpdate();
        BloodMotionWater.tick(this);
        this.amalgPostUpdate();
    }

    public void renderParticle(@Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        BloodWaterRendering.renderParticle(this, buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
    }

    public void renderQueuedDroplet(@Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks) {
        BloodWaterRendering.renderQueuedDroplet(this, buffer, entityIn, partialTicks);
    }

    public void renderSurfaceDecal(@Nonnull BufferBuilder buffer, float partialTicks) {
        if (this.heavy) {
            return;
        }
        BloodWaterRendering.renderSurfaceDecal(this, buffer, partialTicks);
    }

    public void renderFloorDecal(@Nonnull BufferBuilder buffer, float partialTicks) {
        BloodWaterRendering.renderFloorDecal(this, buffer, partialTicks);
    }

    public void renderVanillaBillboard(@Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        super.renderParticle(buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
    }

    public Level getParticleWorld() {
        return this.world;
    }

    @Nullable
    public TextureAtlasSprite getSprite() {
        return this.particleTexture;
    }

    public RandomSource getRng() {
        return this.rand;
    }

    public double getPosX() {
        return this.posX;
    }

    public double getPosY() {
        return this.posY;
    }

    public double getPosZ() {
        return this.posZ;
    }

    public double getPrevPosX() {
        return this.prevPosX;
    }

    public double getPrevPosY() {
        return this.prevPosY;
    }

    public double getPrevPosZ() {
        return this.prevPosZ;
    }

    public void copyPosToPrev() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
    }

    public void setParticlePos(double x, double y, double z) {
        this.setPosition(x, y, z);
    }

    public void moveParticle(double dx, double dy, double dz) {
        this.move(dx, dy, dz);
    }

    public double getMotionX() {
        return this.motionX;
    }

    public double getMotionY() {
        return this.motionY;
    }

    public double getMotionZ() {
        return this.motionZ;
    }

    public void setMotionX(double v) {
        this.motionX = v;
    }

    public void setMotionY(double v) {
        this.motionY = v;
    }

    public void setMotionZ(double v) {
        this.motionZ = v;
    }

    public int getAge() {
        return this.particleAge;
    }

    public int getMaxAge() {
        return this.particleMaxAge;
    }

    public boolean incrementAgeAndShouldExpire() {
        return this.particleAge++ >= this.particleMaxAge;
    }

    public float getParticleAlpha() {
        return this.particleAlpha;
    }

    public void setParticleAlpha(float a) {
        this.particleAlpha = a;
    }

    public float getParticleScale() {
        return this.particleScale;
    }

    public void setParticleScale(float s) {
        this.particleScale = s;
    }

    public float getParticleRed() {
        return this.particleRed;
    }

    public float getParticleGreen() {
        return this.particleGreen;
    }

    public float getParticleBlue() {
        return this.particleBlue;
    }

    public void setParticleColor(float r, float g, float b) {
        this.particleRed = r;
        this.particleGreen = g;
        this.particleBlue = b;
    }

    public float getParticleAngle() {
        return this.particleAngle;
    }

    public void setParticleAngle(float a) {
        this.particleAngle = a;
    }

    public void setPrevParticleAngle(float a) {
        this.prevParticleAngle = a;
    }

    public static double getCameraX() {
        return interpPosX;
    }

    public static double getCameraY() {
        return interpPosY;
    }

    public static double getCameraZ() {
        return interpPosZ;
    }

    public static void setCamera(double x, double y, double z) {
        interpPosX = x;
        interpPosY = y;
        interpPosZ = z;
    }

    private void syncDecalTracking() {
        this.setTrackReason(1, this.onSurface || this.onFloor);
    }

    public void trackIfNeeded() {
        this.syncDecalTracking();
    }

    public void untrackIfNeeded() {
        this.setTrackReason(1, false);
    }

    public void expireAndUntrack() {
        this.setExpired();
        this.cache.track.mask = 0;
        this.refreshTrackingRegistration();
    }
}

