package com.voltyx.efw.gear;

import com.voltyx.efw.tech.CuriosCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A worn track jacket, worn in the body curio slot. It is clothing, not armour: the wearer's skin itself gets the jacket on the body
 * and the arms, both layers, the hands and the neck staying their own ({@link com.voltyx.efw.gear.client.OlympicSkin}).
 */
public class WornOlympicItem extends Item {
    public WornOlympicItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static boolean wears(LivingEntity entity) {
        return !CuriosCompat.findFirst(entity, s -> s.getItem() instanceof WornOlympicItem).isEmpty();
    }

    /** Worn and shown: its Curios slot is not hidden (the skin gets the jacket only then; it keeps out the wet either way). */
    public static boolean shows(LivingEntity entity) {
        return !CuriosCompat.findVisible(entity, s -> s.getItem() instanceof WornOlympicItem).isEmpty();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.efw.worn_olympic.desc"));
    }
}
