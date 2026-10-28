package com.voltyx.efw.speech.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.LsoSurvival;
import com.voltyx.efw.speech.SpeechConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * The character's own complaints about its state, as personal replicas above the hotbar (heard only by the player, like the doll's):
 * cold and hot (LSO temperature), soaked (LSO wetness), thirsty (LSO hydration) and hungry. One complaint at a time: after any of them
 * there is a pause, and the same complaint comes back only after a longer one, so a bad moment does not become a list. In a critical
 * moment (see {@link #critical()}) and a little after it nobody complains about the weather or the stomach.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class SurvivalReplicas {
    private enum Need {
        COLD(p -> is(LsoSurvival.temperature(p), "COLD", "FROSTBITE")),
        HOT(p -> is(LsoSurvival.temperature(p), "HOT", "HEAT_STROKE")),
        WET(p -> LsoSurvival.wetness(p) >= SpeechConfig.SURVIVAL_WET.get()),
        THIRST(p -> {
            int h = LsoSurvival.hydration(p);
            return h >= 0 && h <= SpeechConfig.SURVIVAL_THIRST.get();
        }),
        HUNGER(p -> p.getFoodData().getFoodLevel() <= SpeechConfig.SURVIVAL_FOOD.get());

        final Predicate<LocalPlayer> test;

        Need(Predicate<LocalPlayer> test) {
            this.test = test;
        }

        String key() {
            return "speech.efw.survival." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final int CHECK_TICKS = 20;
    private static final Random RANDOM = new Random();
    private static final Map<Need, Long> LAST_SAID = new EnumMap<>(Need.class);
    private static final Map<String, Integer> LAST_LINE = new java.util.HashMap<>();
    private static long lastAny;
    private static int checkIn;
    /** Critical until then: the danger seen last, plus the calm-down time. */
    private static long criticalUntil;
    private static float lastHealth = -1.0F;
    /** After the danger the complaints wait this long. */
    private static final long CALM_MS = 8000L;
    private static final double THREAT_RANGE = 12.0, CLOSE_RANGE = 6.0;

    private SurvivalReplicas() {
    }

    /** Hungry enough for the replica (then the food bar does not come out by itself). */
    public static boolean hungry(LocalPlayer player) {
        return SpeechConfig.SURVIVAL_ENABLED.get() && Need.HUNGER.test.test(player);
    }

    public static boolean thirsty(LocalPlayer player) {
        return SpeechConfig.SURVIVAL_ENABLED.get() && Need.THIRST.test.test(player);
    }

    /** Cold, hot or soaked. */
    public static boolean uncomfortable(LocalPlayer player) {
        return SpeechConfig.SURVIVAL_ENABLED.get()
                && (Need.COLD.test.test(player) || Need.HOT.test.test(player) || Need.WET.test.test(player));
    }

    /**
     * A critical moment, no time for complaints: the doll's activation or frenzy, a hit taken, a hostile mob close by that is after
     * the player (attacking, or near and in sight); and a few seconds after.
     */
    public static boolean critical() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && com.voltyx.efw.doll.DollAbility.isActivating(player)) {
            return true; // at once, not with the next check
        }
        return System.currentTimeMillis() < criticalUntil;
    }

    private static void updateCritical(LocalPlayer player) {
        boolean now = com.voltyx.efw.doll.DollAbility.isActivating(player) || com.voltyx.efw.doll.DollAbility.isBuffActive(player);
        float hp = player.getHealth();
        if (lastHealth >= 0.0F && hp < lastHealth - 0.01F) {
            now = true;
        }
        lastHealth = hp;
        if (!now) {
            for (net.minecraft.world.entity.Mob mob : player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    player.getBoundingBox().inflate(THREAT_RANGE), m -> m instanceof net.minecraft.world.entity.monster.Enemy && m.isAlive())) {
                double dist = mob.distanceTo(player);
                if (dist <= THREAT_RANGE && (mob.isAggressive() || mob.getTarget() == player || dist <= CLOSE_RANGE)
                        && player.hasLineOfSight(mob)) {
                    now = true;
                    break;
                }
            }
        }
        if (now) {
            criticalUntil = System.currentTimeMillis() + CALM_MS;
        }
    }

    /** Other complaints (the sickness) share the pause: true when it is over, and then it starts again. */
    public static boolean claimTurn() {
        if (critical()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastAny < SpeechConfig.SURVIVAL_GAP.get() * 1000L) {
            return false;
        }
        lastAny = now;
        return true;
    }

    /** A complaint that could not wait (it is coming up) was said: the pause starts now. */
    public static void noteSaid() {
        lastAny = System.currentTimeMillis();
    }

    private static boolean is(String value, String a, String b) {
        return a.equals(value) || b.equals(value);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || --checkIn > 0) {
            return;
        }
        checkIn = CHECK_TICKS;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            LAST_SAID.clear();
            lastAny = 0;
            criticalUntil = 0;
            lastHealth = -1.0F;
            return;
        }
        if (player.isAlive()) {
            updateCritical(player);
        }
        if (mc.isPaused() || !SpeechConfig.SURVIVAL_ENABLED.get() || !player.isAlive() || player.isCreative() || player.isSpectator()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (critical() || now - lastAny < SpeechConfig.SURVIVAL_GAP.get() * 1000L) {
            return;
        }
        long repeat = SpeechConfig.SURVIVAL_REPEAT.get() * 1000L;
        // of the needs that are due, the one not heard for the longest time
        Need pick = null;
        long pickSaid = Long.MAX_VALUE;
        for (Need need : Need.values()) {
            long said = LAST_SAID.getOrDefault(need, 0L);
            if (now - said >= repeat && said < pickSaid && need.test.test(player)) {
                pick = need;
                pickSaid = said;
            }
        }
        if (pick != null) {
            String line = randomLine(pick.key());
            if (line != null) {
                SpeechClientManager.showPersonal(line);
                LAST_SAID.put(pick, now);
                lastAny = now;
            }
        }
    }

    /** One of the numbered lines KEY.1, KEY.2 ... of the current language, not the one said last time for it. */
    private static String randomLine(String key) {
        int count = 0;
        while (I18n.exists(key + "." + (count + 1))) {
            count++;
        }
        if (count == 0) {
            return null;
        }
        int last = LAST_LINE.getOrDefault(key, 0);
        int pick = 1 + RANDOM.nextInt(count);
        if (pick == last && count > 1) {
            pick = pick % count + 1;
        }
        LAST_LINE.put(key, pick);
        return I18n.get(key + "." + pick);
    }
}
