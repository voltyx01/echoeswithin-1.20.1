package com.voltyx.efw.mixin;

import com.voltyx.efw.gear.WornOlympicItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Legendary Survival Overhaul: the worn track jacket keeps some of the rain and water off, the wetness its wearer gains is about a
 * third smaller (drying is not touched). LSO knows no item that does this (only its sponge charm, which stops wetness entirely), so the
 * gain is scaled here while the capability ticks for a player in the jacket.
 */
@Pseudo
@Mixin(targets = "sfiomn.legendarysurvivaloverhaul.common.capabilities.wetness.WetnessCapability", remap = false)
public abstract class LsoWetnessMixin {
    @Unique
    private static final float EFW$JACKET_GAIN = 0.65f;
    @Unique
    private boolean efw$jacket;
    @Unique
    private Player efw$player;

    @Inject(method = "tickUpdate", at = @At("HEAD"), remap = false, require = 0)
    private void efw$tickStart(Player player, Level level, TickEvent.Phase phase, CallbackInfo ci) {
        efw$player = player;
        efw$jacket = phase == TickEvent.Phase.END && WornOlympicItem.wears(player);
    }

    @Inject(method = "tickUpdate", at = @At("RETURN"), remap = false, require = 0)
    private void efw$tickEnd(Player player, Level level, TickEvent.Phase phase, CallbackInfo ci) {
        efw$jacket = false;
        efw$player = null;
    }

    @ModifyVariable(method = "addWetness", at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private int efw$jacketGain(int wetness) {
        if (!efw$jacket || wetness <= 0) {
            return wetness;
        }
        float scaled = wetness * EFW$JACKET_GAIN;
        int whole = (int) scaled;
        // small steps (rain adds a point or two) are rounded at random, so they shrink on average too
        return whole + (efw$player != null && efw$player.getRandom().nextFloat() < scaled - whole ? 1 : 0);
    }
}
