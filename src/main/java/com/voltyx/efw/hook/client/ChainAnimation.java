package com.voltyx.efw.hook.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.hook.ChainHookEntity;
import com.voltyx.efw.hook.ChainHookItem;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The thrower's body while using the chain (assets/efw/player_animation/chain_*.json), for every player in sight, read from what
 * every client already has: the item being used (the swing over the head) and the synced state of the hook (the throw, the pull
 * of a light creature, the flight to a heavy one). Only loaded with Player Animator.
 */
public final class ChainAnimation {
    private static final ResourceLocation LAYER = new ResourceLocation(EFW.MOD_ID, "chain");
    /** Under the roll (1000), over the walking and running of Serious Player Animations. */
    private static final int PRIORITY = 950;

    private static final String WINDUP = "chain_windup";
    private static final String THROW = "chain_throw";
    private static final String YANK = "chain_yank";
    private static final String FLY = "chain_fly";
    /** The kick of the second run's landing (without the combo the landing strikes as the attack key does, HookClient). */
    private static final String SLAM = "chain_slam";

    private static final class Track {
        String playing;
        int hookId = -1;
        byte hookState = -1;
    }

    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private ChainAnimation() {
    }

    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> new ModifierLayer<IAnimation>());
    }

    /** Once a client tick, after the world ticked. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TRACKS.clear();
            return;
        }
        for (AbstractClientPlayer player : mc.level.players()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track());
            update(player, track, HookClient.hookOf(player));
        }
        TRACKS.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
    }

    private static void update(AbstractClientPlayer player, Track track, ChainHookEntity hook) {
        boolean swinging = player.isUsingItem() && player.getUseItem().getItem() instanceof ChainHookItem;
        byte state = hook != null ? hook.state() : -1;
        int hookId = hook != null ? hook.getId() : -1;
        boolean newHook = hookId != -1 && hookId != track.hookId;
        boolean changed = newHook || state != track.hookState;
        track.hookId = hookId;
        track.hookState = state;

        String wanted = null;
        int fade = 3;
        if (swinging) {
            wanted = WINDUP;
            fade = 4;
        } else if (state == ChainHookEntity.PULLED || state == ChainHookEntity.THROUGH) {
            wanted = FLY;
        } else if (changed && state == ChainHookEntity.IMPACT && hook.combo()) {
            wanted = SLAM; // the kick the second run springs off
            fade = 2;
        } else if (changed && state == ChainHookEntity.REHOOK) {
            wanted = THROW; // the hook thrown again from the top of the bounce
            fade = 2;
        } else if (changed && state == ChainHookEntity.DRAG) {
            wanted = YANK;
            fade = 2;
        } else if (newHook && state == ChainHookEntity.FLYING) {
            wanted = THROW;
            fade = 2;
        }
        if (changed && state == ChainHookEntity.ROLL) {
            // the combo ends rolling out on the other side of the creature; Better Combat's swing of the blow just before plays on a
            // layer over the roll's (2000 to 1000) and would hide it but the turn of the head: it is cut short
            stopBetterCombatSwing(player);
            Vec3 v = player.getDeltaMovement();
            com.voltyx.efw.client.RollClient.trigger(player, new Vec3(v.x, 0.0, v.z));
        }
        if (wanted != null) {
            if (!wanted.equals(track.playing) || !(WINDUP.equals(wanted) || FLY.equals(wanted))) {
                play(player, wanted, fade);
                track.playing = wanted;
            }
        } else if (WINDUP.equals(track.playing) || FLY.equals(track.playing)) {
            // a loop that ended without its follow-up (the swing let go too early, the chain snapped): back to normal
            stop(player);
            track.playing = null;
        }
    }

    private static java.lang.reflect.Method stopSwing;
    private static boolean swingLookedUp;

    /** Better Combat's attack animation of this player, faded out in a couple of ticks (nothing without Better Combat). */
    private static void stopBetterCombatSwing(AbstractClientPlayer player) {
        if (!swingLookedUp) {
            swingLookedUp = true;
            try {
                stopSwing = Class.forName("net.bettercombat.client.animation.PlayerAttackAnimatable").getMethod("stopAttackAnimation", float.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                stopSwing = null;
            }
        }
        if (stopSwing != null && stopSwing.getDeclaringClass().isInstance(player)) {
            try {
                stopSwing.invoke(player, 2.0F);
            } catch (ReflectiveOperationException | RuntimeException e) {
                stopSwing = null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layer(AbstractClientPlayer player) {
        return (ModifierLayer<IAnimation>) PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
    }

    private static void play(AbstractClientPlayer player, String clip, int fade) {
        ModifierLayer<IAnimation> layer = layer(player);
        KeyframeAnimation data = PlayerAnimationRegistry.getAnimation(new ResourceLocation(EFW.MOD_ID, clip));
        if (layer != null && data != null) {
            layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fade, Ease.INOUTSINE), new KeyframeAnimationPlayer(data));
        }
    }

    private static void stop(AbstractClientPlayer player) {
        ModifierLayer<IAnimation> layer = layer(player);
        if (layer != null) {
            layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(5, Ease.INOUTSINE), null);
        }
    }
}
