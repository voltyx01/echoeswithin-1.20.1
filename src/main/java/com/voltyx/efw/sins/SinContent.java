package com.voltyx.efw.sins;

import com.voltyx.efw.EFW;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Items and sounds of the SEVEN progression: the level-up page (DPOR) and the five manuals. */
public final class SinContent {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EFW.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, EFW.MOD_ID);

    /** Manual categories; the index is the slot in {@link SinData#getLoreBooksProgress()}. */
    public enum ManualType {
        MELEE(0, "manual_melee"),
        FIREARMS(1, "manual_firearms"),
        MEDS(2, "manual_meds"),
        LOCKPICK(3, "manual_lockpick"),
        TOOLS(4, "manual_tools");

        public final int categoryIndex;
        public final String id;

        ManualType(int categoryIndex, String id) {
            this.categoryIndex = categoryIndex;
            this.id = id;
        }
    }

    public static final RegistryObject<Item> DPOR = ITEMS.register("dpor", DporItem::new);
    public static final RegistryObject<Item> MANUAL_MELEE = ITEMS.register("manual_melee", () -> new ManualItem(ManualType.MELEE));
    public static final RegistryObject<Item> MANUAL_FIREARMS = ITEMS.register("manual_firearms", () -> new ManualItem(ManualType.FIREARMS));
    public static final RegistryObject<Item> MANUAL_MEDS = ITEMS.register("manual_meds", () -> new ManualItem(ManualType.MEDS));
    public static final RegistryObject<Item> MANUAL_LOCKPICK = ITEMS.register("manual_lockpick", () -> new ManualItem(ManualType.LOCKPICK));
    public static final RegistryObject<Item> MANUAL_TOOLS = ITEMS.register("manual_tools", () -> new ManualItem(ManualType.TOOLS));

    public static final RegistryObject<SoundEvent> SKILL_UPGRADE = sound("skill.upgrade");
    public static final RegistryObject<SoundEvent> MENU_SWOOSH = sound("menu.swoosh");
    public static final RegistryObject<SoundEvent> MENU_SWOOSH_INVERT = sound("menu.swooshinvert");
    public static final RegistryObject<SoundEvent> FLOWER_MENU = sound("flowermenu");
    public static final RegistryObject<SoundEvent> FLOWER_MENU_0 = sound("flowermenu0");
    public static final RegistryObject<SoundEvent> FLOWER_MENU_ENTER = sound("flowermenuenter");
    public static final RegistryObject<SoundEvent> NOTES = sound("notes");
    public static final RegistryObject<SoundEvent> ITEM_SOUND = sound("itemsound");
    public static final RegistryObject<SoundEvent> DIARY_OPEN = sound("diaryopen");

    private SinContent() {
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EFW.MOD_ID, name)));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(SinContent::onCreativeTab);
    }

    public static Item manualForCategory(int categoryIndex) {
        return switch (categoryIndex) {
            case 0 -> MANUAL_MELEE.get();
            case 1 -> MANUAL_FIREARMS.get();
            case 2 -> MANUAL_MEDS.get();
            case 3 -> MANUAL_LOCKPICK.get();
            case 4 -> MANUAL_TOOLS.get();
            default -> null;
        };
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(DPOR);
            event.accept(MANUAL_MELEE);
            event.accept(MANUAL_FIREARMS);
            event.accept(MANUAL_MEDS);
            event.accept(MANUAL_LOCKPICK);
            event.accept(MANUAL_TOOLS);
        }
    }

    /** Defiled Page of Revelation: consumed by a sin level-up. */
    public static class DporItem extends Item {
        DporItem() {
            super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
        }

        @Override
        public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
            return true;
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.efw.dpor.consumable").withStyle(ChatFormatting.GRAY));
        }
    }

    /** A manual: studying it (X over the item in the inventory) raises its category. */
    public static class ManualItem extends Item {
        public final ManualType type;

        ManualItem(ManualType type) {
            super(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
            this.type = type;
        }
    }
}
