package com.voltyx.efw.speech;

import com.voltyx.efw.config.ConfigRegistry;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Settings of the two speech systems (ported from mwccf_speech.cfg): the personal replicas (subtitles above the hotbar) and the public
 * ones (bubbles above the heads of the speakers in the world). The same file is read on the server (range, length, pitch of the voice)
 * and on the client (looks and timing).
 */
public final class SpeechConfig {
    public static final ForgeConfigSpec SPEC;

    // general
    private static final ForgeConfigSpec.IntValue MAX_MESSAGE_LENGTH;
    private static final ForgeConfigSpec.IntValue MAX_HISTORY;
    private static final ForgeConfigSpec.IntValue MAX_BUBBLES;
    private static final ForgeConfigSpec.IntValue MAX_LINES_PER_BUBBLE;

    // personal replicas
    private static final ForgeConfigSpec.IntValue P_HOLD_BASE;
    private static final ForgeConfigSpec.IntValue P_HOLD_PER_CHAR;
    private static final ForgeConfigSpec.IntValue P_HOLD_MAX;
    private static final ForgeConfigSpec.IntValue P_FADE;
    private static final ForgeConfigSpec.DoubleValue P_TYPING_SPEED;
    private static final ForgeConfigSpec.IntValue P_LETTER_DELAY;
    private static final ForgeConfigSpec.BooleanValue P_SOUND;
    private static final ForgeConfigSpec.DoubleValue P_SOUND_VOLUME;
    private static final ForgeConfigSpec.DoubleValue P_SCALE;
    private static final ForgeConfigSpec.DoubleValue P_X;
    private static final ForgeConfigSpec.DoubleValue P_Y;
    private static final ForgeConfigSpec.BooleanValue P_BRACKETS;
    private static final ForgeConfigSpec.BooleanValue P_GRADIENT;
    private static final ForgeConfigSpec.IntValue P_COLOR_R;
    private static final ForgeConfigSpec.IntValue P_COLOR_G;
    private static final ForgeConfigSpec.IntValue P_COLOR_B;

    // public replicas
    private static final ForgeConfigSpec.IntValue U_HOLD_BASE;
    private static final ForgeConfigSpec.IntValue U_HOLD_PER_CHAR;
    private static final ForgeConfigSpec.IntValue U_HOLD_MAX;
    private static final ForgeConfigSpec.IntValue U_FADE;
    private static final ForgeConfigSpec.DoubleValue U_TYPING_SPEED;
    private static final ForgeConfigSpec.IntValue U_LETTER_DELAY;
    private static final ForgeConfigSpec.BooleanValue U_SOUND;
    private static final ForgeConfigSpec.DoubleValue U_SOUND_VOLUME;
    private static final ForgeConfigSpec.DoubleValue U_SCALE;
    private static final ForgeConfigSpec.DoubleValue U_HEIGHT;
    private static final ForgeConfigSpec.DoubleValue U_X;
    private static final ForgeConfigSpec.DoubleValue U_Y;
    private static final ForgeConfigSpec.IntValue U_MAX_TEXT_WIDTH;
    private static final ForgeConfigSpec.IntValue U_MAX_HEIGHT;
    private static final ForgeConfigSpec.DoubleValue U_FULL_ALPHA_DISTANCE;
    private static final ForgeConfigSpec.DoubleValue U_HIDDEN_DISTANCE;
    private static final ForgeConfigSpec.DoubleValue U_PITCH_VARIATION;
    private static final ForgeConfigSpec.DoubleValue U_FEMALE_PITCH;

    // survival replicas
    public static final ForgeConfigSpec.BooleanValue SURVIVAL_ENABLED;
    public static final ForgeConfigSpec.IntValue SURVIVAL_GAP;
    public static final ForgeConfigSpec.IntValue SURVIVAL_REPEAT;
    public static final ForgeConfigSpec.IntValue SURVIVAL_FOOD;
    public static final ForgeConfigSpec.IntValue SURVIVAL_THIRST;
    public static final ForgeConfigSpec.IntValue SURVIVAL_WET;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        ConfigRegistry.section("Speech");
        b.push("general");
        MAX_MESSAGE_LENGTH = ConfigRegistry.integer(b, "maxMessageLength", 160, 16, 512, "Maximum length of a replica.");
        MAX_HISTORY = ConfigRegistry.integer(b, "maxHistory", 100, 10, 500, "Size of the local history of messages.");
        MAX_BUBBLES = ConfigRegistry.integer(b, "maxBubbles", 32, 4, 128, "Maximum number of bubbles shown in the world at once.");
        MAX_LINES_PER_BUBBLE = ConfigRegistry.integer(b, "maxLinesPerBubble", 8, 2, 24, "Maximum number of lines in one bubble.");
        b.pop();

        ConfigRegistry.section("Speech: personal replicas");
        b.push("personal");
        P_HOLD_BASE = ConfigRegistry.integer(b, "baseHoldMs", 2200, 200, 10000, "Base time a personal replica stays (ms).");
        P_HOLD_PER_CHAR = ConfigRegistry.integer(b, "extraHoldMsPerChar", 35, 0, 200, "Extra time per character (ms).");
        P_HOLD_MAX = ConfigRegistry.integer(b, "maxHoldMs", 8000, 500, 30000, "Longest time a personal replica stays (ms).");
        P_FADE = ConfigRegistry.integer(b, "fadeMs", 350, 50, 2000, "Duration of the fade out (ms).");
        P_TYPING_SPEED = ConfigRegistry.decimal(b, "typingSpeed", 1.0, 0.2, 5.0, "Typing speed multiplier (1 = standard, 2 = twice as fast).");
        P_LETTER_DELAY = ConfigRegistry.integer(b, "letterDelayMs", 85, 20, 300, "Base delay between typed letters (ms).");
        P_SOUND = ConfigRegistry.bool(b, "soundEnabled", true, "Sound of the typed letters.");
        P_SOUND_VOLUME = ConfigRegistry.decimal(b, "soundVolume", 1.0, 0.0, 4.0, "Volume of the typed letters (above 1 the letters get louder than one sound can be).");
        P_SCALE = ConfigRegistry.decimal(b, "scale", 1.0, 0.5, 3.0, "Size of the text above the hotbar.");
        P_X = ConfigRegistry.decimal(b, "xOffset", 0.0, -1000.0, 1000.0, "Horizontal shift from the center of the screen.");
        P_Y = ConfigRegistry.decimal(b, "yOffset", 0.0, -500.0, 500.0, "Vertical shift relative to the hotbar.");
        P_BRACKETS = ConfigRegistry.bool(b, "showBrackets", true, "Square brackets around the replica [ ... ].");
        P_GRADIENT = ConfigRegistry.bool(b, "bottomGradient", true, "Dark gradient from the bottom up to a third of the screen for special replicas.");
        P_COLOR_R = ConfigRegistry.integer(b, "textColorRed", 255, 0, 255, "Red part of the text color.");
        P_COLOR_G = ConfigRegistry.integer(b, "textColorGreen", 255, 0, 255, "Green part of the text color.");
        P_COLOR_B = ConfigRegistry.integer(b, "textColorBlue", 85, 0, 255, "Blue part of the text color.");
        b.pop();

        ConfigRegistry.section("Speech: public replicas");
        b.push("public");
        U_HOLD_BASE = ConfigRegistry.integer(b, "baseHoldMs", 1200, 200, 10000, "Base time a bubble stays (ms).");
        U_HOLD_PER_CHAR = ConfigRegistry.integer(b, "extraHoldMsPerChar", 28, 0, 200, "Extra time per character (ms).");
        U_HOLD_MAX = ConfigRegistry.integer(b, "maxHoldMs", 6000, 500, 30000, "Longest time a bubble stays (ms).");
        U_FADE = ConfigRegistry.integer(b, "fadeMs", 300, 50, 2000, "Duration of the fade out (ms).");
        U_TYPING_SPEED = ConfigRegistry.decimal(b, "typingSpeed", 1.0, 0.2, 5.0, "Typing speed multiplier in bubbles.");
        U_LETTER_DELAY = ConfigRegistry.integer(b, "letterDelayMs", 90, 20, 300, "Base delay between typed letters in bubbles (ms).");
        U_SOUND = ConfigRegistry.bool(b, "soundEnabled", true, "Sound of the typed letters in bubbles.");
        U_SOUND_VOLUME = ConfigRegistry.decimal(b, "soundVolume", 0.95, 0.0, 4.0, "Volume of the typed letters in bubbles (above 1 louder than one sound can be).");
        U_SCALE = ConfigRegistry.decimal(b, "bubbleScale", 0.025, 0.005, 0.10, "Size of the bubble in the world.");
        U_HEIGHT = ConfigRegistry.decimal(b, "bubbleHeight", 0.45, -2.0, 5.0, "Height of the bubble above the head.");
        U_X = ConfigRegistry.decimal(b, "bubbleXOffset", 0.0, -5.0, 5.0, "Horizontal shift of the bubble.");
        U_Y = ConfigRegistry.decimal(b, "bubbleYOffset", 0.0, -5.0, 5.0, "Vertical shift of the bubble.");
        U_MAX_TEXT_WIDTH = ConfigRegistry.integer(b, "maxTextWidth", 180, 50, 500, "Widest text inside a bubble (pixels).");
        U_MAX_HEIGHT = ConfigRegistry.integer(b, "maxBubbleHeight", 128, 30, 400, "Highest bubble (pixels).");
        U_FULL_ALPHA_DISTANCE = ConfigRegistry.decimal(b, "fullAlphaDistance", 16.0, 2.0, 64.0, "Distance (blocks) up to which a bubble is fully visible.");
        U_HIDDEN_DISTANCE = ConfigRegistry.decimal(b, "hiddenDistance", 20.0, 4.0, 128.0, "Distance (blocks) from which a bubble is hidden; also the range of the chat.");
        U_PITCH_VARIATION = ConfigRegistry.decimal(b, "pitchVariation", 0.08, 0.0, 0.5, "Random spread of the pitch of the typing sound per player.");
        U_FEMALE_PITCH = ConfigRegistry.decimal(b, "femalePitchOffset", 0.04, 0.0, 0.3, "Higher pitch for female characters (Wildfire Gender).");
        b.pop();

        ConfigRegistry.section("Speech: survival replicas");
        b.push("survival");
        SURVIVAL_ENABLED = ConfigRegistry.bool(b, "enabled", true,
                "The character says it out loud (only to the player, like the doll) when cold, hot, soaked, hungry or thirsty.");
        SURVIVAL_GAP = ConfigRegistry.integer(b, "cooldownSeconds", 90, 5, 1800,
                "Pause after any complaint (these and the sickness) before the next one (so a bad moment is not a list of complaints).");
        SURVIVAL_REPEAT = ConfigRegistry.integer(b, "sameCooldownSeconds", 600, 10, 3600,
                "Pause before the same complaint (cold again, hungry again) comes back; it is said once when it starts.");
        SURVIVAL_FOOD = ConfigRegistry.integer(b, "hungerAtOrBelow", 6, 0, 20, "Food level (of 20) at which the character is hungry.");
        SURVIVAL_THIRST = ConfigRegistry.integer(b, "thirstAtOrBelow", 6, 0, 20, "LSO hydration (of 20) at which the character is thirsty.");
        SURVIVAL_WET = ConfigRegistry.integer(b, "wetAtOrAbove", 160, 1, 400, "LSO wetness (of 400) at which the character is soaked.");
        b.pop();

        SPEC = b.build();
        ConfigRegistry.bind(b, SPEC);
    }

    private SpeechConfig() {
    }

    // general
    public static int maxMessageLength() { return MAX_MESSAGE_LENGTH.get(); }
    public static int maxHistory() { return MAX_HISTORY.get(); }
    public static int maxBubbles() { return MAX_BUBBLES.get(); }
    public static int maxLinesPerBubble() { return MAX_LINES_PER_BUBBLE.get(); }

    // personal
    public static int personalFadeMs() { return P_FADE.get(); }
    public static float personalTypingSpeed() { return P_TYPING_SPEED.get().floatValue(); }
    public static int personalLetterDelayMs() { return P_LETTER_DELAY.get(); }
    public static boolean personalSoundEnabled() { return P_SOUND.get(); }
    public static float personalSoundVolume() { return P_SOUND_VOLUME.get().floatValue(); }
    public static float personalScale() { return P_SCALE.get().floatValue(); }
    public static float personalXOffset() { return P_X.get().floatValue(); }
    public static float personalYOffset() { return P_Y.get().floatValue(); }
    public static boolean personalShowBrackets() { return P_BRACKETS.get(); }
    public static boolean personalBottomGradient() { return P_GRADIENT.get(); }
    public static int personalTextColorRgb() { return P_COLOR_R.get() << 16 | P_COLOR_G.get() << 8 | P_COLOR_B.get(); }

    public static int personalHoldDuration(String text) {
        if (text == null || text.isEmpty()) {
            return P_HOLD_BASE.get();
        }
        int length = text.codePointCount(0, text.length());
        return Math.min(P_HOLD_MAX.get(), P_HOLD_BASE.get() + length * P_HOLD_PER_CHAR.get());
    }

    // public
    public static int publicFadeMs() { return U_FADE.get(); }
    public static float publicTypingSpeed() { return U_TYPING_SPEED.get().floatValue(); }
    public static int publicLetterDelayMs() { return U_LETTER_DELAY.get(); }
    public static boolean publicSoundEnabled() { return U_SOUND.get(); }
    public static float publicSoundVolume() { return U_SOUND_VOLUME.get().floatValue(); }
    public static float publicBubbleScale() { return U_SCALE.get().floatValue(); }
    public static float publicBubbleHeight() { return U_HEIGHT.get().floatValue(); }
    public static float publicBubbleXOffset() { return U_X.get().floatValue(); }
    public static float publicBubbleYOffset() { return U_Y.get().floatValue(); }
    public static int publicMaxTextWidth() { return U_MAX_TEXT_WIDTH.get(); }
    public static int publicMaxBubbleHeight() { return U_MAX_HEIGHT.get(); }
    public static float publicFullAlphaDistance() { return U_FULL_ALPHA_DISTANCE.get().floatValue(); }
    public static float publicHiddenDistance() { return U_HIDDEN_DISTANCE.get().floatValue(); }
    public static float publicPitchVariation() { return U_PITCH_VARIATION.get().floatValue(); }
    public static float femalePitchOffset() { return U_FEMALE_PITCH.get().floatValue(); }

    public static int publicHoldDuration(String text) {
        if (text == null || text.isEmpty()) {
            return U_HOLD_BASE.get();
        }
        int length = text.codePointCount(0, text.length());
        return Math.min(U_HOLD_MAX.get(), U_HOLD_BASE.get() + length * U_HOLD_PER_CHAR.get());
    }
}
