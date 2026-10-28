package com.voltyx.efw.heartbeat;

import com.voltyx.efw.sins.stat.SinStats;
import com.voltyx.efw.sins.stat.Stat;
import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.TaczCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Heartbeat of the player (ported from mwccf's HeartbeatManager). The pulse follows what happens around the player: parasites in sight,
 * darkness, closed rooms, low health, sprinting, aiming and shooting, panic / fear / adrenaline effects and damage. It drives the body
 * heartbeat sounds, the drifting aim at the top of the scale, the ghost trail of the picture and the shaking of the hands.
 * Not ported: the bracelet display (item), the bloody necklace and the limb damage system, which do not exist here.
 */
public final class HeartbeatManager {
    public static final float BASE_BPM = 65.0f;
    public static final float MAX_BPM = 180.0f;
    /**
     * How much of what danger, wounds and shooting add to the pulse it really gets: they lift it a little, not to the top in a few
     * beats. The doll, explosions and the sickness set their own pulse and are not scaled.
     */
    private static final float RISE_SCALE = 0.45f;
    /** Part of the way to the pulse it is heading for covered each update: it climbs over several seconds, not at once. */
    private static final float RISE_STEP = 0.25f;

    /** Under the sedatives a rise of the pulse counts this part, and the pulse settles back to rest this many BPM a second. */
    private static final float SEDATED_SPIKE = 0.2f;
    private static final float SEDATED_SETTLE = 6.0f;

    public static float currentBPM = BASE_BPM;
    /** BPM added by an explosion; consumed on the next update. */
    public static float explosionImpulse;
    /** BPM added by a hit; consumed on the next update. */
    public static float damageImpulse;
    /** The pulse the sickness (Call of the Hive) keeps up at least: a fever, a fit coming, a fit. Set each tick from outside. */
    public static float sicknessFloor;
    /** 0..1: how much gunfire and artillery (Gunfire Overhaul) is heard around; fades in a few seconds. */
    private static float gunfireStress;
    public static long lastBeatTime;

    private static long lastUpdateTime;
    private static long lastLubTime;
    private static boolean beatHalfStepDone = true;

    private static final float UPDATE_INTERVAL_BASE = 0.5f;
    private static float nextUpdateIn;
    private static final java.util.Random RNG = new java.util.Random();

    // adaptation: the more the player sees a tier, the less it raises the pulse
    private static float shortAdaptLow;
    private static float shortAdaptMid;
    private static float shortAdaptHigh;
    private static float longAdaptLow;
    private static float longAdaptMid;
    private static float longAdaptHigh;
    private static long lastSeenLowMs;
    private static long lastSeenMidMs;
    private static long lastSeenHighMs;
    private static boolean loadedAdaptation;
    private static long lastSaveTime;

    private static int lastAmmo = -1;
    private static int lastGunSlot = -1;
    private static float shootingHeat;
    private static int decayDelayUpdates;
    private static float lastHealth = -1.0f;
    private static boolean wasDead;

    private static final Set<String> SEEN_MOBS = new HashSet<>();

    private static final Set<String> SRP_IGNORE = new HashSet<>(List.of("buglin", "lodo"));

    private static final Set<String> SRP_LOW = new HashSet<>(Arrays.asList(
            "heed", "mes", "quac", "leer", "done", "inhoos", "inhoom",
            "inf_cow_head", "inf_horse_head", "inf_pig_head", "inf_sheep_head", "inf_wolf_head", "inf_villager_head", "inf_enderman_head",
            "dorpa", "carrier_worm", "airscrew",
            "fer_bear", "fer_cow", "fer_horse", "fer_pig", "fer_sheep", "fer_wolf", "fer_enderman",
            "crux_incomplete", "dredge"));

    private static final Set<String> SRP_MID = new HashSet<>(Arrays.asList(
            "crux", "thrall",
            "ada_longarms", "ada_manducater", "ada_reeker", "ada_yelloweye", "ada_summoner", "ada_bolster", "ada_tozoon", "ada_arachnida",
            "ada_devourer", "ada_vermin", "ada_viscera", "ada_burrower",
            "inf_bear", "inf_cow", "inf_horse", "inf_pig", "inf_sheep", "inf_wolf", "inf_enderman", "inf_human", "inf_villager", "inf_squid",
            "inf_player", "inf_dragon_e",
            "spe_bear", "spe_cow", "spe_enderman", "spe_human", "spe_sheep", "spe_villager",
            "inf_human_head", "inf_player_head", "inf_dragon_e_head",
            "dodt", "leemb", "nak", "rof", "tonro", "unvo",
            "fer_human", "fer_villager",
            "ata", "buthol", "gothol", "kol", "lodo", "mudo", "nuuh", "rathol", "viin"));

    private static final Set<String> SRP_HIGH = new HashSet<>(Arrays.asList(
            "pri_longarms", "pri_manducater", "pri_reeker", "pri_yelloweye", "pri_summoner", "pri_bolster", "pri_tozoon", "pri_arachnida",
            "pri_devourer", "pri_vermin", "pri_viscera", "pri_burrower",
            "overseer", "vigilante", "warden", "bomber_light", "marauder", "monarch", "grunt", "bomber_heavy", "wraith", "bogle", "haunter",
            "carrier_colony", "succor", "seeker", "architect",
            "anc_dreadnaut", "anc_overlord", "anc_pod", "anc_dreadnaut_ten",
            "dod", "dodsii", "dodsiii", "dodsiv", "leem", "leemsii", "leemsiii", "leemsiv", "venkrol", "venkrolsii", "venkrolsiii", "venkrolsiv",
            "kirin", "draconite"));

    private HeartbeatManager() {
    }

    // ---- public API -------------------------------------------------------------------------------------------------

    public static void notifyExplosion(float damage) {
        float spike = 20.0f + Math.min(1.0f, damage / 15.0f) * 10.0f;
        explosionImpulse = Math.max(explosionImpulse, spike);
    }

    /** A shot or a shell heard from the given distance (Gunfire Overhaul sounds): the closer and the heavier, the more stress. */
    public static void notifyGunfire(float stress) {
        gunfireStress = Math.max(gunfireStress, Math.min(1.0f, stress));
    }

    public static void notifyDamage() {
        damageImpulse = Math.max(damageImpulse, 20.0f * RISE_SCALE);
    }

    public static void reset() {
        currentBPM = BASE_BPM;
        explosionImpulse = 0.0f;
        damageImpulse = 0.0f;
        shootingHeat = 0.0f;
        gunfireStress = 0.0f;
        decayDelayUpdates = 0;
        beatHalfStepDone = true;
        shortAdaptLow = 0.0f;
        shortAdaptMid = 0.0f;
        shortAdaptHigh = 0.0f;
        lastHealth = -1.0f;
        lastAmmo = -1;
        HeartbeatTrail.reset();
        HeartbeatTremor.reset();
    }

    /** 1.0 between the beats, up to 1.4 at the moment of a beat. */
    public static float getHeartScale() {
        long t = System.currentTimeMillis() - lastBeatTime;
        float p = Math.min(1.0f, t / 800.0f);
        return 1.0f + 0.4f * (float) Math.exp(-p * 6.0f);
    }

    // ---- update (each client tick) ----------------------------------------------------------------------------------

    public static void update(Minecraft mc) {
        Player player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastUpdateTime == 0L) {
            lastUpdateTime = now;
            nextUpdateIn = UPDATE_INTERVAL_BASE;
        }
        float dt = Math.min((now - lastUpdateTime) / 1000.0f, 0.1f);
        lastUpdateTime = now;

        if (!loadedAdaptation) {
            loadAdaptation();
            loadedAdaptation = true;
        }
        if (now - lastSaveTime > 60000L) {
            saveAdaptation();
            lastSaveTime = now;
        }

        if (player.isDeadOrDying()) {
            wasDead = true;
            reset();
            currentBPM = 0.0f;
            return;
        }
        if (wasDead) {
            wasDead = false;
            reset();
        }

        // a hit: the health fell since the last tick
        float health = player.getHealth();
        if (lastHealth >= 0.0f && health < lastHealth - 0.01f && HeartbeatConfig.ENABLED.get()) {
            notifyDamage();
        }
        lastHealth = health;

        // shooting heats the pulse up (the magazine of the gun in the hand got lighter)
        ItemStack held = player.getMainHandItem();
        int ammo = TaczCompat.currentAmmo(held);
        int slot = player.getInventory().selected;
        if (ammo < 0 || slot != lastGunSlot) {
            lastGunSlot = slot;
            lastAmmo = ammo;
        } else {
            if (ammo < lastAmmo) {
                shootingHeat = Math.min(8.0f, shootingHeat + 2.0f);
            }
            lastAmmo = ammo;
        }
        if (shootingHeat > 0.0f) {
            shootingHeat = Math.max(0.0f, shootingHeat - 2.0f * dt);
        }
        if (gunfireStress > 0.0f) {
            gunfireStress = Math.max(0.0f, gunfireStress - 0.25f * dt);
        }
        boolean aiming = TaczCompat.isAiming(player);

        float beforeUpdate = currentBPM;
        if (damageImpulse > 0.0f) {
            currentBPM += damageImpulse * (float) SinStats.mult(player, Stat.PULSE_HURT);
            damageImpulse = 0.0f;
        }
        if (explosionImpulse > 0.0f) {
            currentBPM = Math.max(currentBPM, 175.0f);
            decayDelayUpdates = 6;
            explosionImpulse = 0.0f;
        }

        nextUpdateIn -= dt;
        if (nextUpdateIn <= 0.0f) {
            nextUpdateIn = UPDATE_INTERVAL_BASE * (0.7f + RNG.nextFloat() * 0.6f);
            recalcBPM(mc, player, aiming);
        }

        // the sedatives: what lifted the pulse this update counts a fifth, and it settles back to rest by itself. The doll is not covered
        boolean dollOn = com.voltyx.efw.doll.DollAbility.isBuffActive(player) || com.voltyx.efw.doll.DollAbility.isActivating(player);
        if (!dollOn && player.hasEffect(com.voltyx.efw.init.EfwContent.SEDATED.get())) {
            float rest = BASE_BPM + (float) SinStats.value(player, Stat.PULSE_REST);
            if (currentBPM > beforeUpdate) {
                currentBPM = beforeUpdate + (currentBPM - beforeUpdate) * SEDATED_SPIKE;
            }
            if (currentBPM > rest) {
                currentBPM = Math.max(rest, currentBPM - SEDATED_SETTLE * dt);
            }
        }
        // the doll of Saya (activation and frenzy) keeps the pulse at the top
        if (com.voltyx.efw.doll.DollAbility.isBuffActive(player) || com.voltyx.efw.doll.DollAbility.isActivating(player)) {
            currentBPM = Math.max(currentBPM, 178.0f);
        }
        // the sickness pulls the pulse up quickly (not in one jump) and it stays a while after
        if (sicknessFloor > currentBPM) {
            currentBPM = Math.min(sicknessFloor, currentBPM + 60.0f * dt);
            decayDelayUpdates = 6;
        }
        if (currentBPM > MAX_BPM) {
            currentBPM = MAX_BPM;
        }
        if (currentBPM < 58.0f) {
            currentBPM = 58.0f;
        }

        beat(mc, now);
        reportPulse(player);

        // the aim drifts at the top of the scale
        if (HeartbeatConfig.AIM_DRIFT.get() && currentBPM >= 160.0f && !mc.isPaused() && mc.screen == null && mc.isWindowActive()) {
            float amp = (currentBPM - 160.0f) / 20.0f;
            float t = player.tickCount * 0.05f;
            float yawDrift = (float) Math.sin(t * 1.1f + 123.4f) * amp * 0.25f;
            float pitchDrift = (float) Math.cos(t * 0.8f + 567.8f) * amp * 0.25f;
            player.setYRot(player.getYRot() + yawDrift);
            player.setXRot(Math.max(-90.0f, Math.min(90.0f, player.getXRot() + pitchDrift)));
        }
    }

    /** The two thumps of a beat ("tup-tup"): only audible above the threshold. */
    private static void beat(Minecraft mc, long now) {
        long beatInterval = (long) (60000L / currentBPM);
        long lubDelay = (long) Math.max(120L, Math.min(260L, beatInterval * 0.35f));
        float threshold = HeartbeatConfig.BODY_THRESHOLD.get();
        float volume = HeartbeatConfig.BODY_VOLUME.get().floatValue();

        if (now - lastBeatTime >= beatInterval) {
            lastBeatTime = now;
            lastLubTime = now;
            beatHalfStepDone = false;
            if (HeartbeatConfig.ENABLED.get() && currentBPM >= threshold && volume > 0.0f) {
                play("heartbeat.out", bodyVolume(threshold, volume));
            }
        }
        if (!beatHalfStepDone && now - lastLubTime >= lubDelay) {
            beatHalfStepDone = true;
            if (HeartbeatConfig.ENABLED.get() && currentBPM >= threshold && volume > 0.0f) {
                play("heartbeat.in", bodyVolume(threshold, volume) * 0.85f);
            }
        }
    }

    private static float bodyVolume(float threshold, float volume) {
        float range = Math.max(10.0f, 175.0f - threshold);
        float progress = Math.min(1.0f, (currentBPM - threshold) / range);
        return (0.30f + 0.70f * progress) * volume;
    }

    /**
     * The sound sits at the head of the player instead of being "relative to the listener": mods that process sounds by their world
     * position (Sound Physics Remastered) then find it inside the head, with nothing between, and do not muffle it as if it came from
     * the world origin.
     */
    private static void play(String name, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        mc.getSoundManager().play(new SimpleSoundInstance(new ResourceLocation(EFW.MOD_ID, name), SoundSource.PLAYERS,
                volume, 1.0f, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, eye.x, eye.y, eye.z, false));
    }

    // ---- the pulse itself -------------------------------------------------------------------------------------------

    /** The server checks pulse conditions of SEVEN: it gets the pulse about once a second when it changed. */
    private static void reportPulse(Player player) {
        if (player.tickCount % 20 == 0 && Math.abs(currentBPM - lastReportedBPM) >= 2.0f) {
            lastReportedBPM = currentBPM;
            com.voltyx.efw.sins.SinNetwork.sendToServer(new com.voltyx.efw.sins.SinNetwork.PulseReport(currentBPM));
        }
    }

    private static float lastReportedBPM = -100.0f;

    private static void recalcBPM(Minecraft mc, Player player, boolean aiming) {
        float interval = UPDATE_INTERVAL_BASE;
        float growthRate = 0.0f;
        // SEVEN: the pulse at rest, how much the parasites frighten, how fast it calms down
        float rest = BASE_BPM + (float) SinStats.value(player, Stat.PULSE_REST);
        float fear = (float) SinStats.mult(player, Stat.PULSE_FEAR);
        float baseCeiling = rest;
        float activeFloor = rest;
        boolean isAiming = false;

        if (aiming) {
            isAiming = true;
            activeFloor = 60.0f;
            if (currentBPM < 90.0f) {
                growthRate -= 2.0f;
            } else {
                growthRate += 1.0f;
                baseCeiling += 10.0f;
            }
        }
        if (player.isSprinting()) {
            growthRate += 2.0f;
            baseCeiling += 15.0f;
        }
        if (mc.level.getMaxLocalRawBrightness(player.blockPosition()) < 4) {
            growthRate += 2.0f;
            baseCeiling += 15.0f;
        }
        if (isEnclosed(player)) {
            growthRate += 1.0f;
            baseCeiling += 13.0f;
        }
        float hp = player.getHealth() / player.getMaxHealth();
        if (hp < 0.25f) {
            growthRate += 11.0f;
            baseCeiling += 65.0f;
        } else if (hp < 0.50f) {
            growthRate += 3.0f;
            baseCeiling += 35.0f;
        }

        boolean hasMorphine = false;
        for (MobEffectInstance eff : player.getActiveEffects()) {
            ResourceLocation key = ForgeRegistries.MOB_EFFECTS.getKey(eff.getEffect());
            if (key == null) {
                continue;
            }
            String n = key.toString().toLowerCase();
            if (n.contains("panic")) {
                growthRate += 12.0f;
                baseCeiling += 85.0f;
            } else if (n.contains("fear")) {
                growthRate += 6.0f;
                baseCeiling += 55.0f;
            } else if (n.contains("adrenaline")) {
                growthRate += 10.0f;
                baseCeiling += 45.0f;
            } else if (n.contains("energy_boost")) {
                growthRate += 4.0f;
                baseCeiling += 25.0f;
            } else if (n.contains("morphine") || n.contains("painkiller")) {
                hasMorphine = true;
            }
        }

        // broken limbs (Legendary Survival Overhaul), unless a painkiller is in the blood
        if (!hasMorphine) {
            int broken = LsoBodyCompat.brokenParts(player);
            if (broken > 0) {
                growthRate += Math.min(8.0f, broken * 2.5f);
                baseCeiling += Math.min(45.0f, broken * 15.0f);
            }
        }
        // distant gunfire and artillery (Gunfire Overhaul)
        if (gunfireStress > 0.02f) {
            growthRate += gunfireStress * 6.0f;
            baseCeiling += gunfireStress * 45.0f;
        }

        MobStats mob = getMobStats(player);
        if (mob.maxParasiteFactor > 0) {
            growthRate += mob.maxParasiteFactor * fear;
            baseCeiling += mob.maxParasiteFactor * 2.5f * fear;
        }
        if (mob.hasAggro) {
            growthRate += 8.0f * fear;
            baseCeiling += 50.0f * fear;
        } else if (mob.hasVisible && mob.maxParasiteFactor == 0) {
            growthRate += 4.0f * fear;
            baseCeiling += 30.0f * fear;
        }
        if (mob.newSrpMobSeen) {
            currentBPM += 30.0f * RISE_SCALE * fear;
        }

        // shooting adds up to 20 BPM over the ceiling of the rest, never more than 160 by itself
        float shootingCeiling = Math.min(160.0f, baseCeiling + 20.0f);
        float activeCeiling = baseCeiling;
        if (shootingHeat > 0.0f) {
            growthRate += shootingHeat * 2.0f;
            activeCeiling = Math.max(activeCeiling, Math.min(shootingCeiling, baseCeiling + shootingHeat * 2.5f));
        }
        // what all of it adds over the rest counts only in part
        activeCeiling = rest + Math.max(0.0f, activeCeiling - rest) * RISE_SCALE;
        if (hasMorphine) {
            activeCeiling = Math.max(rest, activeCeiling - 40.0f);
            growthRate = Math.min(0.0f, growthRate - 4.0f);
        }

        if (currentBPM < activeCeiling) {
            float step = Math.max(2.0f, (activeCeiling - currentBPM) * RISE_STEP);
            currentBPM = Math.min(activeCeiling, currentBPM + step);
        } else if (hasMorphine && currentBPM > activeCeiling) {
            float step = Math.max(6.0f, (currentBPM - activeCeiling) * 0.5f);
            currentBPM = Math.max(Math.max(rest, activeCeiling), currentBPM - step);
        } else if (growthRate < 0.0f && currentBPM > activeFloor) {
            currentBPM = Math.max(activeFloor, currentBPM + growthRate * interval);
        }

        boolean rising = currentBPM < activeCeiling || mob.hasAggro || mob.hasVisible || shootingHeat > 0.0f || gunfireStress > 0.05f;
        if (rising) {
            decayDelayUpdates = 6;
        } else if (decayDelayUpdates > 0) {
            decayDelayUpdates--;
        } else {
            float decayStep = 3.0f * interval * (float) SinStats.mult(player, Stat.PULSE_DECAY);
            float targetDecay = Math.max(rest, activeCeiling);
            if (currentBPM > targetDecay) {
                currentBPM = Math.max(targetDecay, currentBPM - decayStep);
            } else if (currentBPM < rest && !isAiming) {
                currentBPM = Math.min(rest, currentBPM + decayStep);
            }
        }
    }

    private static boolean isEnclosed(Player p) {
        Vec3 eye = p.getEyePosition(1.0f);
        Vec3[] dirs = {new Vec3(0, 1, 0), new Vec3(0, -1, 0), new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1)};
        for (Vec3 d : dirs) {
            HitResult r = p.level().clip(new ClipContext(eye, eye.add(d.x * 5, d.y * 5, d.z * 5), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (r.getType() != HitResult.Type.BLOCK) {
                return false;
            }
        }
        return true;
    }

    private static final class MobStats {
        boolean hasVisible;
        boolean hasAggro;
        int maxParasiteFactor;
        boolean newSrpMobSeen;
    }

    /** The weak ones of the swarm do not frighten anybody: buglins, rupters, moving flesh and the walking heads. */
    private static boolean isHarmlessParasite(String name) {
        return SRP_IGNORE.contains(name) || name.equals("rupter") || name.equals("movingflesh") || name.contains("head");
    }

    /**
     * How dangerous a parasite is, from what it really has (the ids of 1.20 differ from the 1.12 lists): health plus four times the
     * attack damage. Under 25 (gnats, lice) it is nothing, up to 60 low, up to 160 mid, above that high (primitives, ancients, bosses).
     */
    private static int parasiteTier(LivingEntity e) {
        double health = e.getMaxHealth();
        var attack = e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        double threat = health + 4.0 * (attack == null ? 0.0 : attack.getValue());
        return threat < 25.0 ? 0 : threat < 60.0 ? 1 : threat < 160.0 ? 2 : 3;
    }

    private static MobStats getMobStats(Player player) {
        MobStats s = new MobStats();
        Vec3 look = player.getLookAngle();
        List<LivingEntity> near = player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(32.0));
        boolean seenLow = false;
        boolean seenMid = false;
        boolean seenHigh = false;

        for (LivingEntity e : near) {
            if (e == player || !(e instanceof Enemy)) {
                continue;
            }
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
            if (key == null || !key.getNamespace().equals("srparasites")) {
                continue;
            }
            String shortName = key.getPath();
            if (isHarmlessParasite(shortName)) {
                continue;
            }
            int tier = parasiteTier(e);
            if (tier == 0) {
                continue;
            }
            double dist = player.distanceTo(e);
            if (dist > 16.0 && tier != 3) {
                continue;
            }
            boolean aggro = e instanceof Mob m && m.getTarget() == player;
            if (!player.hasLineOfSight(e)) {
                if (aggro) {
                    s.hasAggro = true;
                }
                continue;
            }
            Vec3 toE = new Vec3(e.getX() - player.getX(), e.getY() + e.getEyeHeight() * 0.5 - player.getEyeY(), e.getZ() - player.getZ()).normalize();
            if (look.dot(toE) < 0.3) {
                if (aggro) {
                    s.hasAggro = true;
                }
                continue;
            }
            s.hasVisible = true;
            if (aggro) {
                s.hasAggro = true;
            }
            if (tier == 3) {
                seenHigh = true;
            } else if (tier == 2) {
                seenMid = true;
            } else {
                seenLow = true;
            }

            int steps = tier == 3 ? (int) (32.0 - Math.min(dist, 32.0)) : (int) (16.0 - Math.min(dist, 16.0));
            float redHigh = shortAdaptHigh * 0.3f + longAdaptHigh * 0.5f;
            float redMid = shortAdaptMid * 0.3f + longAdaptMid * 0.5f;
            float redLow = shortAdaptLow * 0.4f + longAdaptLow * 0.6f;
            int bonus;
            if (tier == 3) {
                bonus = (int) (steps * (19.0f / 32.0f) * (1.0f - redHigh));
            } else if (tier == 2) {
                bonus = (int) (Math.min(13.0f, steps) * (1.0f - redMid));
            } else {
                bonus = (int) (Math.min(8.0f, steps / 2.0f) * (1.0f - redLow));
            }
            if (bonus > s.maxParasiteFactor) {
                s.maxParasiteFactor = bonus;
            }
            if (SEEN_MOBS.add(key.toString())) {
                s.newSrpMobSeen = true;
            }
        }

        long nowMs = System.currentTimeMillis();
        if (seenHigh) {
            shortAdaptHigh = Math.min(1.0f, shortAdaptHigh + 1.0f / 120.0f);
            longAdaptHigh = Math.min(1.0f, longAdaptHigh + 1.0f / 2400.0f);
            lastSeenHighMs = nowMs;
        } else if (nowMs - lastSeenHighMs > 180000L) {
            shortAdaptHigh = 0.0f;
        }
        if (seenMid) {
            shortAdaptMid = Math.min(1.0f, shortAdaptMid + 1.0f / 120.0f);
            longAdaptMid = Math.min(1.0f, longAdaptMid + 1.0f / 2400.0f);
            lastSeenMidMs = nowMs;
        } else if (nowMs - lastSeenMidMs > 180000L) {
            shortAdaptMid = 0.0f;
        }
        if (seenLow) {
            shortAdaptLow = Math.min(1.0f, shortAdaptLow + 1.0f / 120.0f);
            longAdaptLow = Math.min(1.0f, longAdaptLow + 1.0f / 2400.0f);
            lastSeenLowMs = nowMs;
        } else if (nowMs - lastSeenLowMs > 180000L) {
            shortAdaptLow = 0.0f;
        }
        return s;
    }

    // ---- adaptation file --------------------------------------------------------------------------------------------

    private static File adaptationFile() {
        return new File(FMLPaths.CONFIGDIR.get().toFile(), "efw/heartbeat_adaptation.txt");
    }

    public static void saveAdaptation() {
        try {
            File f = adaptationFile();
            f.getParentFile().mkdirs();
            Files.writeString(f.toPath(), longAdaptLow + "\n" + longAdaptMid + "\n" + longAdaptHigh);
        } catch (Exception ignored) {
            // the adaptation is only a convenience
        }
    }

    public static void loadAdaptation() {
        try {
            File f = adaptationFile();
            if (f.exists()) {
                List<String> lines = Files.readAllLines(f.toPath());
                if (lines.size() >= 3) {
                    longAdaptLow = Float.parseFloat(lines.get(0).trim());
                    longAdaptMid = Float.parseFloat(lines.get(1).trim());
                    longAdaptHigh = Float.parseFloat(lines.get(2).trim());
                }
            }
        } catch (Exception ignored) {
            // start without adaptation
        }
    }
}
