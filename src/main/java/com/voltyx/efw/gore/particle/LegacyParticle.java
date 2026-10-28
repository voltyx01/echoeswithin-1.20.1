package com.voltyx.efw.gore.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.voltyx.efw.gore.particle.render.BufferBuilder;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * The particle of Minecraft 1.12 on top of the one of 1.20 (Extra Gore was written for 1.12): the same fields and methods under the
 * same names, with their 1.12 behaviour (update, collision, size, light, the billboard), so the blood logic runs as it was written.
 * The vanilla 1.20 fields (x, xo, xd ...) are kept in step for the rest of the game.
 *
 * <p>Particles are drawn by the gore renderer itself (see {@code BloodRenderType}): {@link #render} only hands the particle the
 * camera, in the 1.12 form (the five billboard rotations and the interpolated camera position).</p>
 */
public abstract class LegacyParticle extends Particle {
    public static double interpPosX;
    public static double interpPosY;
    public static double interpPosZ;
    public static Vec3 cameraViewDir = Vec3.ZERO;
    /** 1.12 LegacyParticle.rotationX ... of the current frame. */
    public static float rotationX;
    public static float rotationZ;
    public static float rotationYZ;
    public static float rotationXY;
    public static float rotationXZ;

    protected final ClientLevel world;
    protected final RandomSource rand;
    public double prevPosX;
    public double prevPosY;
    public double prevPosZ;
    public double posX;
    public double posY;
    public double posZ;
    public double motionX;
    public double motionY;
    public double motionZ;
    protected float width = 0.6f;
    protected float height = 1.8f;
    protected boolean canCollide = true;
    protected int particleAge;
    protected int particleMaxAge;
    protected float particleScale;
    protected float particleGravity;
    protected float particleRed = 1.0f;
    protected float particleGreen = 1.0f;
    protected float particleBlue = 1.0f;
    protected float particleAlpha = 1.0f;
    protected float particleAngle;
    protected float prevParticleAngle;
    protected TextureAtlasSprite particleTexture;

    protected LegacyParticle(Level level, double x, double y, double z) {
        super((ClientLevel) level, x, y, z);
        this.world = (ClientLevel) level;
        this.rand = this.random;
        this.width = 0.6f;
        this.height = 1.8f;
        setBoundingBox(new AABB(0, 0, 0, 0, 0, 0));
        setSize(0.2f, 0.2f);
        setPosition(x, y, z);
        this.prevPosX = x;
        this.prevPosY = y;
        this.prevPosZ = z;
        this.particleRed = this.particleGreen = this.particleBlue = 1.0f;
        this.particleAlpha = 1.0f;
        this.particleScale = (this.rand.nextFloat() * 0.5f + 0.5f) * 2.0f;
        this.particleMaxAge = (int) (4.0f / (this.rand.nextFloat() * 0.9f + 0.1f));
        this.particleAge = 0;
        this.canCollide = true;
    }

    /** 1.12 Particle(world, x, y, z, xSpeed, ySpeed, zSpeed): the given motion with some random spread. */
    protected LegacyParticle(Level level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        this(level, x, y, z);
        this.motionX = xSpeed + (Math.random() * 2.0 - 1.0) * 0.4;
        this.motionY = ySpeed + (Math.random() * 2.0 - 1.0) * 0.4;
        this.motionZ = zSpeed + (Math.random() * 2.0 - 1.0) * 0.4;
        float f = (float) (Math.random() + Math.random() + 1.0) * 0.15f;
        float len = (float) Math.sqrt(this.motionX * this.motionX + this.motionY * this.motionY + this.motionZ * this.motionZ);
        this.motionX = this.motionX / (double) len * (double) f * 0.4;
        this.motionY = this.motionY / (double) len * (double) f * 0.4 + 0.1;
        this.motionZ = this.motionZ / (double) len * (double) f * 0.4;
    }

    // ------------------------------------------------------------------------------------------------ 1.12 behaviour

    @Override
    public final void tick() {
        onUpdate();
        this.xo = this.prevPosX;
        this.yo = this.prevPosY;
        this.zo = this.prevPosZ;
    }

    /** 1.12 Particle.onUpdate. */
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        if (this.particleAge++ >= this.particleMaxAge) {
            this.setExpired();
        }
        this.motionY -= 0.04 * (double) this.particleGravity;
        this.move(this.motionX, this.motionY, this.motionZ);
        this.motionX *= 0.98;
        this.motionY *= 0.98;
        this.motionZ *= 0.98;
        if (this.onGround) {
            this.motionX *= 0.7;
            this.motionZ *= 0.7;
        }
    }

    /** 1.12 Particle.move: collides with the blocks (when it can), lands on the ground, stops sideways where it hits. */
    @Override
    public void move(double x, double y, double z) {
        double origY = y;
        double origX = x;
        double origZ = z;
        if (this.canCollide && (x != 0.0 || y != 0.0 || z != 0.0) && x * x + y * y + z * z < 10000.0) {
            Vec3 v = Entity.collideBoundingBox(null, new Vec3(x, y, z), this.getBoundingBox(), this.world, List.of());
            x = v.x;
            y = v.y;
            z = v.z;
        }
        if (x != 0.0 || y != 0.0 || z != 0.0) {
            this.setBoundingBox(this.getBoundingBox().move(x, y, z));
        }
        this.resetPositionToBB();
        this.onGround = origY != y && origY < 0.0;
        if (origX != x) {
            this.motionX = 0.0;
        }
        if (origZ != z) {
            this.motionZ = 0.0;
        }
    }

    protected void resetPositionToBB() {
        AABB bb = this.getBoundingBox();
        this.posX = (bb.minX + bb.maxX) / 2.0;
        this.posY = bb.minY;
        this.posZ = (bb.minZ + bb.maxZ) / 2.0;
        this.x = this.posX;
        this.y = this.posY;
        this.z = this.posZ;
    }

    @Override
    protected void setSize(float w, float h) {
        if (w != this.width || h != this.height) {
            this.width = w;
            this.height = h;
            AABB bb = this.getBoundingBox();
            this.setBoundingBox(new AABB(bb.minX, bb.minY, bb.minZ, bb.minX + w, bb.minY + h, bb.minZ + w));
        }
    }

    public void setPosition(double x, double y, double z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        float f = this.width / 2.0f;
        this.setBoundingBox(new AABB(x - f, y, z - f, x + f, y + this.height, z + f));
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void setExpired() {
        this.remove();
    }

    public void setParticleTexture(TextureAtlasSprite sprite) {
        this.particleTexture = sprite;
    }

    public void setRBGColorF(float r, float g, float b) {
        this.particleRed = r;
        this.particleGreen = g;
        this.particleBlue = b;
    }

    public void setAlphaF(float a) {
        this.particleAlpha = a;
    }

    public float getRedColorF() {
        return this.particleRed;
    }

    public float getGreenColorF() {
        return this.particleGreen;
    }

    public float getBlueColorF() {
        return this.particleBlue;
    }

    public void setMaxAge(int age) {
        this.particleMaxAge = age;
    }

    public int getFXLayer() {
        return 1;
    }

    /** 1.12 Particle.getBrightnessForRender: the packed light where the particle is. */
    public int getBrightnessForRender(float partialTicks) {
        BlockPos pos = BlockPos.containing(this.posX, this.posY, this.posZ);
        return this.world.isLoaded(pos) ? LevelRenderer.getLightColor(this.world, pos) : 0;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return getBrightnessForRender(partialTick);
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.CUSTOM;
    }

    @Override
    public boolean shouldCull() {
        return false;
    }

    /**
     * The five billboard rotations of 1.12's ActiveRenderInfo for this camera, in the order 1.12's ParticleManager passes them to
     * renderParticle: X, XZ, Z, YZ, XY (the parameters are named X, Z, YZ, XY, XZ, so a parameter's name is not what it holds).
     */
    public static float[] billboard(Camera camera) {
        float yaw = camera.getYRot() * Mth.DEG_TO_RAD;
        float pitch = camera.getXRot() * Mth.DEG_TO_RAD;
        float rotationX = Mth.cos(yaw);
        float rotationZ = Mth.sin(yaw);
        float rotationYZ = -rotationZ * Mth.sin(pitch);
        float rotationXY = rotationX * Mth.sin(pitch);
        float rotationXZ = Mth.cos(pitch);
        return new float[] {rotationX, rotationXZ, rotationZ, rotationYZ, rotationXY};
    }

    /** The camera in the 1.12 form: its position (interpPos) and where it looks. */
    public static void setCamera(Camera camera) {
        Vec3 cam = camera.getPosition();
        interpPosX = cam.x;
        interpPosY = cam.y;
        interpPosZ = cam.z;
        Vector3f look = camera.getLookVector();
        cameraViewDir = new Vec3(look.x(), look.y(), look.z());
        float[] r = billboard(camera);
        rotationX = r[0];
        rotationXZ = r[1];
        rotationZ = r[2];
        rotationYZ = r[3];
        rotationXY = r[4];
    }

    @Override
    public void render(VertexConsumer ignored, Camera camera, float partialTicks) {
        setCamera(camera);
        float[] r = billboard(camera);
        Entity view = camera.getEntity();
        renderParticle(BufferBuilder.PARTICLES, view, partialTicks, r[0], r[1], r[2], r[3], r[4]);
    }

    /** 1.12 Particle.renderParticle: a camera facing square of the particle's sprite, turned by its angle. */
    public void renderParticle(BufferBuilder buffer, Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ,
            float rotationXY, float rotationXZ) {
        float u0 = 0.0f;
        float u1 = 1.0f;
        float v0 = 0.0f;
        float v1 = 1.0f;
        if (this.particleTexture != null) {
            u0 = this.particleTexture.getU0();
            u1 = this.particleTexture.getU1();
            v0 = this.particleTexture.getV0();
            v1 = this.particleTexture.getV1();
        }
        float s = 0.1f * this.particleScale;
        float x = (float) (this.prevPosX + (this.posX - this.prevPosX) * (double) partialTicks - interpPosX);
        float y = (float) (this.prevPosY + (this.posY - this.prevPosY) * (double) partialTicks - interpPosY);
        float z = (float) (this.prevPosZ + (this.posZ - this.prevPosZ) * (double) partialTicks - interpPosZ);
        int light = this.getBrightnessForRender(partialTicks);
        int hi = light >> 16 & 0xFFFF;
        int lo = light & 0xFFFF;
        Vec3[] c = new Vec3[] {
                new Vec3(-rotationX * s - rotationXY * s, -rotationZ * s, -rotationYZ * s - rotationXZ * s),
                new Vec3(-rotationX * s + rotationXY * s, rotationZ * s, -rotationYZ * s + rotationXZ * s),
                new Vec3(rotationX * s + rotationXY * s, rotationZ * s, rotationYZ * s + rotationXZ * s),
                new Vec3(rotationX * s - rotationXY * s, -rotationZ * s, rotationYZ * s - rotationXZ * s)};
        if (this.particleAngle != 0.0f) {
            float angle = this.particleAngle + (this.particleAngle - this.prevParticleAngle) * partialTicks;
            float cos = Mth.cos(angle * 0.5f);
            float sin = Mth.sin(angle * 0.5f);
            Vec3 axis = new Vec3(sin * cameraViewDir.x, sin * cameraViewDir.y, sin * cameraViewDir.z);
            for (int i = 0; i < 4; i++) {
                c[i] = axis.scale(2.0 * c[i].dot(axis)).add(c[i].scale((double) (cos * cos) - axis.dot(axis)))
                        .add(axis.cross(c[i]).scale(2.0f * cos));
            }
        }
        buffer.pos(x + c[0].x, y + c[0].y, z + c[0].z).tex(u1, v1).color(this.particleRed, this.particleGreen, this.particleBlue, this.particleAlpha).lightmap(hi, lo).endVertex();
        buffer.pos(x + c[1].x, y + c[1].y, z + c[1].z).tex(u1, v0).color(this.particleRed, this.particleGreen, this.particleBlue, this.particleAlpha).lightmap(hi, lo).endVertex();
        buffer.pos(x + c[2].x, y + c[2].y, z + c[2].z).tex(u0, v0).color(this.particleRed, this.particleGreen, this.particleBlue, this.particleAlpha).lightmap(hi, lo).endVertex();
        buffer.pos(x + c[3].x, y + c[3].y, z + c[3].z).tex(u0, v1).color(this.particleRed, this.particleGreen, this.particleBlue, this.particleAlpha).lightmap(hi, lo).endVertex();
    }
}
