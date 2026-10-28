package com.voltyx.efw.mixin;

import com.voltyx.efw.eyes.client.EyesOpen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the eyes are opening (EyesOpen) every sound that starts comes as if through water: Sound Physics Remastered's environment of
 * the sound is pulled toward a long, loud echo and a dull direct sound. The respawn sound itself stays clear, and so does the world's
 * background (ambience, weather, music).
 */
@Pseudo
@Mixin(targets = "com.sonicether.soundphysics.SoundPhysics", remap = false)
public abstract class SprEyesMuffleMixin {
    /** Echo sends (short to long reverb) and their brightness, the direct sound's brightness and loudness, fully muffled. */
    private static final float[] EFW$TARGET = {0.25f, 0.45f, 0.75f, 1.0f, 0.55f, 0.55f, 0.55f, 0.55f, 0.06f, 0.55f};

    private static boolean efw$inside;
    private static ResourceLocation efw$sound;
    private static SoundSource efw$category;

    @Shadow
    public static void setEnvironment(int sourceID, float g0, float g1, float g2, float g3, float c0, float c1, float c2, float c3,
            float directCutoff, float directGain) {
    }

    @Inject(method = "setLastSoundCategoryAndName", at = @At("HEAD"), remap = false, require = 0)
    private static void efw$sound(SoundSource category, ResourceLocation sound, CallbackInfo ci) {
        efw$sound = sound;
        efw$category = category;
    }

    @Inject(method = "setEnvironment", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void efw$muffle(int sourceID, float g0, float g1, float g2, float g3, float c0, float c1, float c2, float c3,
            float directCutoff, float directGain, CallbackInfo ci) {
        float m = EyesOpen.muffle();
        // ambience, weather and music are left clear: a loop that starts muffled stays muffled for as long as it plays
        if (efw$inside || m <= 0.0f || (efw$sound != null && "efw".equals(efw$sound.getNamespace()) && "respawn".equals(efw$sound.getPath()))
                || (efw$sound != null && "ambientsounds".equals(efw$sound.getNamespace()))
                || efw$category == SoundSource.AMBIENT || efw$category == SoundSource.WEATHER || efw$category == SoundSource.MUSIC
                || efw$category == SoundSource.RECORDS) {
            return;
        }
        float[] v = {g0, g1, g2, g3, c0, c1, c2, c3, directCutoff, directGain};
        for (int i = 0; i < v.length; i++) {
            // more echo and less brightness, never less than the place already gives
            v[i] = i < 4 ? Math.max(v[i], v[i] + (EFW$TARGET[i] - v[i]) * m) : Math.min(v[i], v[i] + (EFW$TARGET[i] - v[i]) * m);
        }
        efw$inside = true;
        try {
            setEnvironment(sourceID, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9]);
        } finally {
            efw$inside = false;
        }
        ci.cancel();
    }
}
