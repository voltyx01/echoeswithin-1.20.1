package com.voltyx.efw.hook;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.registries.ForgeRegistries;

/** The chain's sounds: the chain and impact sounds of SRP's Book of Vengeance when SRP is there, vanilla ones otherwise. */
final class HookSounds {
    private HookSounds() {
    }

    static SoundEvent swing() {
        return srp("vengeance.attack.whoosh", SoundEvents.PLAYER_ATTACK_SWEEP);
    }

    static SoundEvent whoosh() {
        return srp("vengeance.attack.whoosh", SoundEvents.TRIDENT_THROW);
    }

    static SoundEvent chain() {
        return srp("vengeance.attack.chain", SoundEvents.CHAIN_HIT);
    }

    static SoundEvent impact() {
        return srp("vengeance.attack.impact", SoundEvents.PLAYER_ATTACK_STRONG);
    }

    static SoundEvent thud() {
        return srp("vengeance.attack.rock", SoundEvents.PLAYER_ATTACK_KNOCKBACK);
    }

    static SoundEvent snap() {
        return SoundEvents.CHAIN_BREAK;
    }

    private static SoundEvent srp(String path, SoundEvent fallback) {
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("srparasites", path));
        return sound != null ? sound : fallback;
    }
}
