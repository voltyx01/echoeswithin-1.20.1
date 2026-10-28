package com.voltyx.efw.init;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Self-igniting cloth (ported mwccf:flaming_cloth): wraps a weapon and lights it at once in the enhanced soaked burn mode. */
public class ItemFlamingCloth extends Item {
    public ItemFlamingCloth() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.efw.fireweapon.flaming_cloth").withStyle(ChatFormatting.GOLD));
    }
}
