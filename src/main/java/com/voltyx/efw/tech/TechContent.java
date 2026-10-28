package com.voltyx.efw.tech;

import com.voltyx.efw.EFW;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The terminal, the bodycam, the safe (registered as "antenna" like in 1.12), the old radio and their parts: the internet
 * module, the radio board, the screwdriver and the batteries.
 */
public final class TechContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, EFW.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EFW.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, EFW.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, EFW.MOD_ID);

    public static final RegistryObject<Block> TERMINAL = BLOCKS.register("terminal", TerminalBlock::new);
    public static final RegistryObject<Block> ANTENNA = BLOCKS.register("antenna", AntennaBlock::new);
    public static final RegistryObject<Block> OLD_RADIO = BLOCKS.register("old_radio", OldRadioBlock::new);

    public static final RegistryObject<BlockEntityType<TerminalBlockEntity>> TERMINAL_BE = BLOCK_ENTITIES.register("terminal",
            () -> BlockEntityType.Builder.of(TerminalBlockEntity::new, TERMINAL.get()).build(null));
    public static final RegistryObject<BlockEntityType<AntennaBlockEntity>> ANTENNA_BE = BLOCK_ENTITIES.register("antenna",
            () -> BlockEntityType.Builder.of(AntennaBlockEntity::new, ANTENNA.get()).build(null));
    public static final RegistryObject<BlockEntityType<OldRadioBlockEntity>> OLD_RADIO_BE = BLOCK_ENTITIES.register("old_radio",
            () -> BlockEntityType.Builder.of(OldRadioBlockEntity::new, OLD_RADIO.get()).build(null));

    public static final RegistryObject<Item> TERMINAL_ITEM = ITEMS.register("terminal", () -> new BlockItem(TERMINAL.get(), new Item.Properties()));
    public static final RegistryObject<Item> ANTENNA_ITEM = ITEMS.register("antenna", () -> new BlockItem(ANTENNA.get(), new Item.Properties()));
    public static final RegistryObject<Item> OLD_RADIO_ITEM = ITEMS.register("old_radio", () -> new BlockItem(OLD_RADIO.get(), new Item.Properties()));

    public static final RegistryObject<Item> INTERNET_MODULE = ITEMS.register("internet_module", () -> new Item(new Item.Properties()) {
        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.efw.internet_module"));
        }
    });
    public static final RegistryObject<Item> RADIO_BOARD = ITEMS.register("radio_board", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SCREWDRIVER = ITEMS.register("screwdriver", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> BATTERY = ITEMS.register("battery", () -> new BatteryItem(48000, false, 0));
    public static final RegistryObject<Item> LION_BATTERY_1 = ITEMS.register("lion_battery_1", () -> new BatteryItem(120000, true, 1));
    public static final RegistryObject<Item> LION_BATTERY_2 = ITEMS.register("lion_battery_2", () -> new BatteryItem(250000, true, 2));
    public static final RegistryObject<Item> LION_BATTERY_3 = ITEMS.register("lion_battery_3", () -> new BatteryItem(500000, true, 3));

    public static final RegistryObject<Item> BODYCAM = ITEMS.register("bodycam", BodycamItem::new);
    public static final RegistryObject<Item> PORTABLE_MAP = ITEMS.register("portable_map", PortableMapItem::new);

    public static final RegistryObject<SoundEvent> LOOT_PROGRESS = SOUNDS.register("loot.lootprog",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EFW.MOD_ID, "loot.lootprog")));

    private TechContent() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(TechContent::onCreativeTab);
        TechNetwork.init();
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(TERMINAL_ITEM);
            event.accept(ANTENNA_ITEM);
            event.accept(OLD_RADIO_ITEM);
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(INTERNET_MODULE);
            event.accept(RADIO_BOARD);
            event.accept(BATTERY);
            event.accept(LION_BATTERY_1);
            event.accept(LION_BATTERY_2);
            event.accept(LION_BATTERY_3);
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(SCREWDRIVER);
            event.accept(BODYCAM);
            event.accept(PORTABLE_MAP);
        }
    }
}
