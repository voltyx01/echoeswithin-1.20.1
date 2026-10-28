package com.voltyx.efw.init;

import com.voltyx.efw.EFW;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sound, effect and item of the stamina system (ported 1.12 mwccf:dash sound, mwccf:energy_boost potion, mwccf:redbull). */
public final class EfwContent {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, EFW.MOD_ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, EFW.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EFW.MOD_ID);

    public static final RegistryObject<SoundEvent> DASH_SOUND = SOUNDS.register("dash",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EFW.MOD_ID, "dash")));
    public static final RegistryObject<SoundEvent> NVG_TOGGLE = SOUNDS.register("nvg.toggle",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EFW.MOD_ID, "nvg.toggle")));
    /** Heard by the player coming back after a death. */
    public static final RegistryObject<SoundEvent> RESPAWN = SOUNDS.register("respawn",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EFW.MOD_ID, "respawn")));
    public static final RegistryObject<MobEffect> ENERGY_BOOST = EFFECTS.register("energy_boost",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x0099CC) {
            });
    /** Given by amoxicillin (lrtactical): cures the Call of the Hive and keeps it away while it lasts. */
    public static final RegistryObject<MobEffect> ANTIBIOTIC = EFFECTS.register("antibiotic",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xE6E2D3) {
            });
    /** Given by the sedatives (lrtactical): the pulse settles and its spikes are five times smaller (HeartbeatManager); not against the doll. */
    public static final RegistryObject<MobEffect> SEDATED = EFFECTS.register("sedated",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x9FB8C9) {
            });
    public static final RegistryObject<Item> REDBULL = ITEMS.register("redbull", ItemRedbull::new);
    /** The Tagilla helmet of Fracture Point (that mod is gone from the pack). */
    public static final RegistryObject<Item> TAGILLA_MASK = ITEMS.register("tagilla_mask", com.voltyx.efw.gear.TagillaMaskItem::new);
    /** Cute animated ears (the mwccf Kawaii Ears). */
    public static final RegistryObject<Item> KAWAII_EARS = ITEMS.register("kawaii_ears", com.voltyx.efw.gear.KawaiiEarsItem::new);
    /** A worn wedding ring (ring curio slot): shows the weak spots of strong parasites, bigger parasite heads for its wearer. */
    public static final RegistryObject<Item> WORN_RING = ITEMS.register("worn_ring", com.voltyx.efw.gear.WornRingItem::new);
    public static final RegistryObject<Item> WORN_OLYMPIC = ITEMS.register("worn_olympic", com.voltyx.efw.gear.WornOlympicItem::new);
    public static final RegistryObject<Item> FLAMING_CLOTH = ITEMS.register("flaming_cloth", ItemFlamingCloth::new);

    private EfwContent() {
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
        EFFECTS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(EfwContent::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(REDBULL);
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(TAGILLA_MASK);
            event.accept(KAWAII_EARS);
            event.accept(WORN_RING);
            event.accept(WORN_OLYMPIC);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(FLAMING_CLOTH);
        }
    }
}
