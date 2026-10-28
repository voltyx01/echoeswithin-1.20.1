package com.voltyx.efw.doll.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import com.voltyx.efw.EFW;
import com.voltyx.efw.blood.client.BloodClient;
import com.voltyx.efw.compat.ShoulderCompat;
import com.voltyx.efw.doll.DollAbility;
import com.voltyx.efw.doll.DollConfig;
import com.voltyx.efw.doll.DollContent;
import com.voltyx.efw.doll.DollItem;
import com.voltyx.efw.network.EfwNetwork;
import com.voltyx.efw.speech.client.SpeechClientManager;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Client side of Saya's doll (port of DollRenderer and the client half of SayaDollManager): the doll that comes up from below the
 * screen in the first person, its pose in the hand, the timers of the activation, the dark screen and the HUD that slides away.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class DollClient {
    private static final ResourceLocation TEXTURE = new ResourceLocation(EFW.MOD_ID, "textures/entity/doll.png");
    private static final ResourceLocation VIGNETTE = new ResourceLocation("textures/misc/vignette.png");
    private static final DollModel MODEL = new DollModel();
    private static final int FULL_BRIGHT = 15728880;

    // the doll in the hand
    private static float showProgress;
    private static float prevShowProgress;
    private static float animTime;
    private static float prevAnimTime;
    private static float hideAnimTime;
    private static float prevHideAnimTime;
    private static float settleTime;
    private static float prevSettleTime;
    private static boolean dollHeld;
    private static boolean hiding;
    private static int lastEntityId = -1;

    // inertia of the doll
    private static float lastYaw;
    private static float lastPitch;
    private static float swayYaw, prevSwayYaw, swayPitch, prevSwayPitch, swayY, prevSwayY;
    private static float swayYawVel, swayPitchVel, swayYVel;
    /** Step bob: the phase runs with the walking speed, the strength fades in and out (both interpolated per frame). */
    private static float walkBob, prevWalkBob, bobAmount, prevBobAmount;

    // activation
    private static int lockedSlot = -1;
    private static boolean speechTriggered;
    private static long lastActivateAttempt;
    private static int lastNonDollSlot = -1;
    private static float darknessAlpha;
    private static boolean darknessDrawn;
    private static float hudFade = 1.0F;
    private static float targetHudFade = 1.0F;
    private static long lastHudFadeNanos;
    private static boolean descending;
    private static int descendingTicks;
    private static CameraType savedCamera;
    private static boolean savedShoulder;
    private static boolean hudShifted;
    /** The end of the activation: the player blinks, and while the lids are shut the doll is gone and the HUD is back. */
    private static final int BLINK_TICKS_BEFORE_END = 4;
    /** The concussion comes in this long before the end (Explosion Overhaul fades it in over 40 ticks). */
    private static final int CONCUSSION_TICKS_BEFORE_END = 40;
    private static boolean snapAway;
    private static boolean concussed;

    private DollClient() {
    }

    public static DollModel model() {
        if (!MODEL.isLoaded()) {
            MODEL.load(new ResourceLocation(EFW.MOD_ID, "bedrock/doll.geo.json"), new ResourceLocation(EFW.MOD_ID, "bedrock/doll.animation.json"));
        }
        return MODEL;
    }

    public static ResourceLocation texture() {
        return TEXTURE;
    }

    // ---- state ---------------------------------------------------------------------------------------------------------

    public static boolean isLyingOrCrawling(Player player) {
        Pose pose = player.getPose();
        return player.isSleeping() || pose == Pose.SWIMMING || pose == Pose.FALL_FLYING || pose == Pose.SPIN_ATTACK || pose == Pose.DYING;
    }

    /** The doll is on the screen: held or still sliding away. */
    public static boolean isDollActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isDeadOrDying() || isLyingOrCrawling(mc.player)) {
            return false;
        }
        return dollHeld || showProgress > 0.0F;
    }

    /** How far the arms of the player are raised with the doll (smooth 0..1). */
    public static float holdProgress(Player player, float pt) {
        if (isLyingOrCrawling(player)) {
            return 0.0F;
        }
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) {
            float p = Mth.clamp(Mth.lerp(pt, prevShowProgress, showProgress), 0.0F, 1.0F);
            return p * p * (3.0F - 2.0F * p);
        }
        return DollItem.isHoldingDoll(player) ? 1.0F : 0.0F;
    }

    /** The activation is running: the replica is then shown high on the screen, away from the doll. */
    public static boolean activating() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && DollAbility.isActivating(mc.player);
    }

    /** The HUD is hidden (the doll ability sends it off the screen). */
    public static boolean hidesHud() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && (DollAbility.isActivating(mc.player) || hudFadeAlpha() <= 0.001F);
    }

    /** 1 = normal HUD, 0 = gone; advanced with the real time, so the slide is smooth at any frame rate. */
    public static float hudFadeAlpha() {
        long now = System.nanoTime();
        if (lastHudFadeNanos == 0L) {
            lastHudFadeNanos = now;
        }
        float dt = Math.min(0.1F, (now - lastHudFadeNanos) / 1.0e9F);
        lastHudFadeNanos = now;
        if (dt > 0.0F && hudFade != targetHudFade) {
            float step = (targetHudFade < hudFade ? 1.0F / 0.7F : 1.0F / 0.5F) * dt;
            hudFade = hudFade < targetHudFade ? Math.min(targetHudFade, hudFade + step) : Math.max(targetHudFade, hudFade - step);
        }
        return hudFade;
    }

    public static void reset() {
        dollHeld = false;
        hiding = false;
        showProgress = prevShowProgress = 0.0F;
        animTime = prevAnimTime = 0.0F;
        hideAnimTime = prevHideAnimTime = 0.0F;
        settleTime = prevSettleTime = 0.0F;
        swayYaw = prevSwayYaw = swayPitch = prevSwayPitch = swayY = prevSwayY = 0.0F;
        swayYawVel = swayPitchVel = swayYVel = 0.0F;
        walkBob = prevWalkBob = bobAmount = prevBobAmount = 0.0F;
        lockedSlot = -1;
        speechTriggered = false;
        darknessAlpha = 0.0F;
        hudFade = targetHudFade = 1.0F;
        descending = false;
        descendingTicks = 0;
        snapAway = false;
        if (concussed) {
            concussed = false;
            DollConcussion.stop();
        }
        DollAbility.clientActivationTicks = 0;
        DollAbility.clientBuffTicks = 0;
        DollAbility.clientLastActivationMs = 0L;
        restoreCamera(Minecraft.getInstance());
    }

    // ---- camera ---------------------------------------------------------------------------------------------------------

    private static void switchToFirstPerson(Minecraft mc) {
        if (savedCamera == null && mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            savedCamera = mc.options.getCameraType();
            savedShoulder = ShoulderCompat.isShoulderSurfing();
            ShoulderCompat.runImmediate(() -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        }
    }

    private static void restoreCamera(Minecraft mc) {
        if (savedCamera != null && mc != null) {
            CameraType type = savedCamera;
            savedCamera = null;
            if (savedShoulder) {
                // shoulder surfing is its own perspective over the third person camera: the camera type alone gave vanilla third person
                savedShoulder = false;
                ShoulderCompat.enterShoulderSurfing();
                return;
            }
            ShoulderCompat.runImmediate(() -> mc.options.setCameraType(type));
        }
    }

    // ---- activation (client copy) -----------------------------------------------------------------------------------------

    /** The doll is in the hand with the blood at the maximum: starts the local show at once and tells the server. */
    private static void beginActivation(LocalPlayer player, boolean tellServer) {
        Minecraft mc = Minecraft.getInstance();
        DollAbility.clientActivationTicks = DollAbility.activationTotalTicks();
        DollAbility.clientLastActivationMs = System.currentTimeMillis();
        DollAbility.clientBuffTicks = 0;
        lockedSlot = player.getInventory().selected;
        speechTriggered = false;
        descending = false;
        descendingTicks = 0;
        targetHudFade = 0.0F;
        // whatever was being said is cut off at once: the doll's "forgive me" comes alone
        SpeechClientManager.clearPersonal();
        com.voltyx.efw.heartbeat.HeartbeatManager.currentBPM = com.voltyx.efw.heartbeat.HeartbeatManager.MAX_BPM;
        switchToFirstPerson(mc);
        mc.getSoundManager().play(new SimpleSoundInstance(DollContent.DOLL_ACTIVATE.get().getLocation(), SoundSource.MASTER, 4.0F, 1.0F,
                RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
        if (tellServer) {
            EfwNetwork.CHANNEL.sendToServer(new EfwNetwork.DollActivate());
        }
    }

    /** Packet from the server: -1 = the activation started, 0 = the frenzy is over, more = ticks of the frenzy left. */
    public static void onSync(int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (ticks < 0) {
            if (DollAbility.clientActivationTicks <= 0) {
                beginActivation(mc.player, false);
            }
            return;
        }
        if (concussed && ticks > DollAbility.clientBuffTicks) {
            DollConcussion.extend(Math.round((ticks - DollAbility.clientBuffTicks) / 20.0F));
        }
        DollAbility.clientBuffTicks = ticks;
        if (ticks <= 0) {
            DollAbility.clientActivationTicks = 0;
        }
    }

    private static void putDollAway(LocalPlayer player) {
        int slot = DollAbility.chooseSlotAfterDoll(player, lastNonDollSlot);
        player.getInventory().selected = slot;
        lockedSlot = -1;
        SpeechClientManager.startPersonalFadeOut();
        restoreCamera(Minecraft.getInstance());
        if (player.connection != null) {
            player.connection.send(new ServerboundSetCarriedItemPacket(slot));
        }
    }

    private static void consumeHotbarKeys(Minecraft mc) {
        for (KeyMapping key : mc.options.keyHotbarSlots) {
            while (key.consumeClick()) {
                // swallowed
            }
        }
        for (KeyMapping key : new KeyMapping[] {mc.options.keySwapOffhand, mc.options.keyDrop, mc.options.keyInventory}) {
            while (key.consumeClick()) {
                // swallowed
            }
        }
    }

    // ---- tick -----------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            lastEntityId = -1;
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            if (DollAbility.isActivating(player)) {
                consumeHotbarKeys(mc);
                if (lockedSlot >= 0 && lockedSlot < 9 && player.getInventory().selected != lockedSlot) {
                    player.getInventory().selected = lockedSlot;
                }
            }
            return;
        }

        // a new player entity: the respawn
        if (lastEntityId != -1 && lastEntityId != player.getId()) {
            reset();
            if (DollItem.isDoll(player.getMainHandItem())) {
                putDollAway(player);
            }
        }
        lastEntityId = player.getId();
        if (player.isDeadOrDying()) {
            reset();
            return;
        }

        boolean holdingMain = DollItem.isDoll(player.getMainHandItem());
        if (!holdingMain && !DollAbility.isActivating(player)) {
            lastNonDollSlot = player.getInventory().selected;
        }
        if (holdingMain && DollAbility.isReady(player)) {
            long now = System.currentTimeMillis();
            if (now - lastActivateAttempt > 500L) {
                lastActivateAttempt = now;
                beginActivation(player, true);
            }
        }

        tickActivation(mc, player);
        tickHand(mc, player);
    }

    private static void tickActivation(Minecraft mc, LocalPlayer player) {
        if (DollAbility.clientActivationTicks > 0) {
            if (lockedSlot < 0 || lockedSlot > 8) {
                lockedSlot = player.getInventory().selected;
            }
            consumeHotbarKeys(mc);
            if (player.getInventory().selected != lockedSlot) {
                player.getInventory().selected = lockedSlot;
            }
            DollAbility.clientActivationTicks--;
            int left = DollAbility.clientActivationTicks;
            int elapsed = DollAbility.activationTotalTicks() - left;
            if (left == Math.min(CONCUSSION_TICKS_BEFORE_END, DollAbility.activationTotalTicks() - 1)) {
                // held for the whole frenzy, counted from the end of the activation
                concussed = true;
                DollConcussion.start(Math.round(DollAbility.buffTotalTicks(player) / 20.0F));
            }
            if (left == BLINK_TICKS_BEFORE_END) {
                com.voltyx.efw.eyes.client.EyesOpen.blink();
            }
            if (elapsed >= DollAbility.speechTick() && !speechTriggered) {
                speechTriggered = true;
                String text = I18n.get("speech.efw.doll.sorry");
                SpeechClientManager.showPersonal(text.equals("speech.efw.doll.sorry") ? "I'm sorry" : text);
            }
            if (DollAbility.clientActivationTicks <= 0) {
                lockedSlot = -1;
                DollAbility.clientBuffTicks = DollAbility.buffTotalTicks(player);
                SpeechClientManager.startPersonalFadeOut();
                putDollAway(player);
                // the lids are shut now: the doll and the dark are gone at once and the HUD is fully back when they open
                snapAway = true;
                darknessAlpha = 0.0F;
                hudFade = targetHudFade = 1.0F;
                descending = false;
                descendingTicks = 0;
                if (concussed) {
                    DollConcussion.ring();
                }
            }
        }
        if (DollAbility.clientBuffTicks > 0) {
            DollAbility.clientBuffTicks--;
        } else if (concussed && !DollAbility.isActivating(player)) {
            concussed = false; // ran out with the frenzy; Explosion Overhaul fades it out by itself
        }

        if (DollAbility.isActivating(player)) {
            darknessAlpha += (0.95F - darknessAlpha) * 0.12F;
            targetHudFade = 0.0F;
        } else {
            darknessAlpha += (0.0F - darknessAlpha) * 0.08F;
            if (darknessAlpha < 0.005F) {
                darknessAlpha = 0.0F;
            }
            if (descending) {
                // the HUD stays hidden while the doll is high and comes back as it is almost lowered
                descendingTicks++;
                targetHudFade = showProgress <= 0.35F || descendingTicks > 40 ? 1.0F : 0.0F;
                if (showProgress <= 0.0F && hudFade >= 0.98F) {
                    descending = false;
                    descendingTicks = 0;
                }
            } else {
                targetHudFade = 1.0F;
            }
        }
    }

    private static void tickHand(Minecraft mc, LocalPlayer player) {
        boolean holding = DollItem.isHoldingDoll(player) && !isLyingOrCrawling(player);
        prevShowProgress = showProgress;
        prevAnimTime = animTime;
        prevHideAnimTime = hideAnimTime;
        prevSettleTime = settleTime;
        DollModel m = model();
        float animSpeed = DollConfig.ANIM_SPEED.get().floatValue();

        if (holding) {
            if (!dollHeld) {
                mc.level.playLocalSound(player.getX(), player.getY(), player.getZ(), DollContent.DOLL_UP.get(), SoundSource.PLAYERS, 0.9F, 1.0F, false);
            }
            dollHeld = true;
            hiding = false;
            hideAnimTime = 0.0F;
            showProgress = Math.min(1.0F, showProgress + DollConfig.APPEAR_SPEED.get().floatValue());
            float len = m.getAnimationLength("animation");
            if (len <= 0.0F) {
                len = 0.625F;
            }
            animTime = Math.min(len, animTime + (1.0F / 20.0F) * animSpeed);
            if (animTime >= Math.max(0.0F, len - 0.33F)) {
                settleTime = Math.min(2.0F, settleTime + 1.0F / 20.0F);
            } else {
                settleTime = 0.0F;
            }
        } else {
            settleTime = 0.0F;
            if (dollHeld || showProgress > 0.0F) {
                if (dollHeld) {
                    mc.level.playLocalSound(player.getX(), player.getY(), player.getZ(), DollContent.DOLL_DOWN.get(), SoundSource.PLAYERS, 0.9F, 1.0F, false);
                    SpeechClientManager.startPersonalFadeOut();
                }
                dollHeld = false;
                hiding = true;
                float downLen = m.getAnimationLength("down");
                if (downLen <= 0.0F) {
                    downLen = 0.25F;
                }
                hideAnimTime = Math.min(downLen, hideAnimTime + (1.0F / 20.0F) * animSpeed);
            }
            showProgress = Math.max(0.0F, showProgress - DollConfig.HIDE_SPEED.get().floatValue());
            if (snapAway) {
                snapAway = false;
                showProgress = prevShowProgress = 0.0F;
            }
            if (showProgress <= 0.0F) {
                hiding = false;
                animTime = 0.0F;
                hideAnimTime = 0.0F;
            }
        }

        // the doll lags behind the mouse and the steps
        prevSwayYaw = swayYaw;
        prevSwayPitch = swayPitch;
        prevSwayY = swayY;
        float dYaw = Mth.wrapDegrees(player.getYRot() - lastYaw);
        float dPitch = player.getXRot() - lastPitch;
        lastYaw = player.getYRot();
        lastPitch = player.getXRot();
        dYaw = Mth.clamp(dYaw, -30.0F, 30.0F);
        dPitch = Mth.clamp(dPitch, -30.0F, 30.0F);
        double motY = player.getY() - player.yo;
        swayYawVel = (swayYawVel + (-dYaw * 0.35F - swayYaw) * 0.45F) * 0.65F;
        swayYaw += swayYawVel;
        swayPitchVel = (swayPitchVel + (-dPitch * 0.30F - swayPitch) * 0.45F) * 0.65F;
        swayPitch += swayPitchVel;
        swayYVel = (swayYVel + ((float) (-motY * 3.5F) - swayY) * 0.45F) * 0.65F;
        swayY += swayYVel;
        // a short step off the ground or a slowdown no longer snaps the doll back: the bob fades instead of resetting
        double speed = player.getDeltaMovement().horizontalDistance();
        boolean walking = player.onGround() && speed > 0.05;
        prevWalkBob = walkBob;
        prevBobAmount = bobAmount;
        if (walking) {
            walkBob += 0.35F * (float) Mth.clamp(speed / 0.13, 0.6, 1.4);
        }
        bobAmount = Mth.clamp(bobAmount + (walking ? 0.2F : -0.15F), 0.0F, 1.0F);
        if (bobAmount <= 0.0F) {
            walkBob = prevWalkBob = 0.0F;
        }
    }

    /** The mouse look of the first frame after the camera switch must not throw the doll. */
    public static void resetSway() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            lastYaw = mc.player.getYRot();
            lastPitch = mc.player.getXRot();
        }
        swayYaw = prevSwayYaw = swayPitch = prevSwayPitch = swayY = prevSwayY = 0.0F;
        swayYawVel = swayPitchVel = swayYVel = 0.0F;
    }

    // ---- input and screens ------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && DollAbility.isActivating(mc.player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getNewScreen() instanceof AbstractContainerScreen<?> && mc.player != null && DollAbility.isActivating(mc.player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (DollAbility.isActivating(mc.player) || isDollActive())) {
            event.setNewFovModifier(1.0F);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    // ---- HUD ---------------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onCrosshair(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id()) && Minecraft.getInstance().options.getCameraType().isFirstPerson()
                && isDollActive()) {
            event.setCanceled(true);
        }
    }

    /** The whole HUD slides below the screen while the doll is out. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGuiPre(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        float darkness = darknessAlpha;
        if (darkness > 0.001F && !darknessDrawn) {
            drawDarkness(event.getGuiGraphics().pose().last().pose(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), darkness);
        }
        darknessDrawn = false;
        float fade = hudFadeAlpha();
        if (fade < 0.999F) {
            event.getGuiGraphics().pose().pushPose();
            event.getGuiGraphics().pose().translate(0.0F, (1.0F - fade) * mc.getWindow().getGuiScaledHeight(), 0.0F);
            hudShifted = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onGuiPost(RenderGuiEvent.Post event) {
        if (hudShifted) {
            event.getGuiGraphics().pose().popPose();
            hudShifted = false;
        }
    }

    /** The screen goes dark: a vignette from the edges and a black veil. */
    private static void drawDarkness(Matrix4f matrix, int w, int h, float alpha) {
        float vignette = Math.min(1.0F, alpha * 1.15F);
        float blackout = Math.min(0.92F, alpha * 0.92F);
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ZERO,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderColor(vignette, vignette, vignette, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, VIGNETTE);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buf.vertex(matrix, 0, h, -90).uv(0, 1).endVertex();
        buf.vertex(matrix, w, h, -90).uv(1, 1).endVertex();
        buf.vertex(matrix, w, 0, -90).uv(1, 0).endVertex();
        buf.vertex(matrix, 0, 0, -90).uv(0, 0).endVertex();
        BufferUploader.drawWithShader(buf.end());

        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(matrix, 0, h, -90).color(0.0F, 0.0F, 0.0F, blackout).endVertex();
        buf.vertex(matrix, w, h, -90).color(0.0F, 0.0F, 0.0F, blackout).endVertex();
        buf.vertex(matrix, w, 0, -90).color(0.0F, 0.0F, 0.0F, blackout).endVertex();
        buf.vertex(matrix, 0, 0, -90).color(0.0F, 0.0F, 0.0F, blackout).endVertex();
        BufferUploader.drawWithShader(buf.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /** The same darkness inside the hand render (3D projection): drawn under the doll, which stays bright on top of it. */
    private static void drawDarknessInHand(Minecraft mc, float alpha) {
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        Matrix4f oldProjection = RenderSystem.getProjectionMatrix();
        VertexSorting oldSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, w, h, 0.0F, 1000.0F, 3000.0F), VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.translate(0.0F, 0.0F, -2000.0F);
        RenderSystem.applyModelViewMatrix();
        try {
            drawDarkness(new Matrix4f(), w, h, alpha);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
        }
        darknessDrawn = true;
    }

    // ---- the doll in the hand ----------------------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isDeadOrDying() || isLyingOrCrawling(player)) {
            return;
        }
        float progress = Mth.lerp(event.getPartialTick(), prevShowProgress, showProgress);
        boolean holds = DollItem.isHoldingDoll(player);
        if (!holds && progress <= 0.005F) {
            showProgress = prevShowProgress = 0.0F;
            return;
        }
        if (event.getHand() == InteractionHand.OFF_HAND) {
            if (progress > 0.005F || holds) {
                event.setCanceled(true);
            }
            return;
        }
        if (progress <= 0.005F) {
            return;
        }
        // the vanilla item is hidden; the doll is drawn by us, even while it sinks after the slot was switched
        if (holds || DollItem.isDoll(event.getItemStack())) {
            event.setCanceled(true);
        }
        if (darknessAlpha > 0.001F) {
            drawDarknessInHand(mc, darknessAlpha);
        }
        BloodClient.beginHand(player);
        try {
            renderDoll(mc, event, progress);
        } catch (RuntimeException e) {
            EFW.LOGGER.debug("[Doll] hand render failed: {}", e.toString());
            event.setCanceled(false);
        } finally {
            BloodClient.end();
        }
    }

    private static void renderDoll(Minecraft mc, RenderHandEvent event, float progress) {
        DollModel m = model();
        if (!m.isLoaded()) {
            return;
        }
        float pt = event.getPartialTick();
        if (hiding) {
            float animT = Mth.lerp(pt, prevHideAnimTime, hideAnimTime);
            m.applyAnimationBlended("animation", Mth.lerp(pt, prevAnimTime, animTime), "down", animT, Math.min(1.0F, animT / 0.12F));
        } else {
            m.applyAnimation("animation", Mth.lerp(pt, prevAnimTime, animTime));
        }

        float sYaw = Mth.lerp(pt, prevSwayYaw, swayYaw);
        float sPitch = Mth.lerp(pt, prevSwayPitch, swayPitch);
        float sY = Mth.lerp(pt, prevSwayY, swayY);
        float bob = (float) Math.sin(Mth.lerp(pt, prevWalkBob, walkBob)) * Mth.lerp(pt, prevBobAmount, bobAmount);
        float inert = DollConfig.INERTIA.get().floatValue();
        if (inert > 0.0F) {
            m.addBoneRotation("Head", rad((sPitch * 0.7F + bob * 1.5F) * inert), rad(sYaw * 0.6F * inert), rad(-sYaw * 0.3F * inert));
            m.addBoneRotation("RHand", rad((sPitch * 1.1F + sY * 15.0F + bob * 3.0F) * inert), rad(sYaw * 1.2F * inert),
                    rad((sYaw * 0.5F - sY * 10.0F) * inert));
            m.addBoneRotation("LHand", rad((sPitch * 1.1F + sY * 15.0F - bob * 3.0F) * inert), rad(sYaw * 1.2F * inert),
                    rad((sYaw * 0.5F + sY * 10.0F) * inert));
        }
        // the head nods as the doll settles at the end of the raising
        float settle = Mth.lerp(pt, prevSettleTime, settleTime);
        float settleDuration = 0.55F;
        if (settle > 0.0F && settle < settleDuration && !hiding) {
            float t = settle / settleDuration;
            float env = (1.0F - t) * (1.0F - t);
            m.addBoneRotation("Head", rad((float) Math.sin(t * Math.PI * 2.5) * env * 9.0F), 0.0F, 0.0F);
        }

        float c = Mth.clamp(progress, 0.0F, 1.0F);
        float smooth = c * c * (3.0F - 2.0F * c);
        float slideY = -(1.0F - smooth) * DollConfig.SLIDE_DISTANCE.get().floatValue();
        float scale = DollConfig.HAND_SCALE.get().floatValue();

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        // the shaking of the hands at a high pulse has already been applied to this pose by the heartbeat system
        pose.translate(DollConfig.HAND_X.get(), slideY + DollConfig.HAND_Y.get(), DollConfig.HAND_Z.get());
        pose.mulPose(Axis.YP.rotationDegrees(DollConfig.HAND_ROT_Y.get().floatValue()));
        pose.mulPose(Axis.XP.rotationDegrees(DollConfig.HAND_ROT_X.get().floatValue()));
        pose.mulPose(Axis.ZP.rotationDegrees(DollConfig.HAND_ROT_Z.get().floatValue()));
        pose.scale(scale, -scale, scale);
        int light = DollConfig.WORLD_LIGHTING.get() ? event.getPackedLight() : FULL_BRIGHT;
        VertexConsumer buf = event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        m.render(pose, buf, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }

    private static float rad(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
