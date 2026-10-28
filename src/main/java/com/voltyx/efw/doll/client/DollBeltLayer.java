package com.voltyx.efw.doll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voltyx.efw.EFW;
import com.voltyx.efw.doll.DollAbility;
import com.voltyx.efw.doll.DollConfig;
import com.voltyx.efw.doll.DollItem;
import com.voltyx.efw.tech.client.BodycamFeed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The doll hanging on the belt while it is carried (port of DollBaubleLayer): in the inventory, a backpack or a Curios slot. It
 * swings with the steps and lags behind jumps and landings; with blood on the body it is stained too.
 */
public class DollBeltLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final DollModel MODEL = new DollModel();
    private static final ResourceLocation TEXTURE = new ResourceLocation(EFW.MOD_ID, "textures/models/armor/doll_bauble.png");

    private static final float BASE_POS_X = 3.0F;
    private static final float BASE_POS_Y = 11.0F;
    private static final float BASE_POS_Z = 2.0F;
    private static final float BASE_ROT_X = (float) Math.toRadians(-95.26349);
    private static final float BASE_ROT_Y = (float) Math.toRadians(-24.78894);
    private static final float BASE_ROT_Z = (float) Math.toRadians(179.62707);

    private static final Map<Player, State> STATES = new WeakHashMap<>();

    private static final class State {
        int lastTick = -1;
        int checkedTick = -1;
        boolean hasDoll;
        double lastY;
        float prevBounce, bounce, bounceVel;
        float prevPitch, pitch, pitchVel;
        boolean wasOnGround = true;
        float landing;

        void update(Player player) {
            if (lastTick == -1) {
                lastY = player.getY();
                wasOnGround = player.onGround();
                lastTick = player.tickCount;
                return;
            }
            double velY = player.getY() - lastY;
            lastY = player.getY();
            boolean inAir = !player.onGround() && !player.isInWater() && !player.getAbilities().flying;
            if (!inAir && !wasOnGround) {
                // just landed: the doll sags down by inertia
                landing = (float) Math.min(1.4, Math.max(0.3, -velY * 2.2));
            }
            wasOnGround = player.onGround();
            float target = (float) Math.max(-0.6, Math.min(1.1, velY * 1.5));
            if (player.onGround() && player.walkAnimation.speed() > 0.05F) {
                float mul = player.isSprinting() ? 1.3F : 0.7F;
                target += (float) -Math.abs(Math.sin(player.walkAnimation.position() * 0.50F)) * 0.30F * player.walkAnimation.speed() * mul;
            }
            if (landing > 0.01F) {
                target += landing;
                landing *= 0.60F;
            }
            prevBounce = bounce;
            bounceVel = (bounceVel + (target - bounce) * 0.35F) * 0.68F;
            bounce += bounceVel;
            prevPitch = pitch;
            float targetPitch = (float) Math.max(-0.35, Math.min(0.45, velY * 0.7));
            pitchVel = (pitchVel + (targetPitch - pitch) * 0.30F) * 0.70F;
            pitch += pitchVel;
        }
    }

    private static boolean loadModel() {
        if (!MODEL.isLoaded()) {
            MODEL.load(new ResourceLocation(EFW.MOD_ID, "bedrock/doll_bauble.geo.json"), null);
        }
        return MODEL.isLoaded();
    }

    /**
     * The doll on the belt of a dead body (Ragdollified's ragdoll or corpse): {@code pose} is already at the body part, the doll
     * hangs still in the rest pose of the inventory screen.
     */
    public static void renderOnBody(PoseStack pose, MultiBufferSource buffers, int light, ItemStack chest) {
        if (!DollConfig.SHOW_ON_BELT.get() || !loadModel()) {
            return;
        }
        MODEL.setBoneRotation("Head2", (float) Math.toRadians(12.5), 0.0F, (float) Math.toRadians(-17.5));
        MODEL.setBoneRotation("LHand", 0.0F, 0.0F, 0.0F);
        MODEL.setBoneRotation("RHand", 0.0F, 0.0F, 0.0F);
        MODEL.setBoneRotation("bone", (float) Math.toRadians(-100.0), 0.0F, 0.0F);
        MODEL.setBoneRotation("legr", (float) Math.toRadians(22.5), (float) Math.toRadians(22.5), 0.0F);
        MODEL.setBoneRotation("legl", (float) Math.toRadians(20.0), (float) Math.toRadians(-22.5), 0.0F);
        DollBodyOffsets.Offset off = DollBodyOffsets.forChest(chest);
        MODEL.renderBranch(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), "bone2",
                BASE_POS_X + off.posX, BASE_POS_Y + off.posY, BASE_POS_Z + off.posZ,
                BASE_ROT_X + (float) Math.toRadians(off.rotX), BASE_ROT_Y + (float) Math.toRadians(off.rotY),
                BASE_ROT_Z + (float) Math.toRadians(off.rotZ), off.scale, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    public DollBeltLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float age, float headYaw, float headPitch) {
        if (!DollConfig.SHOW_ON_BELT.get() || player.isInvisible() || BodycamFeed.isRendering()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // the own doll is in the hands in the first person; the inventory screen still shows it on the character
        boolean inGui = player == mc.player && mc.screen != null;
        if (!inGui && player == mc.player && mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        State state = STATES.computeIfAbsent(player, p -> new State());
        if (state.checkedTick != player.tickCount) {
            state.checkedTick = player.tickCount;
            // the inventory of other players is not on this client: the server tells who carries the doll
            state.hasDoll = DollItem.showsOnBelt(player) || (player != mc.player && DollCarriers.carries(player.getUUID()));
        }
        if (!state.hasDoll || (!inGui && DollItem.isHoldingDoll(player))) {
            return;
        }
        if (!loadModel()) {
            return;
        }
        if (state.lastTick != player.tickCount) {
            state.update(player);
            state.lastTick = player.tickCount;
        }

        float bounce = 0.0F;
        float inertia = 0.0F;
        if (!inGui) {
            bounce = Mth.lerp(partialTick, state.prevBounce, state.bounce);
            inertia = Mth.lerp(partialTick, state.prevPitch, state.pitch);
        }

        float headX = (float) Math.toRadians(12.5), headY = 0.0F, headZ = (float) Math.toRadians(-17.5);
        float lHandX = 0.0F, lHandY = 0.0F, rHandX = 0.0F, rHandY = 0.0F;
        float boneX = (float) Math.toRadians(-100.0);
        float legrX = (float) Math.toRadians(22.5), legrY = (float) Math.toRadians(22.5);
        float leglX = (float) Math.toRadians(20.0), leglY = (float) Math.toRadians(-22.5);
        if (!inGui) {
            float walk = limbSwing * 0.50F;
            float amount = limbSwingAmount;
            float idle = (player.tickCount + partialTick) * 0.05F;
            headX += (float) Math.sin(walk) * 0.11F * amount + (float) Math.sin(idle) * 0.02F + inertia * 0.4F;
            headY += (float) Math.cos(walk * 0.5F) * 0.07F * amount + (float) Math.cos(idle * 0.7F) * 0.015F;
            headZ += (float) Math.sin(walk * 0.5F) * 0.05F * amount;
            lHandX += (float) Math.sin(walk) * 0.20F * amount + (float) Math.sin(idle + 0.5F) * 0.015F + inertia * 0.5F;
            lHandY += (float) Math.cos(walk * 0.5F) * 0.06F * amount + (float) Math.cos(idle * 0.6F) * 0.01F;
            rHandX += (float) Math.sin(walk + (float) Math.PI * 0.7F) * 0.18F * amount + (float) Math.sin(idle + 1.8F) * 0.015F + inertia * 0.5F;
            rHandY += -(float) Math.cos(walk * 0.5F) * 0.06F * amount - (float) Math.cos(idle * 0.6F) * 0.01F;
            boneX += (float) Math.sin(walk) * 0.16F * amount + (float) Math.sin(idle * 0.9F) * 0.02F + inertia * 0.7F;
            leglX += (float) Math.sin(walk - 0.4F) * 0.12F * amount + (float) Math.sin(idle + 0.8F) * 0.015F;
            leglY += (float) Math.cos(walk * 0.5F) * 0.05F * amount;
            legrX += (float) Math.sin(walk + 0.6F) * 0.12F * amount + (float) Math.sin(idle + 2.0F) * 0.015F;
            legrY += -(float) Math.cos(walk * 0.5F) * 0.05F * amount;
        }
        MODEL.setBoneRotation("Head2", headX, headY, headZ);
        MODEL.setBoneRotation("LHand", lHandX, lHandY, 0.0F);
        MODEL.setBoneRotation("RHand", rHandX, rHandY, 0.0F);
        MODEL.setBoneRotation("bone", boneX, 0.0F, 0.0F);
        MODEL.setBoneRotation("legr", legrX, legrY, 0.0F);
        MODEL.setBoneRotation("legl", leglX, leglY, 0.0F);

        float shade = 1.0F;
        if (player == mc.player && DollAbility.isReady(player)) {
            float pulse = (float) (Math.sin((System.currentTimeMillis() % 800L) / 800.0 * Math.PI * 2.0) * 0.5 + 0.5);
            shade = 1.0F - pulse * 0.85F;
        }

        DollBodyOffsets.Offset off = DollBodyOffsets.forPlayer(player);
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        MODEL.renderBranch(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), "bone2",
                BASE_POS_X + off.posX, BASE_POS_Y + off.posY + bounce, BASE_POS_Z + off.posZ,
                BASE_ROT_X + (float) Math.toRadians(off.rotX), BASE_ROT_Y + (float) Math.toRadians(off.rotY),
                BASE_ROT_Z + (float) Math.toRadians(off.rotZ), off.scale, light, OverlayTexture.NO_OVERLAY, 1.0F, shade, shade, 1.0F);
        pose.popPose();
    }
}
