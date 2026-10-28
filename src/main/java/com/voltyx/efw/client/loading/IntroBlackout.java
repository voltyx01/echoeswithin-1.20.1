package com.voltyx.efw.client.loading;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * The hand-over from the loading screen to the main menu is messy (the loading overlay fading out over a menu whose GUI scale
 * just changed, a frame of black while the menu sets up). The first time the main menu opens in a session the whole screen
 * stays black instead, until the intro video of the menu starts playing; the intro begins on black, so the cut is not seen.
 * With no intro the black ends after {@link #MAX_MS}.
 * <p>
 * The menu's FancyMenu animations (the buttons flying in) wait for the intro too: they are held until it has played to its end, then
 * start right away (FancyAnimationClockMixin), so the buttons come out as the intro goes, however long it took to start.
 */
public final class IntroBlackout {
    private static final long MAX_MS = 4000L;
    /** An intro that plays longer than this is not waited for any more. */
    private static final long MAX_INTRO_MS = 30000L;

    private static long start = -1L;
    private static long playingAt = -1L;
    private static boolean done;
    private static boolean ended;
    private static boolean released;

    private IntroBlackout() {
    }

    /** The menu waits for its intro: the first main menu of the session is up and the intro has not played to its end yet. */
    public static boolean waitingForIntro() {
        if (ended) {
            return false;
        }
        long now = Util.getMillis();
        if (playingAt >= 0L) {
            if (now - playingAt > MAX_INTRO_MS) {
                introEnded();
                return false;
            }
            return true;
        }
        if (start >= 0L) {
            if (now - start > MAX_MS) {
                introEnded(); // no intro came
                return false;
            }
            return true;
        }
        return Minecraft.getInstance().screen instanceof TitleScreen;
    }

    /** The wait for the intro has just ended: true once, for the animation clock to catch up. */
    public static boolean consumeRelease() {
        if (ended && !released) {
            released = true;
            return true;
        }
        return false;
    }

    /** A FancyMenu video element is showing frames. */
    public static void introPlaying() {
        if (start >= 0L || Minecraft.getInstance().screen instanceof TitleScreen) {
            done = true;
            if (playingAt < 0L) {
                playingAt = Util.getMillis();
            }
        }
    }

    /** The intro has played to its end (or was given up): it dissolves now. */
    public static void introEnded() {
        if (start >= 0L || playingAt >= 0L || Minecraft.getInstance().screen instanceof TitleScreen) {
            done = true;
            ended = true;
        }
    }

    /** Drawn over the screen and the loading overlay, under the toasts. */
    public static void render(Minecraft mc) {
        if (done) {
            return;
        }
        if (start < 0L) {
            if (!(mc.screen instanceof TitleScreen)) {
                return;
            }
            start = Util.getMillis();
        }
        if (Util.getMillis() - start > MAX_MS) {
            done = true;
            return;
        }
        GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        g.pose().translate(0.0f, 0.0f, 1000.0f);
        g.fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), 0xFF000000);
        g.flush();
    }
}
