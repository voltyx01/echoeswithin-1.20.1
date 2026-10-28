package com.voltyx.efw.gear;

import com.voltyx.efw.tech.CuriosCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Cute animated ears (port of the mwccf Kawaii Ears): worn in the head curio slot (not in the helmet slot). The ears are drawn by
 * {@link com.voltyx.efw.gear.client.KawaiiEarsLayer}; they move with the head and react to what the wearer does.
 */
public class KawaiiEarsItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public KawaiiEarsItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /** The ears worn by the entity, in a curio slot (never the helmet slot); EMPTY when it wears none. */
    public static ItemStack worn(LivingEntity entity) {
        // only drawn: a hidden Curios slot hides them
        return CuriosCompat.findVisible(entity, s -> s.getItem() instanceof KawaiiEarsItem);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.efw.kawaii_ears"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
