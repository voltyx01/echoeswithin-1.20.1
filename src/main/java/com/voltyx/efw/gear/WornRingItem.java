package com.voltyx.efw.gear;

import com.voltyx.efw.tech.CuriosCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A worn wedding ring, worn in the ring curio slot. Its wearer sees the weak spot of the parasite aimed at with a gun
 * ({@link com.voltyx.efw.gear.client.WornRingGlints}), and the heads of parasites are bigger for the wearer's shots
 * ({@link com.voltyx.efw.headshot.HeadshotManager#getHeadBox(net.minecraft.world.entity.LivingEntity, Entity)}).
 */
public class WornRingItem extends Item {
    public WornRingItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static boolean wears(Player player) {
        return !CuriosCompat.findFirst(player, s -> s.getItem() instanceof WornRingItem).isEmpty();
    }

    public static boolean isParasite(Entity entity) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id != null && id.getNamespace().equals("srparasites");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.efw.worn_ring.desc"));
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.efw.worn_ring.effect_title"));
            tooltip.add(Component.translatable("tooltip.efw.worn_ring.effect_desc"));
        } else {
            tooltip.add(Component.translatable("tooltip.efw.worn_ring.effect_shift"));
        }
    }
}
