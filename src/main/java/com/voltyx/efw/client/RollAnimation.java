package com.voltyx.efw.client;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * The roll animation (assets/efw/player_animation/roll.json, the Combat Roll clip) played through Player Animator.
 * Only loaded when the playeranimator mod is present.
 */
final class RollAnimation {
    private static final ResourceLocation LAYER = new ResourceLocation("efw", "roll");
    private static final ResourceLocation CLIP = new ResourceLocation("efw", "roll");
    /** The same roll without the arms: with a TACZ gun in the main hand the arms keep holding it. */
    private static final ResourceLocation CLIP_GUN = new ResourceLocation("efw", "roll_gun");
    /** Same top priority as Combat Roll's layer: nothing else on the stack is blended into the roll. */
    private static final int PRIORITY = 1000;
    /** Combat Roll's roll_duration: the clip is sped up so its endTick fits into this many ticks. */
    private static final float ROLL_DURATION = 8f;

    private static final java.util.Map<ModifierLayer<?>, SpeedModifier> SPEEDS = new java.util.WeakHashMap<>();

    private RollAnimation() {
    }

    static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> {
            ModifierLayer<IAnimation> layer = new ModifierLayer<>();
            SpeedModifier speed = new SpeedModifier(1.2f);
            layer.addModifierLast(speed);
            SPEEDS.put(layer, speed);
            return layer;
        });
    }

    @SuppressWarnings("unchecked")
    static void play(AbstractClientPlayer player) {
        ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        KeyframeAnimation clip = PlayerAnimationRegistry.getAnimation(com.voltyx.efw.compat.TaczCompat.isHoldingGun(player) ? CLIP_GUN : CLIP);
        if (layer == null || clip == null) {
            return;
        }
        SpeedModifier speed = SPEEDS.get(layer);
        if (speed != null) {
            speed.speed = clip.endTick / ROLL_DURATION;
        }
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(clip.beginTick, Ease.INOUTSINE), new KeyframeAnimationPlayer(clip, 0));
    }
}
