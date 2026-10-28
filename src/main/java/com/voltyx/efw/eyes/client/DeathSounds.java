package com.voltyx.efw.eyes.client;

import com.mojang.blaze3d.audio.Channel;
import com.voltyx.efw.EFW;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The sound of an instant death: every sound playing is cut off at once, only its echo rings on. With Sound Physics Remastered the
 * direct sound of each playing source is silenced and its reverb sends opened wide (the tail fades out by itself); without it they
 * simply stop. EyesOpen stops them once the echo has died. The world's background (ambience, weather, music) is left alone.
 */
final class DeathSounds {
    private static Field instanceToChannel;
    private static Field source;
    private static Method setEnvironment;
    private static boolean failed;
    /** The sounds the death cut off: only these are stopped when the echo is over. */
    private static final List<SoundInstance> CUT = new ArrayList<>();

    private DeathSounds() {
    }

    /**
     * The world's background (ambience, weather, music, records) goes on through the death: cutting and then stopping it left the
     * ambience mods' loops dead (they do not start a loop they think is playing).
     */
    static boolean keeps(SoundSource category) {
        return category == SoundSource.AMBIENT || category == SoundSource.WEATHER || category == SoundSource.MUSIC
                || category == SoundSource.RECORDS;
    }

    /**
     * AmbientSounds plays its loops in the category the pack sets (MASTER by default), not AMBIENT: they are told by their class.
     * Stopped, they stay dead for good (the mod thinks they play).
     */
    static boolean keeps(SoundInstance sound) {
        return keeps(sound.getSource()) || sound.getClass().getName().startsWith("team.creative.ambientsounds.")
                || "ambientsounds".equals(sound.getLocation().getNamespace());
    }

    static void cut() {
        SoundManager manager = Minecraft.getInstance().getSoundManager();
        CUT.clear();
        if (!echo(manager)) {
            for (SoundInstance sound : playing(manager)) {
                manager.stop(sound);
            }
        }
    }

    /** The sounds cut off at the death are stopped now (their echo is over, or the player is back). */
    static void stopCut() {
        SoundManager manager = Minecraft.getInstance().getSoundManager();
        for (SoundInstance sound : CUT) {
            manager.stop(sound);
        }
        CUT.clear();
    }

    /** What is playing now that is not the world's background. */
    private static List<SoundInstance> playing(SoundManager manager) {
        List<SoundInstance> out = new ArrayList<>();
        try {
            if (instanceToChannel == null) {
                instanceToChannel = ObfuscationReflectionHelper.findField(SoundEngine.class, "f_120226_");
            }
            SoundEngine engine = ObfuscationReflectionHelper.getPrivateValue(SoundManager.class, manager, "f_120349_");
            for (Object key : ((Map<?, ?>) instanceToChannel.get(engine)).keySet()) {
                if (key instanceof SoundInstance sound && !keeps(sound)) {
                    out.add(sound);
                }
            }
        } catch (Exception e) {
            EFW.LOGGER.warn("[Eyes] cannot list the playing sounds: {}", e.toString());
        }
        return out;
    }

    private static boolean echo(SoundManager manager) {
        if (failed) {
            return false;
        }
        try {
            if (setEnvironment == null) {
                Class<?> spr = Class.forName("com.sonicether.soundphysics.SoundPhysics");
                setEnvironment = spr.getMethod("setEnvironment", int.class, float.class, float.class, float.class, float.class, float.class,
                        float.class, float.class, float.class, float.class, float.class);
                instanceToChannel = ObfuscationReflectionHelper.findField(SoundEngine.class, "f_120226_");
                source = ObfuscationReflectionHelper.findField(Channel.class, "f_83642_");
            }
            SoundEngine engine = ObfuscationReflectionHelper.getPrivateValue(SoundManager.class, manager, "f_120349_");
            Map<?, ?> channels = (Map<?, ?>) instanceToChannel.get(engine);
            for (Map.Entry<?, ?> entry : new ArrayList<>(channels.entrySet())) {
                if (!(entry.getKey() instanceof SoundInstance sound) || keeps(sound)) {
                    continue;
                }
                CUT.add(sound);
                // on the sound thread, where the sources live
                ((ChannelAccess.ChannelHandle) entry.getValue()).execute(channel -> {
                    try {
                        setEnvironment.invoke(null, source.getInt(channel), 0.4f, 0.7f, 1.0f, 1.0f, 0.6f, 0.6f, 0.5f, 0.5f, 0.0f, 0.0f);
                    } catch (ReflectiveOperationException ignored) {
                        channel.stop();
                    }
                });
            }
            return true;
        } catch (ClassNotFoundException e) {
            failed = true; // no Sound Physics Remastered
            return false;
        } catch (Exception e) {
            failed = true;
            EFW.LOGGER.warn("[Eyes] no death echo: {}", e.toString());
            return false;
        }
    }
}
