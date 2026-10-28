package com.voltyx.efw.client.anim;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import org.jetbrains.annotations.NotNull;

/**
 * The last frame of an animation that has ended, kept on screen while it fades out (the library let the pose jump back to idle
 * the very tick the animation stopped).
 */
public final class HeldPose implements IAnimation {
    private final IAnimation ended;
    private int ticks;

    public HeldPose(IAnimation ended) {
        this.ended = ended;
    }

    /** Only as long as the fade-out needs it: a fade nested on top of it must not keep it alive. */
    @Override
    public boolean isActive() {
        return ticks <= AnimBlend.END_FADE_TICKS + 1;
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float tickDelta, @NotNull Vec3f value0) {
        return ended.get3DTransform(modelName, type, tickDelta, value0);
    }

    @Override
    public void setupAnim(float tickDelta) {
        // frozen: the ended animation keeps the time it stopped at
    }

    @Override
    public @NotNull FirstPersonMode getFirstPersonMode(float tickDelta) {
        return ended.getFirstPersonMode(tickDelta);
    }

    @Override
    public @NotNull FirstPersonConfiguration getFirstPersonConfiguration(float tickDelta) {
        return ended.getFirstPersonConfiguration(tickDelta);
    }
}
