package com.voltyx.efw.gore.particle.state.liquid.lava;

import com.voltyx.efw.gore.json.BloodAmalgamationPolicy;
import com.voltyx.efw.gore.json.BloodBrightnessMode;
import com.voltyx.efw.gore.json.BloodStyle;
import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.BloodTuning;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaLava;
import com.voltyx.efw.gore.particle.common.amalgamation.BloodAmalgamationLava;
import com.voltyx.efw.gore.particle.common.amalgamation.BloodAmalgamationLiquid;
import com.voltyx.efw.gore.particle.common.cache.BloodCaches;
import com.voltyx.efw.gore.particle.common.cache.BloodCachesLava;
import com.voltyx.efw.gore.particle.common.motion.BloodMotionLava;
import com.voltyx.efw.gore.particle.render.BloodRenderType;
import com.voltyx.efw.gore.particle.render.parts.BloodLavaRendering;
import com.voltyx.efw.gore.particle.state.BloodHeavy;
import com.voltyx.efw.gore.particle.state.liquid.BloodLiquidUtil;
import com.voltyx.efw.gore.particle.state.liquid.lava.BloodLavaCache;
import net.minecraft.util.RandomSource;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.voltyx.efw.gore.particle.LegacyParticle;
import com.voltyx.efw.gore.particle.render.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
public class BloodLava
extends LegacyParticle {
    public final BloodCaches.LavaState cache = new BloodCaches.LavaState();
    private boolean onSurface = false;
    private final BloodLavaCache surfaceCache;
    private static final int TRACK_BLOOD_PASS = 1;
    private static final int TRACK_DECAL = 2;
    private int surfaceGrowStartAge = -1;
    private final float groundRot;
    private final boolean flipU;
    private final double surfaceOffset;
    private final float driftSeed;
    private final int noiseSeed;
    private final float surfaceVisc;
    private final float flowSpeedMul;
    private final float turbMul;
    private float impactDirX = 0.0f;
    private float impactDirZ = 0.0f;
    private float impactSpeed = 0.0f;
    private int boilCooldownTicks = 0;
    private int boilBurstTicks = 0;
    private int boilBurstTotal = 0;
    private float boilDirX = 1.0f;
    private float boilDirZ = 0.0f;
    private int popLockoutTicks = 0;
    private int submergedTicks = 0;
    private final float baseScale;
    private final float landScale;
    private final double collHalfY;
    private float surfaceScale;
    private final float surfaceRotSpeed;
    private float surfaceRot;
    private float prevSurfaceRot;
    private final int lavaStartAge;
    private final float carriedAlphaMul;
    private BloodBrightnessMode brightnessMode = BloodBrightnessMode.WORLD;
    private boolean heavyLandedThisTick = false;
    private float heavyLandT = 1.0f;
    private double heavyLandStartX = 0.0;
    private double heavyLandStartY = 0.0;
    private double heavyLandStartZ = 0.0;
    private double heavyLandStepX = 0.0;
    private double heavyLandStepY = 0.0;
    private double heavyLandStepZ = 0.0;
    private float prevAmalgMul = 1.0f;
    private float amalgMul = 1.0f;
    private float amalgTargetMul = 1.0f;
    private int amalgCooldownTicks = 0;
    @Nullable
    private BloodLava amalgMergeInto = null;
    private int amalgMergeStartAge = -1;
    private int amalgMergeTicks = 0;
    private float amalgGiveMul = 0.0f;
    private float prevAmalgOut01 = 0.0f;
    private float amalgOut01 = 0.0f;
    private final BloodStyle fluidWeight;
    private final boolean heavy;
    private float surfaceExitAlphaStartMul = 1.0f;
    private int surfaceExitAlphaStartAge = -1;
    private int surfaceExitAlphaBlendTicks = 0;
    private BloodAmalgamationPolicy amalgamationPolicy = BloodAmalgamationPolicy.BOTH;

    public BloodLava(Level worldIn, double x, double y, double z, double motionX, double motionY, double motionZ, @Nullable TextureAtlasSprite sprite, float scale, float groundRot, boolean flipU, double surfaceOffset, int age, int maxAge, float initialAlpha, @Nullable BloodStyle weight) {
        super(worldIn, x, y, z, motionX, motionY, motionZ);
        float lifeFactor;
        this.surfaceCache = new BloodLavaCache(worldIn);
        if (sprite != null) {
            this.setParticleTexture(sprite);
        }
        this.groundRot = groundRot;
        this.flipU = flipU;
        this.surfaceOffset = surfaceOffset;
        this.driftSeed = this.rand.nextFloat();
        this.noiseSeed = this.rand.nextInt();
        this.surfaceVisc = (float)(0.7 + this.rand.nextDouble() * 0.14);
        this.flowSpeedMul = 0.85f + 0.3f * this.rand.nextFloat();
        this.turbMul = 0.75f + 0.55f * this.rand.nextFloat();
        this.landScale = Math.max(0.001f, scale);
        BloodStyle resolved = weight != null ? weight : ParticleBlood.resolveFluidStyleSafe();
        this.fluidWeight = BloodHeavy.normalizeWeight(resolved);
        this.heavy = BloodHeavy.isHeavyFluid(this.fluidWeight);
        float baseMul = BloodHeavy.lavaBaseScaleMul(this.heavy);
        this.baseScale = this.particleScale = Math.max(0.001f, this.landScale * baseMul);
        this.lavaStartAge = this.particleAge = Math.max(0, age);
        int srcMax = Math.max(1, maxAge);
        int extraTicks = (int)Math.ceil((double)srcMax * (double)(lifeFactor = this.heavy ? 0.5f : 0.3f));
        if (extraTicks < 1) {
            extraTicks = 1;
        }
        this.particleMaxAge = this.particleAge + extraTicks;
        if (this.particleMaxAge < this.particleAge + 1) {
            this.particleMaxAge = this.particleAge + 1;
        }
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
        this.surfaceScale = this.baseScale;
        this.carriedAlphaMul = BloodAlphaLava.lavaCarryAlpha(initialAlpha, this.particleAge, this.particleMaxAge, this.heavy);
        this.particleAlpha = BloodAlphaLava.lavaBaseAlpha(this.carriedAlphaMul, this.particleAge, this.particleMaxAge, this.heavy);
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
        this.trackInBloodPassIfNeeded();
    }

    public void captureQueuedBillboard(float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        BloodCachesLava.captureBillboard(this, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
    }

    public boolean hasQueuedBillboard() {
        return BloodCachesLava.hasBillboard(this);
    }

    public void clearQueuedBillboard() {
        BloodCachesLava.clearBillboard(this);
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

    @Deprecated
    public BloodLava(Level worldIn, double x, double y, double z, double motionX, double motionY, double motionZ, @Nullable TextureAtlasSprite sprite, float scale, float groundRot, boolean flipU, double surfaceOffset, int age, int maxAge, float initialAlpha) {
        this(worldIn, x, y, z, motionX, motionY, motionZ, sprite, scale, groundRot, flipU, surfaceOffset, age, maxAge, initialAlpha, ParticleBlood.resolveFluidStyleSafe());
    }

    public void startSurfaceExitAlphaBlend(float startMul, int ticks) {
        this.surfaceExitAlphaStartMul = BloodLiquidUtil.clamp01(startMul);
        this.surfaceExitAlphaBlendTicks = Math.max(0, ticks);
        this.surfaceExitAlphaStartAge = this.surfaceExitAlphaBlendTicks > 0 ? this.getAge() + 1 : -1;
    }

    public void clearSurfaceExitAlphaBlend() {
        this.surfaceExitAlphaStartMul = 1.0f;
        this.surfaceExitAlphaStartAge = -1;
        this.surfaceExitAlphaBlendTicks = 0;
    }

    public boolean hasSurfaceExitAlphaBlend() {
        return this.surfaceExitAlphaStartAge >= 0 && this.surfaceExitAlphaBlendTicks > 0;
    }

    public float getSurfaceExitAlphaMul01() {
        if (!this.hasSurfaceExitAlphaBlend()) {
            return 1.0f;
        }
        int dt = this.getAge() - this.surfaceExitAlphaStartAge;
        if (dt >= this.surfaceExitAlphaBlendTicks) {
            this.clearSurfaceExitAlphaBlend();
            return 1.0f;
        }
        return BloodAlphaLava.lavaExitAlpha(this.getAge(), this.surfaceExitAlphaStartMul, this.surfaceExitAlphaStartAge, this.surfaceExitAlphaBlendTicks);
    }

    public int getFXLayer() {
        return 1;
    }

    public BloodStyle getFluidWeight() {
        return this.fluidWeight;
    }

    public boolean isSurfaceDecal() {
        return this.isOnSurface() && this.isAlive();
    }

    public BloodLavaCache getCache() {
        return this.surfaceCache;
    }

    public boolean isOnSurface() {
        return this.onSurface;
    }

    public void setOnSurface(boolean v) {
        this.onSurface = v;
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

    public boolean isFlipU() {
        return this.flipU;
    }

    public double getSurfaceOffset() {
        return this.surfaceOffset;
    }

    public float getDriftSeed() {
        return this.driftSeed;
    }

    public int getNoiseSeed() {
        return this.noiseSeed;
    }

    public float getSurfaceVisc() {
        return this.surfaceVisc;
    }

    public float getFlowSpeedMul() {
        return this.flowSpeedMul;
    }

    public float getTurbMul() {
        return this.turbMul;
    }

    public int getSubmergedTicks() {
        return this.submergedTicks;
    }

    public void setSubmergedTicks(int t) {
        this.submergedTicks = Math.max(0, t);
    }

    private void setTrackReason(int bit, boolean enable) {
        BloodCaches.Track track = this.cache.track;
        int before = track.mask;
        track.mask = enable ? (track.mask |= bit) : (track.mask &= ~bit);
        if (before != track.mask) {
            this.refreshTrackingRegistration();
        }
    }

    public BloodAmalgamationPolicy getAmalgamationPolicy() {
        return this.amalgamationPolicy;
    }

    public void setAmalgamationPolicy(@Nullable BloodAmalgamationPolicy p) {
        this.amalgamationPolicy = p != null ? p : BloodAmalgamationPolicy.BOTH;
    }

    private void refreshTrackingRegistration() {
        boolean wantTracked;
        BloodCaches.Track track = this.cache.track;
        boolean bl = wantTracked = track.mask != 0;
        if (wantTracked && !track.tracked) {
            BloodRenderType.trackLava(this);
            track.tracked = true;
        } else if (!wantTracked && track.tracked) {
            BloodRenderType.untrackLava(this);
            track.tracked = false;
        }
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
                BloodLava into = this.amalgMergeInto;
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
            this.setMotionX(0.0);
            this.setMotionZ(0.0);
            this.setMotionY(0.0);
            this.resetBoilBurst();
            this.clearImpact();
            return;
        }
        if (this.isOnSurface() && BloodAmalgamationLiquid.allow(this.amalgamationPolicy, this.getFluidWeight()) && this.getBoilVisual01() <= 0.001f) {
            BloodAmalgamationLava.tryMerge(this);
        }
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

    public void startAmalgMergeOut(@Nonnull BloodLava into, float giveMul) {
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
        this.setMotionX(0.0);
        this.setMotionZ(0.0);
        this.setMotionY(0.0);
        this.resetBoilBurst();
        this.clearImpact();
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

    public float getImpactDirX() {
        return this.impactDirX;
    }

    public float getImpactDirZ() {
        return this.impactDirZ;
    }

    public float getImpactSpeed() {
        return this.impactSpeed;
    }

    public void clearImpact() {
        this.impactDirX = 0.0f;
        this.impactDirZ = 0.0f;
        this.impactSpeed = 0.0f;
    }

    public void setImpact(float dirX, float dirZ, float speed) {
        float lenSq = dirX * dirX + dirZ * dirZ;
        if (lenSq > 1.0E-10f) {
            float inv = (float)(1.0 / Math.sqrt(lenSq));
            dirX *= inv;
            dirZ *= inv;
        } else {
            dirX = 1.0f;
            dirZ = 0.0f;
        }
        this.impactDirX = dirX;
        this.impactDirZ = dirZ;
        this.impactSpeed = Math.max(0.0f, speed);
    }

    public int getBoilCooldownTicks() {
        return this.boilCooldownTicks;
    }

    public void setBoilCooldownTicks(int t) {
        this.boilCooldownTicks = Math.max(0, t);
    }

    public int getBoilBurstTicks() {
        return this.boilBurstTicks;
    }

    public int getBoilBurstTotal() {
        return this.boilBurstTotal;
    }

    public float getBoilDirX() {
        return this.boilDirX;
    }

    public float getBoilDirZ() {
        return this.boilDirZ;
    }

    public boolean isBoiling() {
        return this.boilBurstTicks > 0;
    }

    public void resetBoilBurst() {
        this.boilBurstTicks = 0;
        this.boilBurstTotal = 0;
    }

    public void startBoilBurst(int totalTicks, float dirX, float dirZ) {
        totalTicks = Math.max(1, totalTicks);
        float lenSq = dirX * dirX + dirZ * dirZ;
        if (lenSq > 1.0E-10f) {
            float inv = (float)(1.0 / Math.sqrt(lenSq));
            dirX *= inv;
            dirZ *= inv;
        } else {
            dirX = 1.0f;
            dirZ = 0.0f;
        }
        this.boilBurstTotal = totalTicks;
        this.boilBurstTicks = totalTicks;
        this.boilDirX = dirX;
        this.boilDirZ = dirZ;
    }

    public void tickBoilBurstDown() {
        if (this.boilBurstTicks > 0) {
            --this.boilBurstTicks;
        }
        if (this.boilBurstTicks <= 0) {
            this.boilBurstTicks = 0;
            this.boilBurstTotal = 0;
        }
    }

    public float getBoilVisual01() {
        if (this.boilBurstTicks <= 0 || this.boilBurstTotal <= 0) {
            return 0.0f;
        }
        float t = (float)this.boilBurstTicks / (float)this.boilBurstTotal;
        return BloodLiquidUtil.clamp01(t);
    }

    public int getPopLockoutTicks() {
        return this.popLockoutTicks;
    }

    public void setPopLockoutTicks(int t) {
        this.popLockoutTicks = Math.max(0, t);
    }

    public void tickPopLockoutDown() {
        if (this.popLockoutTicks > 0) {
            this.popLockoutDown();
        }
    }

    private void popLockoutDown() {
        --this.popLockoutTicks;
    }

    public float getBaseScale() {
        return this.baseScale;
    }

    public double getCollHalfY() {
        return this.collHalfY;
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

    public float getCarriedAlphaMul() {
        return this.carriedAlphaMul;
    }

    public int getLavaStartAge() {
        return this.lavaStartAge;
    }

    public boolean isHeavyInLava() {
        return this.heavy;
    }

    public void onUpdate() {
        this.amalgPreUpdate();
        this.clearHeavyLandingInfo();
        BloodMotionLava.tick(this);
        this.amalgPostUpdate();
    }

    public void renderParticle(@Nonnull BufferBuilder buffer, @Nonnull Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        if (this.getParticleAlpha() <= 0.001f) {
            return;
        }
        this.captureQueuedBillboard(rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
        BloodRenderType.queueLavaBillboard(this);
    }

    public void renderSurfaceDecal(@Nonnull BufferBuilder buffer, float partialTicks) {
        BloodLavaRendering.renderSurfaceDecal(this, buffer, partialTicks);
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

    public void setRawPos(double x, double y, double z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    public void setRawPrevPos(double x, double y, double z) {
        this.prevPosX = x;
        this.prevPosY = y;
        this.prevPosZ = z;
    }

    public boolean isHeavyLandedThisTick() {
        return this.heavyLandedThisTick;
    }

    public float getHeavyLandT() {
        return this.heavyLandT;
    }

    public double getHeavyLandStartX() {
        return this.heavyLandStartX;
    }

    public double getHeavyLandStartY() {
        return this.heavyLandStartY;
    }

    public double getHeavyLandStartZ() {
        return this.heavyLandStartZ;
    }

    public double getHeavyLandStepX() {
        return this.heavyLandStepX;
    }

    public double getHeavyLandStepY() {
        return this.heavyLandStepY;
    }

    public double getHeavyLandStepZ() {
        return this.heavyLandStepZ;
    }

    public void clearHeavyLandingInfo() {
        this.heavyLandedThisTick = false;
        this.heavyLandT = 1.0f;
        this.heavyLandStartZ = 0.0;
        this.heavyLandStartY = 0.0;
        this.heavyLandStartX = 0.0;
        this.heavyLandStepZ = 0.0;
        this.heavyLandStepY = 0.0;
        this.heavyLandStepX = 0.0;
    }

    public void setHeavyLandingInfo(double startX, double startY, double startZ, double stepX, double stepY, double stepZ, float landT01) {
        this.heavyLandedThisTick = true;
        this.heavyLandStartX = startX;
        this.heavyLandStartY = startY;
        this.heavyLandStartZ = startZ;
        this.heavyLandStepX = stepX;
        this.heavyLandStepY = stepY;
        this.heavyLandStepZ = stepZ;
        this.heavyLandT = BloodLiquidUtil.clamp01(landT01);
    }

    public void trackIfNeeded() {
        this.setTrackReason(2, this.isSurfaceDecal());
    }

    public void untrackIfNeeded() {
        this.setTrackReason(2, false);
    }

    public void trackInBloodPassIfNeeded() {
        this.setTrackReason(1, true);
    }

    public void expireAndUntrack() {
        this.setExpired();
        this.cache.track.mask = 0;
        this.refreshTrackingRegistration();
    }

    public double getDistanceSqTo(double x, double y, double z) {
        double dx = this.posX - x;
        double dy = this.posY - y;
        double dz = this.posZ - z;
        return dx * dx + dy * dy + dz * dz;
    }
}

