package com.voltyx.efw.client.autohud;

import com.voltyx.efw.client.autohud.AutoHudConfig.Part;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * One part of the HUD that hides by itself (Auto HUD's Component): how far it is slid out (0 shown .. 1 hidden), how visible it is,
 * and for how many more ticks it stays up. The animation moves fast at first and slows down near the end, as in Auto HUD.
 */
public final class HudPart {
    final String name;
    final Part config;
    final boolean mainHud;
    /** Parts that stay up with this one in STACKED mode (the ones drawn above it). */
    final List<HudPart> stacked = new ArrayList<>();
    /** The value that decides when the part shows up (null: driven by events only). */
    private final Supplier<Object> value;
    private Object oldValue;
    private boolean hasOld;

    double offset;
    double offsetDelta;
    double alpha = 1.0;
    double alphaDelta;
    int visibleTime = 1;

    HudPart(String name, Part config, boolean mainHud, Supplier<Object> value) {
        this.name = name;
        this.config = config;
        this.mainHud = mainHud;
        this.value = value;
    }

    HudPart stack(HudPart... parts) {
        stacked.addAll(List.of(parts));
        return this;
    }

    boolean isActive() {
        return AutoHud.dynamic() && config.active.get();
    }

    // ---- what the renderer asks ------------------------------------------------------------------------------------

    double offsetX(float partial) {
        if (!AutoHudConfig.MOVE.get()) {
            return 0;
        }
        double d = (offset + partial * offsetDelta) * config.distance.get();
        return switch (config.direction.get()) {
            case LEFT -> -d;
            case RIGHT -> d;
            default -> 0;
        };
    }

    double offsetY(float partial) {
        if (!AutoHudConfig.MOVE.get()) {
            return 0;
        }
        double d = (offset + partial * offsetDelta) * config.distance.get();
        return switch (config.direction.get()) {
            case UP -> -d;
            case DOWN -> d;
            default -> 0;
        };
    }

    float alpha(float partial) {
        if (!AutoHudConfig.FADE.get()) {
            return 1.0f;
        }
        double min = config.maximumFade.get();
        return (float) Math.max(0.0, Math.min(1.0, (1.0 - min) * (alpha + partial * alphaDelta) + min));
    }

    boolean fullyRevealed() {
        return offset == 0 && alpha == 1;
    }

    boolean fullyHidden() {
        return offset == 1 && alpha == 0;
    }

    /** False when the part is gone completely: then it is not drawn at all. */
    boolean shouldRender() {
        if (!isActive()) {
            return true;
        }
        if (AutoHudConfig.FADE.get()) {
            return !fullyHidden() || config.maximumFade.get() > 0;
        }
        return !fullyHidden();
    }

    // ---- showing and hiding ----------------------------------------------------------------------------------------

    /** Keeps the part up for the configured time (Auto HUD's reveal). */
    void reveal(int ticks) {
        visibleTime = Math.max(visibleTime, ticks);
    }

    /** Shows the part together with the parts that belong to it (Auto HUD's revealCombined). */
    void revealCombined() {
        int ticks = AutoHudConfig.TICKS_REVEALED.get();
        reveal(ticks);
        switch (AutoHudConfig.REVEAL_TYPE.get()) {
            case COMBINED -> AutoHud.parts().forEach(p -> p.reveal(ticks));
            case GROUPED -> {
                if (mainHud) {
                    AutoHud.parts().stream().filter(p -> p.mainHud).forEach(p -> p.reveal(ticks));
                }
            }
            case STACKED -> keepStacked(ticks);
            case INDIVIDUAL -> {
            }
        }
    }

    private void keepStacked(int ticks) {
        for (HudPart p : stacked) {
            // only parts that are up already stay up longer, so a bar does not slide through the ones above it
            if (p.visibleTime > 0 && p.visibleTime < ticks) {
                p.visibleTime = ticks;
            }
            p.keepStacked(ticks);
        }
    }

    void forceHide() {
        visibleTime = 0;
    }

    void revealNow() {
        visibleTime = Math.max(visibleTime, AutoHudConfig.TICKS_REVEALED.get());
        offset = 0;
        offsetDelta = 0;
        alpha = 1;
        alphaDelta = 0;
    }

    /** Checks the watched value against the policy and shows the part when it says so (Auto HUD's PolicyComponentState). */
    void update(boolean held) {
        update(held, false);
    }

    /**
     * Muted: the value is followed but does not bring the part out; a part that is up stays up for its time, a hidden one stays
     * hidden (a change while muted is not shown later either).
     */
    void update(boolean held, boolean muted) {
        if (value == null) {
            return;
        }
        Object now = value.get();
        if (now == null) {
            hasOld = false;
            return;
        }
        if (!hasOld) {
            oldValue = now;
            hasOld = true;
        }
        if (!muted && (held || doReveal(now, oldValue))) {
            revealCombined();
        }
        oldValue = now;
    }

    private boolean doReveal(Object now, Object old) {
        AutoHudConfig.Policy policy = config.policy.get();
        if (now instanceof Level level && old instanceof Level was) {
            return switch (policy) {
                case ALWAYS -> true;
                case DISABLED -> false;
                case CHANGING -> level.value != was.value;
                case INCREASING -> level.value > was.value;
                case DECREASING -> level.value < was.value;
                case NOT_FULL -> level.value < level.max;
                case LOW -> level.value <= level.max / 3;
            };
        }
        return switch (policy) {
            case ALWAYS -> true;
            case DISABLED -> false;
            default -> !now.equals(old);
        };
    }

    /** A value with its maximum, for the policies that compare against the full bar. */
    record Level(int value, int max) {
    }

    void tick() {
        if (visibleTime == 0) {
            if (!fullyHidden()) {
                moveOut();
            }
            if (offset == 1) {
                offsetDelta = 0;
            }
            if (alpha == 0) {
                alphaDelta = 0;
            }
        } else if (!fullyRevealed()) {
            moveIn();
        } else {
            offsetDelta = 0;
            alphaDelta = 0;
        }
        if (isActive() && visibleTime > 0) {
            visibleTime--;
        }
    }

    private double speed() {
        return AutoHudConfig.SPEED.get() * config.speed.get();
    }

    private void moveIn() {
        offset = Math.max(0, offset + offsetDelta);
        alpha = Math.min(1, alpha + alphaDelta);
        double offsetSpeed = Math.sqrt(0.01 + offset) * 0.1 * speed();
        double alphaSpeed = 0.05 * speed();
        offsetDelta = offset - offsetSpeed <= 0 ? -offset : -offsetSpeed;
        alphaDelta = alpha + alphaSpeed >= 1 ? 1 - alpha : alphaSpeed;
    }

    private void moveOut() {
        offset = Math.min(1, offset + offsetDelta);
        alpha = Math.max(0, alpha + alphaDelta);
        double offsetSpeed = Math.sqrt(0.01 + offset) * 0.1 * speed();
        double alphaSpeed = 0.05 * speed();
        offsetDelta = offset + offsetSpeed >= 1 ? 1 - offset : offsetSpeed;
        alphaDelta = alpha - alphaSpeed <= 0 ? -alpha : -alphaSpeed;
    }
}
