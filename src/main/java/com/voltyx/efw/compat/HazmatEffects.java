package com.voltyx.efw.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/**
 * Survival Instinct's hazmat suit protects from the vanilla bad effects by removing them every tick, after they were already given:
 * a source that keeps giving one (a poison cloud, a mob's aura) made it come and go all the time, and the Sounds mod played its
 * gain and lose sounds over and over. The full suit now keeps those effects from being given at all (its own removal stays, it just
 * finds nothing).
 */
public final class HazmatEffects {
    private static final String NS = "survival_instinct";
    /** The effects HazmatArmorSuitProcedure removes. */
    private static final Set<MobEffect> BLOCKED = Set.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.DARKNESS, MobEffects.BLINDNESS,
            MobEffects.HUNGER, MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.CONFUSION, MobEffects.DIG_SLOWDOWN,
            MobEffects.LEVITATION);
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] PIECES = {"hazmat_helmet", "hazmat_chestplate", "hazmat_leggings", "hazmat_boots"};

    private HazmatEffects() {
    }

    @SubscribeEvent
    public static void onApplicable(MobEffectEvent.Applicable event) {
        if (BLOCKED.contains(event.getEffectInstance().getEffect()) && fullSuit(event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
    }

    private static boolean fullSuit(LivingEntity entity) {
        for (int i = 0; i < SLOTS.length; i++) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(entity.getItemBySlot(SLOTS[i]).getItem());
            if (id == null || !NS.equals(id.getNamespace()) || !PIECES[i].equals(id.getPath())) {
                return false;
            }
        }
        return true;
    }
}
