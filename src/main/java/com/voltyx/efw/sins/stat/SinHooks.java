package com.voltyx.efw.sins.stat;

import com.voltyx.efw.EFW;
import com.voltyx.efw.blood.BloodEvents;
import com.voltyx.efw.init.EfwContent;
import com.voltyx.efw.mixin.MobEffectInstanceAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** The server side of SEVEN: the hits, heals, kills, meals, drugs, sleep and attributes the stats change. */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class SinHooks {
    private static final Random RNG = new Random();
    private static final Map<Stat, AttrLink> ATTRIBUTES = new EnumMap<>(Stat.class);
    /** Effect ids that count as stimulants (their time grows with effect.stim and they fire the stim trigger). */
    private static final String[] STIM_WORDS = {"adrenaline", "morphine", "stim", "energy_boost", "painkiller", "panacea", "tonic", "elixir"};

    private record AttrLink(String attributeId, AttributeModifier.Operation op) {
    }

    static {
        ATTRIBUTES.put(Stat.HP_MAX, new AttrLink("minecraft:generic.max_health", AttributeModifier.Operation.MULTIPLY_BASE));
        ATTRIBUTES.put(Stat.MOVE_SPEED, new AttrLink("minecraft:generic.movement_speed", AttributeModifier.Operation.MULTIPLY_BASE));
        ATTRIBUTES.put(Stat.ATTACK_SPEED, new AttrLink("minecraft:generic.attack_speed", AttributeModifier.Operation.MULTIPLY_BASE));
        ATTRIBUTES.put(Stat.KNOCKBACK_DEAL, new AttrLink("minecraft:generic.attack_knockback", AttributeModifier.Operation.ADDITION));
        ATTRIBUTES.put(Stat.KNOCKBACK_RESIST, new AttrLink("minecraft:generic.knockback_resistance", AttributeModifier.Operation.ADDITION));
        ATTRIBUTES.put(Stat.ARMOR, new AttrLink("minecraft:generic.armor", AttributeModifier.Operation.ADDITION));
        ATTRIBUTES.put(Stat.REACH, new AttrLink("forge:block_reach", AttributeModifier.Operation.ADDITION));
        ATTRIBUTES.put(Stat.STEP, new AttrLink("forge:step_height_addition", AttributeModifier.Operation.ADDITION));
        ATTRIBUTES.put(Stat.HEAT_RESIST, new AttrLink("legendarysurvivaloverhaul:heat_resistance", AttributeModifier.Operation.ADDITION));
        ATTRIBUTES.put(Stat.COLD_RESIST, new AttrLink("legendarysurvivaloverhaul:cold_resistance", AttributeModifier.Operation.ADDITION));
        ATTRIBUTES.put(Stat.BODY_RESIST, new AttrLink("legendarysurvivaloverhaul:body_resistance", AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private SinHooks() {
    }

    // ---- every tick: runtime, attributes ---------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Player p = e.player;
        if (p.level().isClientSide && !p.isLocalPlayer()) {
            return;
        }
        SinRuntime.get(p).tick(p);
        if (!p.level().isClientSide && p.tickCount % 10 == 0) {
            applyAttributes(p);
        }
        if (p instanceof ServerPlayer sp && p.tickCount % 40 == 0) {
            com.voltyx.efw.sins.SinData data = com.voltyx.efw.sins.SinData.get(sp);
            if (data != null && !data.knowsDoll() && Cond.hasDoll(sp)) {
                data.learnDoll();
                com.voltyx.efw.sins.SinEvents.sync(sp);
            }
        }
    }

    private static void applyAttributes(Player p) {
        for (Map.Entry<Stat, AttrLink> link : ATTRIBUTES.entrySet()) {
            Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(link.getValue().attributeId));
            AttributeInstance inst = attr == null ? null : p.getAttribute(attr);
            if (inst == null) {
                continue;
            }
            Stat stat = link.getKey();
            double v = SinStats.value(p, stat);
            if (stat == Stat.BODY_RESIST) {
                v = Math.max(-0.9, v);
            }
            UUID id = UUID.nameUUIDFromBytes(("efw_seven_" + stat.id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            AttributeModifier old = inst.getModifier(id);
            if (old != null && Math.abs(old.getAmount() - v) < 1.0E-4) {
                continue;
            }
            if (old != null) {
                inst.removeModifier(id);
            }
            if (Math.abs(v) > 1.0E-4) {
                inst.addTransientModifier(new AttributeModifier(id, "SEVEN " + stat.id, v, link.getValue().op));
            }
        }
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }

    // ---- hits ---------------------------------------------------------------------------------------------------------------

    /** After the other mods (the doll, LSO): the last word on the amount. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        DamageSource source = e.getSource();
        if (source.getEntity() instanceof Player attacker && attacker != victim && source.getDirectEntity() == attacker
                && !BloodEvents.isFirearmDamage(source, attacker)) {
            e.setAmount(e.getAmount() * (float) SinStats.multAgainst(attacker, Stat.MELEE_DAMAGE, victim));
            SinStats.fire(attacker, Trigger.HIT_MELEE);
        }
        if (victim instanceof Player p) {
            LivingEntity from = source.getEntity() instanceof LivingEntity l ? l : null;
            float mult = (float) SinStats.multAgainst(p, Stat.DAMAGE_TAKEN, from);
            if (source.is(DamageTypeTags.IS_FALL)) {
                mult *= (float) SinStats.mult(p, Stat.DAMAGE_FALL);
            }
            e.setAmount(e.getAmount() * mult);
            if (e.getAmount() > 0) {
                SinRuntime.get(p).markHurt(p);
                SinStats.fire(p, Trigger.HURT);
            }
        }
    }

    /** A blow that would kill leaves one heart instead, while the effect that allows it is not resting. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getAmount() < p.getHealth()) {
            return;
        }
        List<SinEffect> used = new ArrayList<>();
        if (SinStats.value(p, Stat.CHEAT_DEATH, null, used) > 0 && p.getHealth() > 1.0f) {
            e.setAmount(p.getHealth() - 1.0f);
            SinStats.rest(p, used);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity().level().isClientSide || !(e.getSource().getEntity() instanceof ServerPlayer killer) || killer == e.getEntity()) {
            return;
        }
        SinStats.fire(killer, Trigger.KILL);
        if (e.getSource().getDirectEntity() == killer && !BloodEvents.isFirearmDamage(e.getSource(), killer)) {
            SinStats.fire(killer, Trigger.KILL_MELEE);
        }
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent e) {
        if (e.getEntity() instanceof Player p && !p.level().isClientSide) {
            e.setAmount(e.getAmount() * (float) SinStats.mult(p, Stat.HEAL_POWER));
        }
    }

    // ---- meals and drugs ------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onFinishUsing(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        ItemStack stack = e.getItem();
        FoodProperties food = stack.getItem().getFoodProperties(stack, p);
        if (food == null) {
            return;
        }
        double extra = SinStats.mult(p, Stat.FOOD_RESTORE) - 1.0;
        int nutrition = (int) Math.round(food.getNutrition() * extra);
        if (nutrition > 0) {
            p.getFoodData().eat(nutrition, food.getSaturationModifier());
        } else if (nutrition < 0) {
            p.getFoodData().setFoodLevel(Math.max(0, p.getFoodData().getFoodLevel() + nutrition));
        }
        SinStats.fire(p, Trigger.EAT);
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        MobEffectInstance inst = e.getEffectInstance();
        ResourceLocation key = ForgeRegistries.MOB_EFFECTS.getKey(inst.getEffect());
        if (key == null || inst.isInfiniteDuration()) {
            return;
        }
        Stat stat = null;
        if (inst.getEffect() == EfwContent.ANTIBIOTIC.get()) {
            stat = Stat.EFFECT_ANTIBIOTIC;
        } else if (isStim(key.getPath())) {
            stat = Stat.EFFECT_STIM;
        }
        if (stat == null) {
            return;
        }
        double mult = SinStats.mult(p, stat);
        if (Math.abs(mult - 1.0) > 1.0E-3) {
            ((MobEffectInstanceAccessor) inst).efw$setDuration(Math.max(1, (int) Math.round(inst.getDuration() * mult)));
        }
        if (stat == Stat.EFFECT_STIM) {
            SinStats.fire(p, Trigger.STIM);
        }
    }

    private static boolean isStim(String path) {
        for (String w : STIM_WORDS) {
            if (path.contains(w)) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !e.wakeImmediately()) {
            SinStats.fire(p, Trigger.WAKE);
        }
    }

    // ---- being seen ----------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onVisibility(LivingEvent.LivingVisibilityEvent e) {
        if (e.getEntity() instanceof Player p && !p.level().isClientSide) {
            e.modifyVisibility(SinStats.mult(p, Stat.VISIBILITY));
        }
    }

    // ---- looting ---------------------------------------------------------------------------------------------------------------

    /** A container was searched and opened: the search trigger, and maybe one more of something it holds. */
    public static void onSearched(ServerPlayer player, Level level, List<BlockPos> parts) {
        SinStats.fire(player, Trigger.SEARCH);
        double chance = SinStats.value(player, Stat.LOOT_BONUS);
        if (chance <= 0 || RNG.nextDouble() >= chance) {
            return;
        }
        List<ItemStack> growable = new ArrayList<>();
        for (BlockPos part : parts) {
            BlockEntity be = level.getBlockEntity(part);
            if (be instanceof Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    ItemStack s = c.getItem(i);
                    if (!s.isEmpty() && s.getCount() < s.getMaxStackSize()) {
                        growable.add(s);
                    }
                }
                be.setChanged();
            }
        }
        if (!growable.isEmpty()) {
            growable.get(RNG.nextInt(growable.size())).grow(1);
        }
    }

    // ---- wear ---------------------------------------------------------------------------------------------------------------

    /** Called by the ItemStack mixin: the damage a tool or weapon takes from this player's use. */
    public static int wear(ItemStack stack, ServerPlayer player, int amount) {
        if (amount <= 0) {
            return amount;
        }
        double mult = SinStats.mult(player, SinRuntime.isMeleeWeapon(stack) ? Stat.WEAR_WEAPON : Stat.WEAR_TOOL);
        double scaled = amount * mult;
        int whole = (int) Math.floor(scaled);
        return whole + (RNG.nextDouble() < scaled - whole ? 1 : 0);
    }
}
