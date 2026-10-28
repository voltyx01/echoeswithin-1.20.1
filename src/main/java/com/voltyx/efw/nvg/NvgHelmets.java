package com.voltyx.efw.nvg;

import com.voltyx.efw.EFW;
import com.voltyx.efw.init.EfwContent;
import com.voltyx.efw.tech.BatteryHelper;
import com.voltyx.efw.tech.DeviceConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * Night vision helmets of Survival Instinct (the night vision goggles, the three hunter helmets and the heavy exo helmets) as
 * battery powered devices (port of SurvivalInstinctArmorHandler / PacketToggleNVG): they do nothing until switched on, the
 * battery installed in them drains while they are on, and the visor goes down and up smoothly (client side). The helmets of the
 * other mod keep giving their own constant night vision, which is denied here while the helmet is off.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class NvgHelmets {
    public static final String NBT_ACTIVE = "nv_active";
    private static final String HAD_NVG = "efw_had_nvg";

    private NvgHelmets() {
    }

    public static boolean isNvgHelmet(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        if (!name.endsWith("helmet")) {
            return false;
        }
        return name.contains("night_vision") || name.contains("hunter") || name.startsWith("exo_heavy");
    }

    public static int charge(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(BatteryHelper.NBT_CHARGE) ? tag.getInt(BatteryHelper.NBT_CHARGE) : 0;
    }

    /** Switched on and with charge. */
    public static boolean isActive(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(NBT_ACTIVE) && charge(stack) > 0;
    }

    public static void setActive(ItemStack stack, boolean on) {
        stack.getOrCreateTag().putBoolean(NBT_ACTIVE, on);
    }

    /** Server: the toggle key was pressed. */
    public static void toggle(ServerPlayer player) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!isNvgHelmet(helmet)) {
            return;
        }
        boolean current = isActive(helmet);
        if (!current && charge(helmet) <= 0) {
            setActive(helmet, false);
            return;
        }
        setActive(helmet, !current);
        if (current) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), EfwContent.NVG_TOGGLE.get(), SoundSource.PLAYERS, 0.8F,
                current ? 1.0F : 1.4F);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        boolean nvg = isNvgHelmet(helmet);
        boolean active = nvg && isActive(helmet);
        boolean had = player.getPersistentData().getBoolean(HAD_NVG);
        if (active) {
            if (player.tickCount % 20 == 0 && BatteryHelper.consumeCharge(helmet, DeviceConfig.NVG_DRAIN_PER_SECOND.get()) <= 0) {
                setActive(helmet, false);
                active = false;
                player.removeEffect(MobEffects.NIGHT_VISION);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), EfwContent.NVG_TOGGLE.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
                player.displayClientMessage(Component.translatable("tooltip.efw.battery.required"), true);
            }
            if (active) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, true, false));
                player.getPersistentData().putBoolean(HAD_NVG, true);
                return;
            }
        }
        if (had) {
            player.removeEffect(MobEffects.NIGHT_VISION);
            player.getPersistentData().remove(HAD_NVG);
        }
    }

    /** The helmets of the other mod give night vision all the time (80 ticks, no particles, no icon); while the helmet is off that is denied. */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        MobEffectInstance instance = event.getEffectInstance();
        LivingEntity entity = event.getEntity();
        if (instance.getEffect() != MobEffects.NIGHT_VISION || instance.getDuration() != 80 || instance.isAmbient() || instance.isVisible()) {
            return;
        }
        ItemStack helmet = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (isNvgHelmet(helmet) && !isActive(helmet)) {
            event.setResult(Event.Result.DENY);
        }
    }
}
