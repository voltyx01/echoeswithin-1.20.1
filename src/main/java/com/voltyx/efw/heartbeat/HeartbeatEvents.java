package com.voltyx.efw.heartbeat;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.voltyx.efw.EFW;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client events of the heartbeat system. */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class HeartbeatEvents {
    private HeartbeatEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) {
            return;
        }
        if (HeartbeatConfig.ENABLED.get()) {
            HeartbeatManager.update(mc);
        }
    }

    /** An explosion sound near the player: the closer, the harder the impulse. */
    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        Minecraft mc = Minecraft.getInstance();
        SoundInstance sound = event.getSound();
        if (mc.player == null || mc.level == null || sound == null || !HeartbeatConfig.ENABLED.get()) {
            return;
        }
        if (sound.getLocation().getNamespace().equals("gunfireoverhaul")) {
            onGunfireOverhaul(mc, sound);
            return;
        }
        String name = event.getName().toLowerCase();
        if (!(name.contains("explode") || name.contains("explosion") || name.contains("grenade"))) {
            return;
        }
        Vec3 pos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        float size = 5.0f;
        float f3 = size * 2.0f;
        double distance = mc.player.position().distanceTo(pos);
        if (distance < f3) {
            double d12 = distance / f3;
            double d14 = Explosion.getSeenPercent(pos, mc.player);
            double d10 = (1.0 - d12) * d14;
            float damage = (int) ((d10 * d10 + d10) / 2.0 * 7.0 * f3 + 1.0);
            if (damage > 0) {
                HeartbeatManager.explosionImpulse = 175.0f;
            }
        }
    }

    /**
     * Gunfire Overhaul plays shots and artillery as close_ / medium_ / far_ / superfar_ sounds by the distance of the shooter: the closer
     * the class and the heavier the weapon (artillery), the more the pulse rises.
     */
    private static void onGunfireOverhaul(Minecraft mc, SoundInstance sound) {
        String path = sound.getLocation().getPath();
        float stress;
        if (path.startsWith("close_")) {
            stress = 0.55f;
        } else if (path.startsWith("medium_")) {
            stress = 0.35f;
        } else if (path.startsWith("far_")) {
            stress = 0.18f;
        } else if (path.startsWith("superfar_")) {
            stress = 0.08f;
        } else {
            return;
        }
        if (path.contains("artillery")) {
            stress *= 1.6f;
        }
        HeartbeatManager.notifyGunfire(stress);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        HeartbeatManager.saveAdaptation();
        HeartbeatManager.reset();
    }

    /** Trail of the picture: after the world is drawn, before the HUD. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGuiPre(RenderGuiEvent.Pre event) {
        HeartbeatTrail.render(event.getGuiGraphics());
    }

    /** Shaking of the hands and of the weapon in them (applied before they are drawn). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            HeartbeatTremor.apply(event.getPoseStack());
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("heartbeat")
                .then(Commands.argument("bpm", IntegerArgumentType.integer(0, 200)).executes(c -> {
                    HeartbeatManager.currentBPM = IntegerArgumentType.getInteger(c, "bpm");
                    c.getSource().sendSuccess(() -> Component.literal("Heartbeat set to " + (int) HeartbeatManager.currentBPM + " BPM."), false);
                    return 1;
                })));
    }
}
