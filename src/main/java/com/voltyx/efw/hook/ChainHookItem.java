package com.voltyx.efw.hook;

import com.voltyx.efw.compat.TaczCompat;
import com.voltyx.efw.stamina.PlayerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A chain with a hook, worn in the off hand. Holding the use key swings it round (the longer, the farther it flies, full after a
 * second); letting go throws the hook where the crosshair is. It only catches creatures: a light one is dragged in, a heavy one drags
 * the thrower to it (ChainHookEntity). Not with a gun in the main hand: there the use key aims.
 */
public class ChainHookItem extends Item {
    /** Ticks of swinging for the full throw. */
    public static final int FULL_CHARGE = 20;
    /** Shorter swings do not throw. */
    private static final int MIN_CHARGE = 5;
    private static final double MIN_SPEED = 1.4;
    private static final double MAX_SPEED = 2.8;
    private static final int COOLDOWN_TICKS = 100;
    /** Stamina of a throw (the roll takes 4 by default). */
    private static final double STAMINA_COST = 5.0;

    public ChainHookItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /**
     * How far round the swung chain has gone after {@code ticks} of swinging: sped up from the start until the full swing. The
     * renderers draw it from this, the whistle sounds once a turn.
     */
    public static float swingAngle(float ticks) {
        float charge = Mth.clamp(ticks / FULL_CHARGE, 0.0F, 1.0F);
        return 0.55F * ticks + 0.35F * Math.min(ticks, FULL_CHARGE) * charge;
    }

    /** The chain stays in the off hand with a two-handed weapon (the melee manual, Stat.HOOK_TWO_HANDED). */
    public static boolean twoHandedGrip(Player player) {
        return com.voltyx.efw.sins.stat.SinStats.value(player, com.voltyx.efw.sins.stat.Stat.HOOK_TWO_HANDED) >= 1.0;
    }

    /** The second run after the blow (the melee manual's last volume, Stat.HOOK_COMBO). */
    public static boolean combo(Player player) {
        return com.voltyx.efw.sins.stat.SinStats.value(player, com.voltyx.efw.sins.stat.Stat.HOOK_COMBO) >= 1.0;
    }

    /** The shotgun finish after the combo: the combo and the firearms manual read to HookFinisher.FIREARMS_RANK. */
    public static boolean finisher(Player player) {
        com.voltyx.efw.sins.SinData data = com.voltyx.efw.sins.SinData.get(player);
        return combo(player) && data != null
                && data.manualRank(com.voltyx.efw.sins.SinContent.ManualType.FIREARMS.categoryIndex) >= HookFinisher.FIREARMS_RANK;
    }

    /** Tricks of the chain learnt: the two-handed grip, the combo, the finish. */
    public static int tricksLearnt(Player player) {
        return (twoHandedGrip(player) ? 1 : 0) + (combo(player) ? 1 : 0) + (finisher(player) ? 1 : 0);
    }

    /** Two tricks learnt or more: the chain comes back to the hand 40% sooner. */
    private static int cooldownTicks(Player player) {
        return tricksLearnt(player) >= 2 ? Math.round(COOLDOWN_TICKS * 0.6F) : COOLDOWN_TICKS;
    }

    /** Turns of the swing counted from half a turn: the whistles fall on these. */
    private static int turns(int ticks) {
        return (int) Math.floor((swingAngle(ticks) + Mth.PI) / Mth.TWO_PI);
    }

    /** The chain is used from the off hand only, and never with a gun in the main hand. */
    public static boolean canSwing(Player player, InteractionHand hand) {
        return hand == InteractionHand.OFF_HAND && !TaczCompat.isGun(player.getMainHandItem()) && !busyHand(player);
    }

    /**
     * The main hand is eating, drinking, bandaging, throwing or drawing something: the use key belongs to that (food, the medicines and
     * grenades of Lesraisins' tactical, bows, shields in use...), the chain stays out of it.
     */
    private static boolean busyHand(Player player) {
        if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            return true;
        }
        ItemStack main = player.getMainHandItem();
        if (main.isEmpty()) {
            return false;
        }
        if (main.isEdible()) {
            return true;
        }
        switch (main.getUseAnimation()) {
            case EAT, DRINK, BOW, CROSSBOW, SPEAR, SPYGLASS, TOOT_HORN, BRUSH -> {
                return true;
            }
            default -> {
            }
        }
        for (Class<?> c = main.getItem().getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Class<?> i : c.getInterfaces()) {
                String n = i.getName();
                if (n.equals("me.xjqsh.lrtactical.api.item.IConsumable") || n.equals("me.xjqsh.lrtactical.api.item.IThrowable")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canSwing(player, hand) || ChainHookEntity.activeHook(player) != null) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        int used = getUseDuration(stack) - remaining;
        // the chain whistles as it starts round and then once a turn, half a turn after the start (the first full turn is slow);
        // the swinger hears it from his own client at once, the others from the server (playSound with him left out)
        boolean whistle = used == 1 || turns(used) != turns(used - 1);
        if (whistle && entity instanceof Player player) {
            float pitch = 0.8F + 0.4F * Math.min(1.0F, used / (float) FULL_CHARGE);
            level.playSound(player, entity.getX(), entity.getY(), entity.getZ(), HookSounds.swing(), SoundSource.PLAYERS, 0.5F, pitch);
        }
        if (entity instanceof Player player && !canSwing(player, player.getUsedItemHand())) {
            player.stopUsingItem();
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        int used = getUseDuration(stack) - remaining;
        if (!(entity instanceof Player thrower) || used < MIN_CHARGE || ChainHookEntity.activeHook(thrower) != null) {
            return;
        }
        float charge = Mth.clamp(used / (float) FULL_CHARGE, 0.0F, 1.0F);
        if (level.isClientSide) {
            // the thrower hears his throw at once; the server plays it for the others
            PlayerData mine = PlayerData.get(thrower);
            if (thrower.isCreative() || mine == null || mine.stamina >= STAMINA_COST) {
                level.playSound(thrower, thrower.getX(), thrower.getY(), thrower.getZ(), HookSounds.whoosh(), SoundSource.PLAYERS, 1.0F,
                        0.9F + 0.2F * charge);
            }
            return;
        }
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        PlayerData data = PlayerData.get(player);
        if (data != null && !player.isCreative()) {
            if (data.stamina < STAMINA_COST) {
                return;
            }
            data.stamina -= STAMINA_COST;
            data.regenDelay = Math.max(data.regenDelay, com.voltyx.efw.stamina.EfwConfig.DASH_REGEN_DELAY.get());
        }
        Vec3 look = player.getLookAngle();
        ChainHookEntity hook = new ChainHookEntity(level, player);
        hook.setDeltaMovement(look.scale(MIN_SPEED + (MAX_SPEED - MIN_SPEED) * charge));
        level.addFreshEntity(hook);
        level.playSound(player, player.getX(), player.getY(), player.getZ(), HookSounds.whoosh(), SoundSource.PLAYERS, 1.0F, 0.9F + 0.2F * charge);
        player.getCooldowns().addCooldown(this, cooldownTicks(player));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.efw.chain_hook.desc"));
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.efw.chain_hook.use_title"));
            tooltip.add(Component.translatable("tooltip.efw.chain_hook.use_desc"));
        } else {
            tooltip.add(Component.translatable("tooltip.efw.chain_hook.use_shift"));
        }
        if (level != null && level.isClientSide) {
            com.voltyx.efw.hook.client.HookTooltip.appendTricks(tooltip); // what has been learnt, and what it takes
        }
    }
}
