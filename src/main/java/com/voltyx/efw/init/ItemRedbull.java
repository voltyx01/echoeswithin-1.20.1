package com.voltyx.efw.init;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Energy drink: no food value, gives Energy Boost (faster stamina regeneration, ignores the food limit) for a minute. */
public class ItemRedbull extends Item {
    public ItemRedbull() {
        super(new Item.Properties().stacksTo(2)
                .food(new FoodProperties.Builder().nutrition(0).saturationMod(0.0f).alwaysEat().build()));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            entity.addEffect(new MobEffectInstance(EfwContent.ENERGY_BOOST.get(), 1200, 0, false, false));
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
