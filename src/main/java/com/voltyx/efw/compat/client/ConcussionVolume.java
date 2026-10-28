package com.voltyx.efw.compat.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.mixin.SoundEngineAccessor;
import com.voltyx.efw.mixin.SoundManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Explosion Overhaul's concussion muffles the positioned sounds only; the ambience (AmbientSounds' environment, the vanilla ambient
 * sounds) went on as if nothing happened. While the concussion is on, the ambience fades out completely and comes back after it. Only
 * the volume is touched: changing the filters of these sounds fights Sound Physics Remastered's and the sound squeaks.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class ConcussionVolume {
    private static final boolean LOADED = ModList.get().isLoaded("explosionoverhaul");
    private static final String AMBIENT_SOUNDS = "team.creative.ambientsounds.";
    /** Volume change per tick: out in half a second, back in two. */
    private static final float FADE_OUT = 0.1F, FADE_IN = 0.025F;

    private static float ambience = 1.0F;
    private static boolean resolved;
    private static Method gain;

    private ConcussionVolume() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !LOADED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            return;
        }
        float target = concussed() ? 0.0F : 1.0F;
        if (target == 1.0F && ambience >= 1.0F) {
            return;
        }
        ambience = target < ambience ? Math.max(target, ambience - FADE_OUT) : Math.min(target, ambience + FADE_IN);
        float factor = ambience;
        try {
            Map<SoundInstance, ChannelAccess.ChannelHandle> playing =
                    ((SoundEngineAccessor) ((SoundManagerAccessor) mc.getSoundManager()).efw$soundEngine()).efw$instanceToChannel();
            for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry : playing.entrySet()) {
                if (isAmbience(entry.getKey())) {
                    entry.getValue().execute(ch -> ((ChannelVolumeAccess) ch).efw$setVolumeFactor(factor));
                }
            }
        } catch (RuntimeException ignored) {
            // no sound engine yet, or the map changed under us: the next tick
        }
    }

    private static boolean isAmbience(SoundInstance sound) {
        return sound.getClass().getName().startsWith(AMBIENT_SOUNDS) || sound.getSource() == SoundSource.AMBIENT;
    }

    /** The concussion is on: its muffling has started and has not faded out yet. */
    private static boolean concussed() {
        Method m = gain();
        if (m == null) {
            return false;
        }
        try {
            return (float) m.invoke(null) < 0.98F;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private static Method gain() {
        if (!resolved) {
            resolved = true;
            try {
                gain = Class.forName("com.vinlanx.explosionoverhaul.client.LowPassConcussionEffect").getMethod("getCurrentGainMultiplier");
            } catch (ReflectiveOperationException | LinkageError e) {
                gain = null;
            }
        }
        return gain;
    }
}
