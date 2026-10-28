package com.voltyx.efw.fireweapon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Wrapping a weapon in cloth and setting it on fire (port of the 1.12 FireWeaponHelper). The cloth and the alcohol come from
 * the Survival Instinct mod.
 *
 * Time is kept as game-time stamps instead of counters: the stack changes only when the state changes (wrap, ignite, go out), so
 * the item in hand does not play the re-equip animation every tick; the client reads the same stamps for the visuals.
 *
 * Two burn modes:
 *   CLOTH  (1): plain burning, the cloth percentage drops with time (about 90 s from 100%). Water or rain puts it out and the
 *               remaining cloth stays on the weapon.
 *   SOAKED (2): alcohol soaked cloth burns on a fixed 60 s timer. Water, rain or death destroys all the cloth at once.
 * After either mode ends the charred cloth stays visible for 20 s and then disappears.
 */
public final class FireWeaponHelper {
    public static final String NBT_WRAPPED = "efw_cloth_wrapped";
    public static final String NBT_CLOTH_PCT = "efw_cloth_pct";
    public static final String NBT_IGNITED = "efw_cloth_ignited";
    public static final String NBT_BURN_MODE = "efw_burn_mode";
    /** Game time of the ignition and of the start of the char fade: only transitions touch the NBT, so the held item never re-equips. */
    public static final String NBT_BURN_START = "efw_burn_start";
    public static final String NBT_CHAR_START = "efw_char_start";
    public static final String NBT_SOAKED_READY = "efw_soaked_ready";

    /** CLOTH mode: ticks between two -1% steps (18 x 100 = 90 s). */
    public static final int CLOTH_TICKS_PER_PCT = 18;
    public static final int SOAKED_BURN_TICKS = 1200;
    public static final int CHAR_FADE_TICKS = 400;

    private static final String SI = "survival_instinct";

    private FireWeaponHelper() {
    }

    private static Item si(String path) {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(SI, path));
        return item == Items.AIR ? null : item;
    }

    public static boolean isCloth(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == si("cloth");
    }

    /** Self-igniting cloth: wraps and lights in one go. */
    public static boolean isFlamingCloth(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == com.voltyx.efw.init.EfwContent.FLAMING_CLOTH.get();
    }

    /** Anything that wraps a weapon: plain or self-igniting cloth. */
    public static boolean isWrappingCloth(ItemStack stack) {
        return isCloth(stack) || isFlamingCloth(stack);
    }

    /** Only these may soak the cloth. */
    public static boolean isAlcohol(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return item == si("whiskey") || item == si("tequila") || item == si("gasoline_can");
    }

    /** The drinks leave an empty bottle behind, the gasoline can does not. */
    public static boolean leavesBottle(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() == si("whiskey") || stack.getItem() == si("tequila"));
    }

    public static ItemStack emptyBottle() {
        Item item = si("empty_bottle");
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    public static boolean isFlint(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.FLINT_AND_STEEL;
    }

    /** Any melee weapon: swords, axes, mod weapons with an attack damage; never armour or guns. */
    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof ArmorItem) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if ("tacz".equals(key.getNamespace())) {
            return false;
        }
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE);
    }

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrCreateTag();
    }

    private static CompoundTag tagOrNull(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : stack.getTag();
    }

    // ------------------------------------------------------------------------------------------------ state

    public static boolean isWrapped(ItemStack stack) {
        CompoundTag t = tagOrNull(stack);
        return t != null && t.getBoolean(NBT_WRAPPED);
    }

    /** Cloth durability 0..100 at the moment it was last stored (ignition or extinguishing). */
    private static int storedClothPct(ItemStack stack) {
        CompoundTag t = tagOrNull(stack);
        if (t == null) {
            return 0;
        }
        return t.contains(NBT_CLOTH_PCT) ? t.getInt(NBT_CLOTH_PCT) : 100;
    }

    /** Cloth durability 0..100 right now (a CLOTH mode fire eats 1% per CLOTH_TICKS_PER_PCT ticks). */
    public static int getClothPct(ItemStack stack, long now) {
        int stored = storedClothPct(stack);
        if (isIgnited(stack) && getBurnMode(stack) == 1) {
            return Math.max(0, stored - (int) ((now - burnStart(stack)) / CLOTH_TICKS_PER_PCT));
        }
        return stored;
    }

    private static long burnStart(ItemStack stack) {
        CompoundTag t = tagOrNull(stack);
        return t != null ? t.getLong(NBT_BURN_START) : 0L;
    }

    /** 0 = not burning, 1 = CLOTH, 2 = SOAKED. */
    public static int getBurnMode(ItemStack stack) {
        CompoundTag t = tagOrNull(stack);
        return t != null ? t.getInt(NBT_BURN_MODE) : 0;
    }

    public static boolean isIgnited(ItemStack stack) {
        CompoundTag t = tagOrNull(stack);
        return t != null && t.getBoolean(NBT_IGNITED) && getBurnMode(stack) > 0;
    }

    public static boolean isSoaked(ItemStack stack) {
        return getBurnMode(stack) == 2;
    }

    public static boolean isSoakedReady(ItemStack stack) {
        CompoundTag t = tagOrNull(stack);
        return t != null && t.getBoolean(NBT_WRAPPED) && t.getBoolean(NBT_SOAKED_READY);
    }

    public static boolean isInCharPhase(ItemStack stack) {
        CompoundTag t = tagOrNull(stack);
        return t != null && t.contains(NBT_CHAR_START) && !isIgnited(stack);
    }

    public static int getCharTicks(ItemStack stack, long now) {
        CompoundTag t = tagOrNull(stack);
        if (t == null || !t.contains(NBT_CHAR_START)) {
            return 0;
        }
        return (int) Math.max(0, CHAR_FADE_TICKS - (now - t.getLong(NBT_CHAR_START)));
    }

    /** 1.0 = just charred, 0.0 = gone. */
    public static float getCharAlpha(ItemStack stack, long now) {
        return Math.max(0f, Math.min(1f, (float) getCharTicks(stack, now) / CHAR_FADE_TICKS));
    }

    /** True when the weapon shows anything at all: cloth, fire or char. */
    public static boolean hasVisibleCloth(ItemStack stack) {
        return isIgnited(stack) || isInCharPhase(stack) || isWrapped(stack) || isSoakedReady(stack);
    }

    // ------------------------------------------------------------------------------------------------ actions

    public static void setWrapped(ItemStack stack, boolean wrapped) {
        CompoundTag t = wrapped ? tag(stack) : tagOrNull(stack);
        if (t == null) {
            return;
        }
        boolean replacingChar = wrapped && isInCharPhase(stack);
        if (replacingChar) {
            clearBurn(t);
            t.remove(NBT_CHAR_START);
            t.remove(NBT_SOAKED_READY);
            t.putInt(NBT_CLOTH_PCT, 100);
        }
        t.putBoolean(NBT_WRAPPED, wrapped);
        if (!wrapped) {
            removeAll(t);
        } else if (replacingChar || !t.contains(NBT_CLOTH_PCT)) {
            t.putInt(NBT_CLOTH_PCT, 100);
        }
    }

    /** Ignites in CLOTH mode with whatever cloth is left on the weapon. */
    public static void igniteCloth(ItemStack stack, long now) {
        if (stack.isEmpty() || isInCharPhase(stack)) {
            return;
        }
        CompoundTag t = tag(stack);
        t.putBoolean(NBT_WRAPPED, true);
        t.putBoolean(NBT_IGNITED, true);
        t.putInt(NBT_BURN_MODE, 1);
        t.putLong(NBT_BURN_START, now);
        t.remove(NBT_SOAKED_READY);
        if (!t.contains(NBT_CLOTH_PCT)) {
            t.putInt(NBT_CLOTH_PCT, 100);
        }
        t.remove(NBT_CHAR_START);
    }

    /** Soaks with alcohol and lights it at once: SOAKED mode, cloth back to 100%. */
    public static void soakAndIgnite(ItemStack stack, long now) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag t = tag(stack);
        t.putBoolean(NBT_WRAPPED, true);
        t.putBoolean(NBT_IGNITED, true);
        t.putInt(NBT_BURN_MODE, 2);
        t.putInt(NBT_CLOTH_PCT, 100);
        t.putLong(NBT_BURN_START, now);
        t.remove(NBT_SOAKED_READY);
        t.remove(NBT_CHAR_START);
    }

    /** Soaked, waiting for the flint and steel. */
    public static void markSoaked(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag t = tag(stack);
        t.putBoolean(NBT_WRAPPED, true);
        t.putInt(NBT_CLOTH_PCT, 100);
        t.putBoolean(NBT_SOAKED_READY, true);
    }

    /**
     * Puts the fire out and starts the char fade.
     *
     * @param consumeCloth true wipes the cloth (SOAKED mode, death); false keeps what is left (CLOTH mode in rain).
     */
    public static void extinguish(ItemStack stack, boolean consumeCloth, long now) {
        if (stack.isEmpty()) {
            return;
        }
        int pct = consumeCloth ? 0 : getClothPct(stack, now);
        CompoundTag t = tag(stack);
        clearBurn(t);
        t.putInt(NBT_CLOTH_PCT, pct);
        t.putLong(NBT_CHAR_START, now);
    }

    private static void clearBurn(CompoundTag t) {
        t.putBoolean(NBT_IGNITED, false);
        t.putInt(NBT_BURN_MODE, 0);
        t.remove(NBT_BURN_START);
    }

    private static void removeAll(CompoundTag t) {
        t.remove(NBT_IGNITED);
        t.remove(NBT_BURN_MODE);
        t.remove(NBT_BURN_START);
        t.remove(NBT_CLOTH_PCT);
        t.remove(NBT_CHAR_START);
        t.remove(NBT_SOAKED_READY);
    }

    /** The charred cloth is gone: back to a clean weapon (and no empty tag left behind, so stacks merge again). */
    public static void removeCloth(ItemStack stack) {
        CompoundTag t = tagOrNull(stack);
        if (t == null) {
            return;
        }
        t.remove(NBT_WRAPPED);
        removeAll(t);
        if (t.isEmpty()) {
            stack.setTag(null);
        }
    }

    /**
     * Server check of one weapon: finishes a fire that has burnt down and removes charred cloth that has faded.
     * Returns true when the weapon just went out and entered the char phase.
     */
    public static boolean checkBurn(ItemStack stack, long now) {
        if (isInCharPhase(stack)) {
            if (getCharTicks(stack, now) <= 0) {
                removeCloth(stack);
            }
            return false;
        }
        if (!isIgnited(stack)) {
            return false;
        }
        int mode = getBurnMode(stack);
        if (mode == 1 && getClothPct(stack, now) <= 0) {
            extinguish(stack, false, now);
            return true;
        }
        if (mode == 2 && now - burnStart(stack) >= SOAKED_BURN_TICKS) {
            extinguish(stack, true, now);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ display

    /** 0.0 just lit .. 1.0 nearly out. */
    public static float getBurnProgress(ItemStack stack, long now) {
        if (!isIgnited(stack)) {
            return 0.0F;
        }
        if (getBurnMode(stack) == 1) {
            return Math.max(0f, Math.min(1f, 1.0F - getClothPct(stack, now) / 100.0F));
        }
        return Math.max(0f, Math.min(1f, (float) (now - burnStart(stack)) / SOAKED_BURN_TICKS));
    }

    public static int getRemainingSeconds(ItemStack stack, long now) {
        if (!isIgnited(stack)) {
            return 0;
        }
        if (getBurnMode(stack) == 1) {
            return getClothPct(stack, now) * CLOTH_TICKS_PER_PCT / 20;
        }
        return (int) Math.max(0, (SOAKED_BURN_TICKS - (now - burnStart(stack))) / 20);
    }

    /** Extra fire damage: +3 (CLOTH) or +5 (SOAKED). */
    public static float getFireDamageBonus(ItemStack stack) {
        return switch (getBurnMode(stack)) {
            case 1 -> 3.0F;
            case 2 -> 5.0F;
            default -> 0.0F;
        };
    }

    /** Smoldering of a struck mob lasts five seconds in both modes. */
    public static int getSmolderTicks(ItemStack stack) {
        int mode = getBurnMode(stack);
        return mode == 1 || mode == 2 ? 100 : 0;
    }

    /** Health lost per smoldering second. */
    public static float getSmolderDamage(ItemStack stack) {
        return switch (getBurnMode(stack)) {
            case 1 -> 1.0F;
            case 2 -> 2.0F;
            default -> 0.0F;
        };
    }
}
