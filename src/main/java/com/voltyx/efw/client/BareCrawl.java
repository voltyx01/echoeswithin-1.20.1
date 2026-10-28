package com.voltyx.efw.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.TaczCompat;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Crawling without a gun (TACZ's crawl key, which otherwise needs a gun in the hand; the crawl itself is let through by the
 * TaczCrawl mixins). Lying players without a gun get TACZ's pistol crawl animation ({@code lie_move} of pistol_default) in the third
 * person: it plays while they move, and when they stop it plays to its last frame and holds there. Lying with or without a gun,
 * the body keeps facing where the head does.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class BareCrawl {
    private static final ResourceLocation LAYER = new ResourceLocation(EFW.MOD_ID, "bare_crawl");
    private static final ResourceLocation PISTOL = new ResourceLocation("tacz", "pistol_default.player_animation");
    private static final String CLIP = "lie_move";
    private static final int PRIORITY = 900;
    private static final int FADE = 6;
    private static final boolean ACTIVE = ModList.get().isLoaded("tacz") && ModList.get().isLoaded("playeranimator");

    private static final Map<ModifierLayer<?>, SpeedModifier> SPEEDS = new WeakHashMap<>();
    private static final Map<AbstractClientPlayer, KeyframeAnimationPlayer> PLAYING = new WeakHashMap<>();
    /** Whether the player was lying (with or without a gun) the tick before. */
    private static final Map<AbstractClientPlayer, Boolean> PRONE = new WeakHashMap<>();

    private BareCrawl() {
    }

    /** Client setup, with Player Animator present. */
    public static void register() {
        if (!ACTIVE) {
            return;
        }
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> {
            ModifierLayer<IAnimation> layer = new ModifierLayer<>();
            SpeedModifier speed = new SpeedModifier(1.0f);
            layer.addModifierLast(speed);
            SPEEDS.put(layer, speed);
            return layer;
        });
    }

    // ------------------------------------------------------------------------------------------------ the key

    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        if (ACTIVE) {
            Impl.onKey(e.getKey(), e.getScanCode(), e.getAction());
        }
    }

    /** Touches TACZ classes: only reached with TACZ loaded. */
    private static final class Impl {
        static void onKey(int key, int scanCode, int action) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || !com.tacz.guns.client.input.CrawlKey.CRAWL_KEY.matches(key, scanCode)
                    || !com.tacz.guns.util.InputExtraCheck.isInGame() || !com.tacz.guns.config.sync.SyncConfig.ENABLE_CRAWL.get()
                    || player.isSpectator() || player.isPassenger() || TaczCompat.isGun(player.getMainHandItem())) {
                return;
            }
            com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator operator =
                    com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(player);
            boolean hold = com.tacz.guns.config.client.KeyConfig.HOLD_TO_CRAWL.get();
            if (action == GLFW.GLFW_PRESS) {
                operator.crawl(hold || !operator.isCrawl());
            } else if (hold && action == GLFW.GLFW_RELEASE) {
                operator.crawl(false);
            }
        }

        static KeyframeAnimation clip() {
            return ((com.voltyx.efw.mixin.TaczPlayerAnimAssetsInvoker) com.tacz.guns.compat.playeranimator.animation.PlayerAnimatorAssetManager.get())
                    .efw$getAnimations(PISTOL, CLIP).orElse(null);
        }
    }

    // ------------------------------------------------------------------------------------------------ the animation

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!ACTIVE || e.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        for (AbstractClientPlayer player : mc.level.players()) {
            update(player);
        }
    }

    @SuppressWarnings("unchecked")
    private static void update(AbstractClientPlayer player) {
        boolean prone = player.getPose() == Pose.SWIMMING && !player.isSwimming() && !player.isInWater();
        if (prone) {
            // lying, the body turns with the head, not towards the way it moves: strafing turned the lying model around its feet,
            // so it swung off to the side of its shadow
            player.yBodyRot = player.yHeadRot;
            player.yBodyRotO = player.yHeadRotO;
        }
        Boolean wasProne = PRONE.put(player, prone);
        ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        if (layer == null) {
            return;
        }
        boolean lying = prone && !TaczCompat.isGun(player.getMainHandItem());
        SpeedModifier speed = SPEEDS.get(layer);
        KeyframeAnimationPlayer playing = PLAYING.get(player);
        if (!lying) {
            if (playing != null) {
                // the fade sits under the speed modifier: frozen on the last frame it never ran, and the pose stayed for good
                if (speed != null) {
                    speed.speed = 1.0f;
                }
                if (prone) {
                    // a gun taken while lying: TACZ's lying pose takes over at once (a fade between the two spun the arms around)
                    layer.setAnimation(null);
                } else {
                    layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE, Ease.INOUTSINE), null);
                }
                PLAYING.remove(player);
            }
            return;
        }
        double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
        boolean moving = dx * dx + dz * dz > 1.0E-4;
        if (playing == null || layer.getAnimation() == null) {
            KeyframeAnimation clip = Impl.clip();
            if (clip == null) {
                return;
            }
            // lying down still: straight into the held last frame
            playing = new KeyframeAnimationPlayer(clip, moving ? 0 : clip.endTick);
            if (Boolean.TRUE.equals(wasProne)) {
                layer.setAnimation(playing); // the gun put away while lying: no fade from TACZ's pose either
            } else {
                layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE, Ease.INOUTSINE), playing);
            }
            PLAYING.put(player, playing);
        }
        if (speed != null) {
            // stopped: the cycle plays to its last frame and holds it
            int end = playing.getData().endTick;
            speed.speed = moving || playing.getTick() < end - 1 ? 1.0f : 0.0f;
        }
    }
}
