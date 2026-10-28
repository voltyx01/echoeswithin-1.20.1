package com.voltyx.efw.speech.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.speech.SpeechConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client state of the speech systems (ported from mwccf SpeechClientManager): typed lines, bubbles in the world, history, personal replicas. */
public final class SpeechClientManager {
    private static final ResourceLocation LETTER_SOUND = new ResourceLocation(EFW.MOD_ID, "speech.letter");

    /** A line that is typed out letter by letter. */
    public static final class TypedLine {
        public final String text;
        public final float pitch;
        public final int entityId;
        public final boolean isPersonal;
        public final boolean isProcedural;
        public int revealedCodePoints;
        public long nextCharacterAt;
        private final boolean playSound;
        private final int[] codePoints;

        private TypedLine(String text, float pitch, int entityId, long startAt, boolean playSound, boolean isPersonal, boolean isProcedural) {
            this.text = text;
            this.pitch = pitch;
            this.entityId = entityId;
            this.playSound = playSound;
            this.isPersonal = isPersonal;
            this.isProcedural = isProcedural;
            this.codePoints = text.codePoints().toArray();
            this.nextCharacterAt = startAt;
        }

        public String getVisibleText() {
            return new String(codePoints, 0, Math.min(revealedCodePoints, codePoints.length));
        }

        public boolean isComplete() {
            return revealedCodePoints >= codePoints.length;
        }
    }

    /** All lines of one speaker shown in one bubble. */
    public static final class SpeechBubble {
        public final int entityId;
        public final String speakerName;
        public final float pitch;
        public final List<TypedLine> lines = new ArrayList<>();
        public long expiresAt;
        public long widthAnimationStart;
        public long widthAnimationEnd;
        public long heightAnimationStart;
        public long heightAnimationEnd;
        public float widthAnimationFrom;
        public float heightAnimationFrom = 12.0F;
        public float renderedWidth;
        public float renderedHeight = 12.0F;

        private SpeechBubble(int entityId, String speakerName, float pitch) {
            this.entityId = entityId;
            this.speakerName = speakerName;
            this.pitch = pitch;
        }

        public String getVisibleText() {
            StringBuilder text = new StringBuilder();
            for (TypedLine line : lines) {
                if (line.revealedCodePoints == 0) {
                    continue;
                }
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append('[').append(line.getVisibleText()).append(']');
            }
            return text.toString();
        }

        public String getFullText() {
            StringBuilder text = new StringBuilder();
            for (TypedLine line : lines) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append('[').append(line.text).append(']');
            }
            return text.toString();
        }

        public float getWidthProgress(long now) {
            if (widthAnimationEnd <= widthAnimationStart || now >= widthAnimationEnd) {
                return 1.0F;
            }
            return Math.max(0.0F, Math.min(1.0F, (now - widthAnimationStart) / (float) (widthAnimationEnd - widthAnimationStart)));
        }

        public boolean isTyping() {
            for (TypedLine line : lines) {
                if (!line.isComplete()) {
                    return true;
                }
            }
            return false;
        }
    }

    /** A line of the history: a message of a player, or (system != null) a system message / command output with its click events. */
    public record HistoryMessage(String speakerName, String text, long receivedAt, net.minecraft.network.chat.Component system) {
        public HistoryMessage(String speakerName, String text, long receivedAt) {
            this(speakerName, text, receivedAt, null);
        }
    }

    /** Output of commands and other system messages go to the history of the panel, where their links can be clicked. */
    public static void addSystemMessage(net.minecraft.network.chat.Component message) {
        HISTORY.addLast(new HistoryMessage("", message.getString(), System.currentTimeMillis(), message));
        while (HISTORY.size() > SpeechConfig.maxHistory()) {
            HISTORY.removeFirst();
        }
    }

    private static final Map<Integer, SpeechBubble> BUBBLES = new ConcurrentHashMap<>();
    private static final Deque<HistoryMessage> HISTORY = new ArrayDeque<>();
    private static final Deque<TypedLine> PERSONAL_QUEUE = new ArrayDeque<>();
    private static TypedLine activePersonal;
    private static long personalExpiresAt;
    private static boolean personalFadingOut;
    private static long personalFadeDurationMs = 350L;

    private SpeechClientManager() {
    }

    /** A chat message of a nearby player. */
    public static void receivePublic(int entityId, String speakerName, String rawText, float pitch) {
        String text = sanitize(rawText);
        if (text.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        boolean isSender = mc.player != null && mc.player.getId() == entityId;
        SpeechBubble bubble = BUBBLES.get(entityId);
        if (bubble == null) {
            if (BUBBLES.size() >= SpeechConfig.maxBubbles()) {
                Integer oldestId = null;
                long earliestExpiry = Long.MAX_VALUE;
                for (Map.Entry<Integer, SpeechBubble> entry : BUBBLES.entrySet()) {
                    if (entry.getValue().expiresAt < earliestExpiry) {
                        earliestExpiry = entry.getValue().expiresAt;
                        oldestId = entry.getKey();
                    }
                }
                if (oldestId != null) {
                    BUBBLES.remove(oldestId);
                }
            }
            bubble = new SpeechBubble(entityId, speakerName, pitch);
            bubble.widthAnimationStart = now;
            bubble.widthAnimationEnd = now + 180L;
            bubble.heightAnimationStart = bubble.widthAnimationEnd;
            bubble.heightAnimationEnd = bubble.heightAnimationStart + 120L;
            BUBBLES.put(entityId, bubble);
        } else {
            bubble.widthAnimationFrom = bubble.renderedWidth;
            bubble.widthAnimationStart = now;
            bubble.widthAnimationEnd = now + 180L;
            bubble.heightAnimationFrom = bubble.renderedHeight;
            bubble.heightAnimationStart = now;
            bubble.heightAnimationEnd = now + 120L;
        }
        long startAt = bubble.heightAnimationEnd;
        if (!bubble.lines.isEmpty()) {
            TypedLine last = bubble.lines.get(bubble.lines.size() - 1);
            startAt = Math.max(bubble.widthAnimationEnd, last.nextCharacterAt + estimatedRemainingMs(last));
        }
        bubble.lines.add(new TypedLine(text, pitch, entityId, startAt, !isSender, false, false));
        while (bubble.lines.size() > SpeechConfig.maxLinesPerBubble()) {
            bubble.lines.remove(0);
        }
        bubble.expiresAt = 0L;
        HISTORY.addLast(new HistoryMessage(speakerName, text, now));
        while (HISTORY.size() > SpeechConfig.maxHistory()) {
            HISTORY.removeFirst();
        }
        if (isSender) {
            PERSONAL_QUEUE.addLast(new TypedLine(text, pitch, entityId, now, true, true, false));
            while (PERSONAL_QUEUE.size() > 8) {
                PERSONAL_QUEUE.removeFirst();
            }
        }
    }

    /** A replica above the hotbar sent by the server (translation key). */
    public static void receivePersonal(String translationKey, String[] arguments, float pitch) {
        String text = I18n.get(translationKey, (Object[]) arguments);
        if (text == null || text.isEmpty() || text.equals(translationKey)) {
            return;
        }
        queuePersonal(sanitize(text).replace(".", ""), pitch);
    }

    /** A replica above the hotbar from the client itself. */
    public static void showPersonal(String text) {
        if (text != null && !text.isEmpty()) {
            queuePersonal(sanitize(text).replace(".", ""), 1.0F);
        }
    }

    private static void queuePersonal(String text, float pitch) {
        if (text.isEmpty()) {
            return;
        }
        if (activePersonal != null && text.equals(activePersonal.text)) {
            return;
        }
        for (TypedLine queued : PERSONAL_QUEUE) {
            if (text.equals(queued.text)) {
                return;
            }
        }
        Minecraft mc = Minecraft.getInstance();
        int playerId = mc.player == null ? -1 : mc.player.getId();
        PERSONAL_QUEUE.addLast(new TypedLine(text, pitch, playerId, System.currentTimeMillis(), true, true, true));
        while (PERSONAL_QUEUE.size() > 8) {
            PERSONAL_QUEUE.removeFirst();
        }
    }

    public static void update() {
        long now = System.currentTimeMillis();
        // own replicas wait while the eyes are closed or opening
        if (activePersonal == null && !PERSONAL_QUEUE.isEmpty() && !com.voltyx.efw.eyes.client.EyesOpen.eyesClosed()) {
            activePersonal = PERSONAL_QUEUE.removeFirst();
            personalFadingOut = false;
        }
        if (activePersonal != null) {
            if (!personalFadingOut) {
                boolean completedThisUpdate = advance(activePersonal, now);
                if (activePersonal.isComplete() && (completedThisUpdate || personalExpiresAt == 0L)) {
                    personalFadeDurationMs = Math.max(1L, SpeechConfig.personalFadeMs());
                    personalExpiresAt = now + SpeechConfig.personalHoldDuration(activePersonal.text) + personalFadeDurationMs;
                }
            }
            if (activePersonal.isComplete() && now >= personalExpiresAt) {
                activePersonal = null;
                personalExpiresAt = 0L;
                personalFadingOut = false;
            }
        }

        Iterator<Map.Entry<Integer, SpeechBubble>> it = BUBBLES.entrySet().iterator();
        while (it.hasNext()) {
            SpeechBubble bubble = it.next().getValue();
            for (TypedLine line : bubble.lines) {
                advance(line, now);
            }
            if (!bubble.isTyping()) {
                if (bubble.expiresAt == 0L) {
                    bubble.expiresAt = now + SpeechConfig.publicHoldDuration(bubble.getFullText()) + SpeechConfig.publicFadeMs();
                }
                if (now >= bubble.expiresAt) {
                    it.remove();
                }
            }
        }
    }

    private static boolean advance(TypedLine line, long now) {
        if (line.isComplete() || now < line.nextCharacterAt) {
            return false;
        }
        int codePoint = line.codePoints[line.revealedCodePoints++];
        line.nextCharacterAt = now + letterDelay(line);
        boolean soundEnabled = line.isPersonal ? SpeechConfig.personalSoundEnabled() : SpeechConfig.publicSoundEnabled();
        if (line.playSound && soundEnabled && !Character.isWhitespace(codePoint)) {
            playLetterSound(line);
        }
        return line.isComplete();
    }

    private static int letterDelay(TypedLine line) {
        float speed = line.isPersonal ? SpeechConfig.personalTypingSpeed() : SpeechConfig.publicTypingSpeed();
        int baseDelay = line.isPersonal ? SpeechConfig.personalLetterDelayMs() : SpeechConfig.publicLetterDelayMs();
        return Math.max(15, (int) (baseDelay / Math.max(0.5F, line.pitch) / Math.max(0.1F, speed)));
    }

    private static void playLetterSound(TypedLine line) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        // the sound engine caps one sound at full volume: above that the same letter (same variant) is played again on top of it
        float volume = line.isPersonal ? SpeechConfig.personalSoundVolume() * 1.5F : SpeechConfig.publicSoundVolume();
        long seed = RandomSource.create().nextLong();
        for (int copy = 0; copy < 6 && volume > 0.01F; copy++) {
            float part = Math.min(1.0F, volume);
            volume -= part;
            SoundInstance sound;
            if (line.isPersonal) {
                // own replicas above the hotbar: straight into the master channel, no distance falloff
                sound = new SimpleSoundInstance(LETTER_SOUND, SoundSource.MASTER, part, line.pitch,
                        RandomSource.create(seed), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
            } else {
                Entity source = line.entityId >= 0 ? mc.level.getEntity(line.entityId) : mc.player;
                if (source == null) {
                    source = mc.player;
                }
                sound = new SimpleSoundInstance(LETTER_SOUND, SoundSource.PLAYERS, part, line.pitch,
                        RandomSource.create(seed), false, 0, SoundInstance.Attenuation.LINEAR, source.getX(), source.getY() + source.getBbHeight() * 0.8, source.getZ(), false);
            }
            mc.getSoundManager().play(sound);
        }
    }

    private static int estimatedRemainingMs(TypedLine line) {
        int remaining = Math.max(0, line.codePoints.length - line.revealedCodePoints);
        return remaining * letterDelay(line);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String text = ChatFormatting.stripFormatting(value).replace('\r', ' ').replace('\n', ' ').replace('\u0000', ' ').trim();
        int length = text.codePointCount(0, text.length());
        if (length > SpeechConfig.maxMessageLength()) {
            text = text.substring(0, text.offsetByCodePoints(0, SpeechConfig.maxMessageLength()));
        }
        return text;
    }

    public static TypedLine getPersonalLine() {
        return activePersonal;
    }

    public static boolean isPersonalProcedural() {
        return activePersonal != null && activePersonal.isProcedural;
    }

    /** Shows the active personal replica in full and fades it out; the queue is dropped. */
    public static void startPersonalFadeOut() {
        if (activePersonal != null && !personalFadingOut) {
            personalFadingOut = true;
            activePersonal.revealedCodePoints = activePersonal.codePoints.length;
            personalFadeDurationMs = Math.max(400L, SpeechConfig.personalFadeMs());
            personalExpiresAt = System.currentTimeMillis() + personalFadeDurationMs;
        }
        PERSONAL_QUEUE.clear();
    }

    public static void clearPersonal() {
        activePersonal = null;
        personalExpiresAt = 0L;
        personalFadingOut = false;
        PERSONAL_QUEUE.clear();
    }

    public static float getPersonalAlpha() {
        if (activePersonal == null) {
            return 0.0F;
        }
        if (!activePersonal.isComplete()) {
            return 1.0F;
        }
        long fadeDur = Math.max(1L, personalFadeDurationMs);
        return Math.max(0.0F, Math.min(1.0F, (personalExpiresAt - System.currentTimeMillis()) / (float) fadeDur));
    }

    public static Collection<SpeechBubble> getBubbles() {
        return new ArrayList<>(BUBBLES.values());
    }

    public static List<HistoryMessage> getHistory() {
        return new ArrayList<>(HISTORY);
    }

    public static void clearWorldState() {
        BUBBLES.clear();
        HISTORY.clear();
        PERSONAL_QUEUE.clear();
        activePersonal = null;
        personalExpiresAt = 0L;
        personalFadingOut = false;
    }
}
