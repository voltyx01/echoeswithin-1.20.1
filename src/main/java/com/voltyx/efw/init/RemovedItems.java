package com.voltyx.efw.init;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.voltyx.efw.EFW;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Items that stay registered (other mods and vanilla code refer to them) but are never obtainable in this pack, each one becoming a
 * kept item wherever it would turn up:
 * <ul>
 * <li>the gold, diamond and netherite locks and picks of Locks Reforged: only wood, copper, iron and steel are left (generated chests
 * only pick those, config/locks-common.toml);</li>
 * <li>diamond and netherite armour (and the diamond horse armour): fantasy plate that shrugs off bullets has no place among the
 * tactical gear; iron is the most a player finds of vanilla armour, the rest of the protection comes from the gun mods.</li>
 * </ul>
 * Their recipes (smithing included) are removed and REI hides them (kubejs efw_removed_items.js). Here: left out of the creative tab,
 * trades with them dropped, generated loot gets the replacement (loot modifier efw:removed_items), mobs wearing them (Improved Mobs
 * dresses mobs up) get the replacement too, so it is what they drop. SRP's Book of Vengeance goes the same way: it is magic, the chain
 * hook does what it did. Locks are never enchanted either: the enchanting table and anvil
 * enchanting are blocked by SettingRules, loot and trades lose their enchantments here.
 */
public final class RemovedItems {
    private static final Map<ResourceLocation, ResourceLocation> REPLACED = Map.ofEntries(
            Map.entry(locks("gold_lock"), locks("copper_lock")),
            Map.entry(locks("diamond_lock"), locks("steel_lock")),
            Map.entry(locks("netherite_lock"), locks("steel_lock")),
            Map.entry(locks("gold_lock_pick"), locks("copper_lock_pick")),
            Map.entry(locks("diamond_lock_pick"), locks("steel_lock_pick")),
            Map.entry(locks("netherite_lock_pick"), locks("steel_lock_pick")),
            Map.entry(mc("diamond_helmet"), mc("iron_helmet")),
            Map.entry(mc("diamond_chestplate"), mc("iron_chestplate")),
            Map.entry(mc("diamond_leggings"), mc("iron_leggings")),
            Map.entry(mc("diamond_boots"), mc("iron_boots")),
            Map.entry(mc("netherite_helmet"), mc("iron_helmet")),
            Map.entry(mc("netherite_chestplate"), mc("iron_chestplate")),
            Map.entry(mc("netherite_leggings"), mc("iron_leggings")),
            Map.entry(mc("netherite_boots"), mc("iron_boots")),
            Map.entry(mc("diamond_horse_armor"), mc("iron_horse_armor")),
            // SRP's Book of Vengeance is magic: its throw and pull became the chain hook
            Map.entry(new ResourceLocation("srparasites", "book_of_vengeance"), new ResourceLocation(EFW.MOD_ID, "chain_hook")));

    private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, EFW.MOD_ID);

    static {
        LOOT_MODIFIERS.register("removed_items", Modifier.CODEC);
    }

    private RemovedItems() {
    }

    public static void register(IEventBus modBus) {
        LOOT_MODIFIERS.register(modBus);
    }

    private static ResourceLocation locks(String path) {
        return new ResourceLocation("locks", path);
    }

    private static ResourceLocation mc(String path) {
        return new ResourceLocation("minecraft", path);
    }

    public static boolean isRemoved(ItemStack stack) {
        return REPLACED.containsKey(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    private static boolean isLock(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && key.getNamespace().equals("locks") && key.getPath().endsWith("_lock");
    }

    /** A removed item becomes its replacement, a lock loses its enchantments. */
    private static ItemStack sanitize(ItemStack stack) {
        ResourceLocation replacement = REPLACED.get(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        if (replacement != null) {
            stack = new ItemStack(ForgeRegistries.ITEMS.getValue(replacement), stack.getCount());
        }
        if (isLock(stack) && stack.isEnchanted()) {
            stack.removeTagKey("Enchantments");
        }
        return stack;
    }

    /** Forge bus. */
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onVillagerTrades(VillagerTradesEvent e) {
            e.getTrades().values().forEach(Events::filter);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onWandererTrades(WandererTradesEvent e) {
            filter(e.getGenericTrades());
            filter(e.getRareTrades());
        }

        /** The offer is only known once it is made: a removed item gives no offer (vanilla skips nulls), a lock comes plain. */
        private static void filter(List<VillagerTrades.ItemListing> listings) {
            listings.replaceAll(listing -> (trader, random) -> {
                MerchantOffer offer = listing.getOffer(trader, random);
                if (offer == null || isRemoved(offer.getResult())) {
                    return null;
                }
                if (isLock(offer.getResult())) {
                    offer.getResult().removeTagKey("Enchantments");
                }
                return offer;
            });
        }

        /** Mobs spawned (or loaded) in removed armour. */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onJoin(EntityJoinLevelEvent e) {
            if (!e.getLevel().isClientSide() && e.getEntity() instanceof Mob mob) {
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    redress(mob, slot, mob.getItemBySlot(slot));
                }
            }
        }

        /** Equipment given later (Improved Mobs dresses mobs up after they have spawned). */
        @SubscribeEvent
        public static void onEquipmentChange(LivingEquipmentChangeEvent e) {
            if (e.getEntity() instanceof Mob mob && !(e.getEntity() instanceof Player) && !mob.level().isClientSide()) {
                redress(mob, e.getSlot(), e.getTo());
            }
        }

        private static void redress(Mob mob, EquipmentSlot slot, ItemStack worn) {
            if (!worn.isEmpty() && isRemoved(worn)) {
                ItemStack replacement = sanitize(worn);
                replacement.setDamageValue(Math.min(worn.getDamageValue(), Math.max(0, replacement.getMaxDamage() - 1)));
                mob.setItemSlot(slot, replacement);
            }
        }
    }

    /** Mod bus. */
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onCreativeTab(BuildCreativeModeTabContentsEvent e) {
            List<ItemStack> removed = new ArrayList<>();
            for (var entry : e.getEntries()) {
                if (isRemoved(entry.getKey())) {
                    removed.add(entry.getKey());
                }
            }
            removed.forEach(stack -> e.getEntries().remove(stack));
        }
    }

    /** Every generated loot: see {@link #sanitize}. */
    public static final class Modifier extends LootModifier {
        static final Supplier<Codec<Modifier>> CODEC = Suppliers.memoize(() ->
                RecordCodecBuilder.create(i -> codecStart(i).apply(i, Modifier::new)));

        Modifier(LootItemCondition[] conditions) {
            super(conditions);
        }

        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
            loot.replaceAll(RemovedItems::sanitize);
            return loot;
        }

        @Override
        public Codec<? extends IGlobalLootModifier> codec() {
            return CODEC.get();
        }
    }
}
