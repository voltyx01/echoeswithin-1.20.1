package com.voltyx.efw.coth.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.coth.Coth;
import com.voltyx.efw.coth.CothConfig;
import com.voltyx.efw.coth.CothServer;
import com.voltyx.efw.heartbeat.HeartbeatManager;
import com.voltyx.efw.heartbeat.HeartbeatTrail;
import com.voltyx.efw.speech.client.SpeechClientManager;
import com.voltyx.efw.speech.client.SurvivalReplicas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The sickness of the Call of the Hive as the player feels it, and the fits of vomiting of everyone around as they are seen.
 * <ul>
 * <li>complaints (personal replicas) by stage: I mild, II worse, III the hive gets into the head;</li>
 * <li>a fit coming: a replica, the view sways more and more;</li>
 * <li>a fit: the player doubles over (crouches) with the head pulled down and jerks with every heave; blood leaves the mouth in
 * heaves and puddles on the ground. Other players see the blood, the crouch and the bowed head (those are synced anyway).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class CothClient {
    private static final Random RANDOM = new Random();
    private static final int HEAVE_PERIOD = 22;
    private static final int HEAVE_LENGTH = 9;

    private static final class Fit {
        final int total;
        int age;

        Fit(int total) {
            this.total = Math.max(1, total);
        }

        /** Strength of the heave at this moment, 0..1 (the last ticks only dribble). */
        float heave(float partial) {
            float t = age + partial;
            if (t > total - 8) {
                return 0.0f;
            }
            float p = t % HEAVE_PERIOD;
            if (p > HEAVE_LENGTH) {
                return 0.0f;
            }
            float weaker = 1.0f - 0.35f * Math.min(1.0f, t / total);
            return (float) Math.sin(p / HEAVE_LENGTH * Math.PI) * weaker;
        }

        boolean heaveStarts() {
            return age % HEAVE_PERIOD == 0 && age < total - 8;
        }
    }

    private static final Map<Integer, Fit> FITS = new HashMap<>();
    private static int warnTicks;
    private static int warnTotal;
    private static long nextReplica;
    /** The stage the complaints know of: a new (higher) stage is told once, soon after it comes. */
    private static int knownStage;
    private static long stageLineAt;
    private static long lastSaid;
    /** The last lines said, not repeated soon. */
    private static final java.util.ArrayDeque<String> RECENT = new java.util.ArrayDeque<>();
    private static int replicaCheck;
    private static long lastFrameNanos;

    private CothClient() {
    }

    /** From the server: warning (the own player), fit (anyone), stop. */
    public static void onVomit(int entityId, byte phase, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        boolean self = entityId == mc.player.getId();
        if (phase == CothServer.STOP) {
            FITS.remove(entityId);
            if (self) {
                warnTicks = 0;
            }
        } else if (phase == CothServer.WARN && self) {
            warnTicks = warnTotal = Math.max(1, ticks);
            // not every time: the swaying view and the gulp already tell it; never right after another complaint
            long now = System.currentTimeMillis();
            if (RANDOM.nextFloat() < CothConfig.WARN_REPLICA.get() && now - lastSaid > 60000L) {
                say(randomLine("speech.efw.coth.warn"));
            }
            mc.player.playSound(SoundEvents.GENERIC_DRINK, 0.35f, 0.55f);
        } else if (phase == CothServer.FIT) {
            FITS.put(entityId, new Fit(ticks));
            if (self) {
                warnTicks = 0;
            }
        }
    }

    public static boolean selfInFit() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && FITS.containsKey(mc.player.getId());
    }

    // ------------------------------------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            FITS.clear();
            warnTicks = 0;
            nextReplica = 0;
            HeartbeatManager.sicknessFloor = 0;
            HeartbeatTrail.nausea = 0;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        if (warnTicks > 0) {
            warnTicks--;
        }
        for (Iterator<Map.Entry<Integer, Fit>> it = FITS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Fit> entry = it.next();
            Fit fit = entry.getValue();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || fit.age >= fit.total) {
                it.remove();
                continue;
            }
            vomit(level, living, fit);
            fit.age++;
        }
        if (fit(player) != null) {
            player.setSprinting(false);
        }
        body(player);
        if (--replicaCheck <= 0) {
            replicaCheck = 20;
            complain(player);
        }
    }

    private static Fit fit(LocalPlayer player) {
        return FITS.get(player.getId());
    }

    /**
     * The heart and the picture (the heartbeat system): a fever keeps the pulse up a bit, a fit coming drives it up and the picture
     * starts to swim, a fit puts it near the top (heard heartbeat, shaking hands, drifting aim) with the picture swimming hard.
     */
    private static void body(LocalPlayer player) {
        int stage = CothConfig.ENABLED.get() && player.isAlive() ? Coth.stage(player) : 0;
        float floor = stage >= 3 ? 100.0f : stage == 2 ? 88.0f : 0.0f;
        float nausea = 0.0f;
        if (warnTicks > 0) {
            float k = 1.0f - (float) warnTicks / warnTotal;
            floor = Math.max(floor, 110.0f + 45.0f * k);
            nausea = 0.55f * k;
        }
        Fit fit = fit(player);
        if (fit != null) {
            floor = 172.0f;
            nausea = 0.85f + 0.15f * fit.heave(0.0f);
        }
        HeartbeatManager.sicknessFloor = floor;
        HeartbeatTrail.nausea = nausea;
    }

    /** Blood out of the mouth: a gush with each heave, a dribble between them. */
    private static void vomit(ClientLevel level, LivingEntity entity, Fit fit) {
        if (fit.heaveStarts()) {
            level.playLocalSound(entity.getX(), entity.getEyeY(), entity.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS,
                    0.8f, 0.42f + RANDOM.nextFloat() * 0.12f, false);
            level.playLocalSound(entity.getX(), entity.getEyeY(), entity.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS,
                    0.6f, 0.5f + RANDOM.nextFloat() * 0.1f, false);
        }
        float heave = fit.heave(0.0f);
        // gore blood particles (VomitBlood) weigh more than flat drops did: about half as many of them
        int count = heave > 0.05f ? 2 + Math.round(heave * 4) : (RANDOM.nextFloat() < 0.2f ? 1 : 0);
        if (count == 0) {
            return;
        }
        Vec3 look = entity.getViewVector(1.0f);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        // the mouth: a bit in front of and below the eyes (the own one a bit further off the camera, not right on the lens)
        Minecraft mc = Minecraft.getInstance();
        boolean own = entity == mc.player && mc.options.getCameraType().isFirstPerson();
        Vec3 mouth = entity.getEyePosition().add(look.scale(own ? 0.32 : 0.22)).add(0, own ? -0.2 : -0.13, 0);
        float speed = heave > 0.05f ? 1.2f + heave * 1.8f : 0.25f;
        for (int i = 0; i < count; i++) {
            float spread = heave > 0.05f ? 0.45f : 0.15f;
            VomitBlood.spawn(entity, mouth.add((RANDOM.nextFloat() - 0.5f) * 0.05f, (RANDOM.nextFloat() - 0.5f) * 0.04f,
                            (RANDOM.nextFloat() - 0.5f) * 0.05f),
                    (float) (flat.x * speed * 0.7 + look.x * speed * 0.3) + (RANDOM.nextFloat() - 0.5f) * spread,
                    (float) (look.y * speed * 0.4) - 0.3f + (RANDOM.nextFloat() - 0.5f) * spread,
                    (float) (flat.z * speed * 0.7 + look.z * speed * 0.3) + (RANDOM.nextFloat() - 0.5f) * spread);
        }
    }

    /**
     * The complaints. Mostly when something changes: a stage that is new comes with a line a few seconds later. Apart from that only
     * rarely (the configured pause, sharing it with the other complaints: cold, hunger...), and no line comes again soon.
     */
    private static void complain(LocalPlayer player) {
        int stage = Coth.stage(player);
        long now = System.currentTimeMillis();
        if (stage == 0 || !CothConfig.ENABLED.get() || !CothConfig.REPLICAS.get() || !player.isAlive()) {
            nextReplica = 0;
            knownStage = 0;
            stageLineAt = 0;
            return;
        }
        if (stage > knownStage) {
            knownStage = stage;
            stageLineAt = now + 3000 + RANDOM.nextInt(4000);
        } else if (stage < knownStage) {
            knownStage = stage;
        }
        if (selfInFit() || warnTicks > 0) {
            return;
        }
        if (stageLineAt != 0 && now >= stageLineAt && !SurvivalReplicas.critical()) {
            stageLineAt = 0;
            say(line(stage));
            SurvivalReplicas.noteSaid();
            nextReplica = now + pause();
            return;
        }
        if (nextReplica == 0) {
            nextReplica = now + pause();
            return;
        }
        if (now < nextReplica || !SurvivalReplicas.claimTurn()) {
            return;
        }
        nextReplica = now + pause();
        say(line(stage));
    }

    private static long pause() {
        int min = CothConfig.REPLICA_MIN.get();
        int max = Math.max(min, CothConfig.REPLICA_MAX.get());
        return (min + RANDOM.nextInt(max - min + 1)) * 1000L;
    }

    private static String line(int stage) {
        String pool = switch (stage) {
            case 1 -> "mild";
            case 2 -> RANDOM.nextFloat() < 0.35f ? "mild" : "bad";
            default -> RANDOM.nextFloat() < 0.4f ? "bad" : "worst";
        };
        return randomLine("speech.efw.coth." + pool);
    }

    private static void say(String line) {
        if (line == null) {
            return;
        }
        SpeechClientManager.showPersonal(line);
        lastSaid = System.currentTimeMillis();
        SurvivalReplicas.noteSaid();
    }

    /** One of the numbered lines KEY.1, KEY.2 ... of the current language, not one of the last few said. */
    static String randomLine(String key) {
        List<String> lines = new ArrayList<>();
        for (int i = 1; I18n.exists(key + "." + i); i++) {
            String k = key + "." + i;
            if (!RECENT.contains(k)) {
                lines.add(k);
            }
        }
        if (lines.isEmpty()) {
            for (int i = 1; I18n.exists(key + "." + i); i++) {
                lines.add(key + "." + i);
            }
        }
        if (lines.isEmpty()) {
            return null;
        }
        String k = lines.get(RANDOM.nextInt(lines.size()));
        RECENT.addLast(k);
        while (RECENT.size() > 6) {
            RECENT.removeFirst();
        }
        return I18n.get(k);
    }

    // ------------------------------------------------------------------------------------------------ the own body

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onInput(MovementInputUpdateEvent e) {
        if (CothConfig.FORCE_CROUCH.get() && e.getEntity() instanceof LocalPlayer player && fit(player) != null) {
            e.getInput().shiftKeyDown = true;
            e.getInput().jumping = false;
        }
    }

    /** The head is pulled down during a fit, smoothly, a bit more with each heave; the mouse can still fight it. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) {
            return;
        }
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.0f : Math.min(0.1f, (now - lastFrameNanos) / 1.0E9f);
        lastFrameNanos = now;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        float pitch = CothConfig.HEAD_PITCH.get().floatValue();
        Fit fit = player == null ? null : fit(player);
        if (fit == null || pitch <= 0 || mc.isPaused() || dt <= 0) {
            return;
        }
        float target = Math.min(90.0f, pitch + fit.heave(e.renderTickTime) * 12.0f);
        float current = player.getXRot();
        if (current < target) {
            float step = (target - current) * (1.0f - (float) Math.exp(-dt * 5.0f));
            // Entity.turn takes mouse units (0.15 degrees each) and moves the previous rotation along: no jump between ticks
            player.turn(0.0, step / 0.15);
        }
    }

    /** The view sways while a fit is coming and jerks with every heave. */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || e.getCamera().getEntity() != player) {
            return;
        }
        float partial = (float) e.getPartialTick();
        float t = player.tickCount + partial;
        if (warnTicks > 0) {
            float k = 1.0f - (warnTicks - partial) / warnTotal;
            k = Math.max(0.0f, Math.min(1.0f, k));
            e.setRoll(e.getRoll() + (float) Math.sin(t * 0.12f) * 3.0f * k);
            e.setPitch(e.getPitch() + (float) Math.sin(t * 0.19f + 1.3f) * 1.4f * k);
            e.setYaw(e.getYaw() + (float) Math.sin(t * 0.07f + 0.4f) * 1.2f * k);
        }
        Fit fit = fit(player);
        if (fit != null) {
            float heave = fit.heave(partial);
            e.setPitch(e.getPitch() + heave * 5.0f);
            e.setRoll(e.getRoll() + (float) Math.sin(t * 0.9f) * heave * 1.5f);
        }
    }
}
