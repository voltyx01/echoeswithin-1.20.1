package com.voltyx.efw.nvg.client;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The visor of a night vision helmet folds down onto the eyes and up again over a third of a second (mwccf NVGAnimationHelper). */
public final class NvgAnimation {
    public static final float ANGLE_UP = -1.35F;
    public static final float ANGLE_DOWN = 0.0F;
    private static final float DURATION_SECONDS = 0.35F;

    private static final class State {
        float progress; // 0 = folded up, 1 = down on the eyes
        long lastMs = System.currentTimeMillis();

        State(boolean active) {
            progress = active ? 1.0F : 0.0F;
        }
    }

    private static final Map<Integer, State> STATES = new ConcurrentHashMap<>();

    private NvgAnimation() {
    }

    private static State advance(LivingEntity entity, boolean active) {
        State s = STATES.get(entity.getId());
        if (s == null) {
            s = new State(active);
            STATES.put(entity.getId(), s);
            return s;
        }
        long now = System.currentTimeMillis();
        float dt = (now - s.lastMs) / 1000.0F;
        if (dt > 0.5F) {
            dt = 0.05F;
        }
        if (dt < 0.0F) {
            dt = 0.0F;
        }
        s.lastMs = now;
        float step = dt / DURATION_SECONDS;
        s.progress = active ? Math.min(1.0F, s.progress + step) : Math.max(0.0F, s.progress - step);
        return s;
    }

    /** 0 folded up .. 1 down. */
    public static float progress(LivingEntity entity, boolean active) {
        return entity == null ? (active ? 1.0F : 0.0F) : advance(entity, active).progress;
    }

    /** The angle of the hinge of the visor (radians), eased with a cosine curve. */
    public static float visorAngle(LivingEntity entity, boolean active) {
        float t = progress(entity, active);
        float eased = (float) (0.5F - 0.5F * Math.cos(t * Math.PI));
        return ANGLE_UP + (ANGLE_DOWN - ANGLE_UP) * eased;
    }

    public static void clear() {
        STATES.clear();
    }
}
