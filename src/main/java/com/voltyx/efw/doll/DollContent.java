package com.voltyx.efw.doll;

import com.voltyx.efw.EFW;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Saya's plush doll: the item, the "Feeling of Guilt" effect of its ability and the sounds (mwccf:doll, mwccf:saya_buff). */
public final class DollContent {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EFW.MOD_ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, EFW.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, EFW.MOD_ID);

    public static final RegistryObject<Item> DOLL = ITEMS.register("doll", DollItem::new);
    public static final RegistryObject<MobEffect> SAYA_BUFF = EFFECTS.register("saya_buff", SayaBuffEffect::new);
    public static final RegistryObject<SoundEvent> DOLL_UP = sound("doll.up");
    public static final RegistryObject<SoundEvent> DOLL_DOWN = sound("doll.down");
    public static final RegistryObject<SoundEvent> DOLL_ACTIVATE = sound("doll.activate");

    private DollContent() {
    }

    private static RegistryObject<SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EFW.MOD_ID, id)));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(DollContent::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(DOLL);
        }
    }
}
