package com.voltyx.efw.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.TaczCompat;
import com.voltyx.efw.init.EfwContent;
import com.voltyx.efw.network.EfwNetwork;
import com.voltyx.efw.stamina.EfwConfig;
import com.voltyx.efw.stamina.PlayerData;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client side of the roll: the key, the immediate local movement, the roll timers of every player (the animation and the body
 * turn read them) and the animation start. Port of DashKeyHandler and the roll part of AnimationTickHandler.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class RollClient {
    public static final KeyMapping ROLL_KEY = new KeyMapping("key.efw.dash", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.efw");

    /** Ticks left of the roll per player; above 2 counts as rolling (the last ticks are the recovery). */
    static final Map<UUID, Integer> TICKS = new HashMap<>();
    /** World yaw the body is turned to while rolling. */
    static final Map<UUID, Float> DASH_YAW = new HashMap<>();

    private RollClient() {
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent e) {
            e.register(ROLL_KEY);
        }
    }

    public static boolean isRolling(Player player) {
        Integer t = TICKS.get(player.getUUID());
        return t != null && t > 2;
    }

    /** Starts the roll animation and body turn; ignored while a roll is already running. */
    public static void trigger(Player player, Vec3 dir) {
        if (isRolling(player)) {
            return;
        }
        TICKS.put(player.getUUID(), 7);
        float yaw;
        if (dir != null && dir.lengthSqr() > 0.001) {
            yaw = (float) Math.toDegrees(Mth.atan2(-dir.x, dir.z));
        } else {
            Vec3 m = player.getDeltaMovement();
            yaw = m.x * m.x + m.z * m.z > 0.001 ? (float) Math.toDegrees(Mth.atan2(-m.x, m.z)) : player.getYRot();
        }
        DASH_YAW.put(player.getUUID(), yaw);
        if (ModList.get().isLoaded("playeranimator") && player instanceof AbstractClientPlayer acp) {
            RollAnimation.play(acp);
        }
    }

    /** Ticks the roll's own movement lasts (Combat Roll's roll_duration): no jumping and no auto jump meanwhile. */
    private static final int MOVE_TICKS = 8;
    private static int moveTicks;

    /** The local player is in the movement of a roll (RollMovementMixin keeps them from jumping). */
    public static boolean isMoving() {
        return moveTicks > 0;
    }

    /**
     * The roll starts before the player's tick, like Combat Roll does it from the key handling: the push moves the player in this
     * very tick, not one tick later. Holding the key rolls as soon as the roll is available again.
     */
    @SubscribeEvent
    public static void onClientTickStart(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.START || mc.level == null || mc.player == null) {
            return;
        }
        while (ROLL_KEY.consumeClick()) {
            // the key is read as held below; the clicks only must not pile up
        }
        if (moveTicks > 0) {
            moveTicks--;
        }
        PlayerData data = PlayerData.get(mc.player);
        if (ROLL_KEY.isDown() && mc.screen == null && !mc.isPaused() && data != null) {
            tryRoll(mc, mc.player, data);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TICKS.clear();
            moveTicks = 0;
            return;
        }
        for (Player p : mc.level.players()) {
            Integer t = TICKS.get(p.getUUID());
            if (t != null) {
                if (t > -20) {
                    TICKS.put(p.getUUID(), t - 1);
                } else {
                    TICKS.remove(p.getUUID());
                }
            }
        }
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        PlayerData data = PlayerData.get(player);
        if (data != null && data.cooldown > 0) {
            data.cooldown--;
        }

        if (isRolling(player)) {
            player.swinging = false;
            player.swingTime = 0;
            player.attackAnim = 0;
            player.oAttackAnim = 0;
            if (mc.gameMode != null) {
                mc.gameMode.stopDestroyBlock();
            }
        }
    }

    /** The movement of Combat Roll's roll (net.combat_roll MinecraftClientMixin.tryRolling); stamina and cooldown stay ours. */
    private static void tryRoll(Minecraft mc, LocalPlayer player, PlayerData data) {
        if (!player.onGround() || data.cooldown > 0) {
            return;
        }
        if ((player.isInWater() || player.isInLava()) && !EfwConfig.ALLOW_DASH_IN_LIQUIDS.get()) {
            return;
        }
        if (player.isSwimming() || player.isVisuallyCrawling() || player.isShiftKeyDown() || player.getBbHeight() < 1.0F
                || player.isPassenger() || player.isFallFlying() || player.isUsingItem() || player.isBlocking()) {
            return;
        }
        // a melee swing being held is not interrupted (a gun's trigger is: a roll while firing is fine)
        if (mc.options.keyAttack.isDown() && !TaczCompat.isHoldingGun(player)) {
            return;
        }
        if (TaczCompat.isReloading(player) || data.stamina < com.voltyx.efw.stamina.StaminaEvents.rollCost(player)) {
            return;
        }
        // the movement input the player really walks with (after Shoulder Surfing and the like turned it), forward without input
        float forward = player.zza;
        float sideways = player.xxa;
        Vec3 dir = forward == 0.0F && sideways == 0.0F ? new Vec3(0, 0, 1) : new Vec3(sideways, 0, forward).normalize();
        dir = dir.yRot((float) Math.toRadians(-player.getYRot()));
        Vec3 velocity = dir.scale(0.475 * EfwConfig.DASH_SPEED.get()
                * com.voltyx.efw.sins.stat.SinStats.mult(player, com.voltyx.efw.sins.stat.Stat.ROLL_DISTANCE));
        if (player.isInWater()) {
            double depth = Math.min(player.getFluidHeight(FluidTags.WATER), 1.0);
            velocity = velocity.scale(Math.max(1.0 - depth * 3.0, 0.3));
        }
        if (player.isInLava()) {
            velocity = velocity.scale(0.3);
        }
        float slip = player.level().getBlockState(player.blockPosition().below()).getBlock().getFriction();
        float normal = Blocks.GRASS_BLOCK.getFriction();
        if (slip > normal) {
            double k = normal / slip;
            velocity = velocity.scale(k * k);
        }
        player.push(velocity.x, velocity.y, velocity.z);
        moveTicks = MOVE_TICKS;

        trigger(player, dir);
        // the sound comes at once and always with the roll the player sees, not only once the server took it
        player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), EfwContent.DASH_SOUND.get(), SoundSource.PLAYERS,
                EfwConfig.SOUND_VOLUME.get().floatValue(), EfwConfig.SOUND_PITCH.get().floatValue(), false);
        data.cooldown = com.voltyx.efw.stamina.StaminaEvents.cooldownTicks(player);
        EfwNetwork.CHANNEL.sendToServer(new EfwNetwork.DashRequest((float) dir.x, (float) dir.z));
    }
}
