package com.voltyx.efw.init;

import com.voltyx.efw.EFW;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * No magic in a realistic setting: their crafting is removed (kubejs/server_scripts/efw_remove_offsetting_recipes.js), and the
 * brewing stands and enchanting tables generated in old structures cannot be used either; the anvil does not put enchanted books
 * or enchantments from another item onto gear (repairs and renaming still work, the grindstone still takes enchantments off).
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class SettingRules {
    private SettingRules() {
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock e) {
        var block = e.getLevel().getBlockState(e.getPos()).getBlock();
        if (block instanceof BrewingStandBlock || block instanceof EnchantmentTableBlock) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            if (!e.getLevel().isClientSide() && e.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
                e.getEntity().displayClientMessage(Component.translatable(block instanceof BrewingStandBlock
                        ? "message.efw.no_brewing" : "message.efw.no_enchanting"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent e) {
        var right = e.getRight();
        if (right.getItem() instanceof EnchantedBookItem || (right.isEnchanted() && !e.getLeft().isEmpty() && right.getItem() == e.getLeft().getItem())) {
            e.setCanceled(true);
        }
    }
}
