package com.voltyx.efw.hook;

import com.voltyx.efw.EFW;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The chain with a hook: the item worn in the off hand and the hook it throws. */
public final class HookContent {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EFW.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EFW.MOD_ID);

    public static final RegistryObject<Item> CHAIN_HOOK = ITEMS.register("chain_hook", ChainHookItem::new);
    public static final RegistryObject<EntityType<ChainHookEntity>> HOOK = ENTITIES.register("chain_hook",
            () -> EntityType.Builder.<ChainHookEntity>of(ChainHookEntity::new, MobCategory.MISC).sized(0.3F, 0.3F)
                    .clientTrackingRange(8).updateInterval(1).noSummon().build("chain_hook"));

    private HookContent() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(HookContent::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(CHAIN_HOOK);
        }
    }
}
