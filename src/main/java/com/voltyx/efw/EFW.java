package com.voltyx.efw;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(EFW.MOD_ID)
public class EFW {
    public static final String MOD_ID = "efw";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public EFW() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::commonSetup);
        bus.addListener(this::registerCapabilities);
        com.voltyx.efw.init.EfwContent.register(bus);
        com.voltyx.efw.network.EfwNetwork.init();
        com.voltyx.efw.sins.SinContent.register(bus);
        com.voltyx.efw.sins.SinNetwork.init();
        com.voltyx.efw.notes.NotesContent.register(bus);
        com.voltyx.efw.doll.DollContent.register(bus);
        com.voltyx.efw.zone.QuestZones.register(bus);
        com.voltyx.efw.tech.TechContent.register(bus);
        com.voltyx.efw.placed.PlacedContent.register(bus);
        com.voltyx.efw.hook.HookContent.register(bus);
        com.voltyx.efw.init.RemovedItems.register(bus);
        MinecraftForge.EVENT_BUS.register(com.voltyx.efw.init.RemovedItems.Events.class);
        bus.register(com.voltyx.efw.init.RemovedItems.ModEvents.class);
        if (net.minecraftforge.fml.ModList.get().isLoaded("survival_instinct")) {
            MinecraftForge.EVENT_BUS.register(com.voltyx.efw.compat.HazmatEffects.class);
        }
        com.voltyx.efw.gender.main.WildfireGender.init(bus);
        com.voltyx.efw.gender.main.config.GeneralClientConfig.register();
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.voltyx.efw.client.eat.EatClient.init(bus));
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> com.voltyx.efw.client.LogSpamFilter::install);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                com.voltyx.efw.stamina.EfwConfig.COMMON_SPEC, "efw-stamina.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                com.voltyx.efw.stamina.EfwConfig.CLIENT_SPEC, "efw-stamina-client.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                com.voltyx.efw.client.LockOnConfig.SPEC, "efw-lockon.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                com.voltyx.efw.client.dark.DarkGui.SPEC, "efw-darkgui.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                com.voltyx.efw.client.autohud.AutoHudConfig.SPEC, "efw-autohud.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                com.voltyx.efw.sins.SevenConfig.SPEC, "efw-seven.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                com.voltyx.efw.client.hearts.HeartsConfig.SPEC, "efw-hearts.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                com.voltyx.efw.headshot.HeadshotConfig.SPEC, "efw-headshots.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                com.voltyx.efw.heartbeat.HeartbeatConfig.SPEC, "efw-heartbeat.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                com.voltyx.efw.speech.SpeechConfig.SPEC, "efw-speech.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                com.voltyx.efw.doll.DollConfig.COMMON_SPEC, "efw-doll.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                com.voltyx.efw.doll.DollConfig.CLIENT_SPEC, "efw-doll-client.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                com.voltyx.efw.tech.DeviceConfig.SPEC, "efw-devices.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                com.voltyx.efw.placed.PlacedConfig.SPEC, "efw-placed.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                com.voltyx.efw.loot.LootConfig.SPEC, "efw-looting.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                com.voltyx.efw.coth.CothConfig.SPEC, "efw-coth.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                com.voltyx.efw.gore.ModConfigurationClient.SPEC, "efw-gore.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                com.voltyx.efw.gear.KawaiiEarsConfig.SPEC, "efw-ears.toml");
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
            if (net.minecraftforge.fml.ModList.get().isLoaded("tacz")) {
                MinecraftForge.EVENT_BUS.register(com.voltyx.efw.gear.client.KawaiiEarsTacz.class);
            }
            if (net.minecraftforge.fml.ModList.get().isLoaded("locks")) {
                MinecraftForge.EVENT_BUS.register(com.voltyx.efw.locks.client.LockTooltip.class);
            }
        });
        if (net.minecraftforge.fml.ModList.get().isLoaded("tacz")) {
            MinecraftForge.EVENT_BUS.register(com.voltyx.efw.headshot.TaczHeadshots.class);
            MinecraftForge.EVENT_BUS.register(com.voltyx.efw.sins.stat.SinTaczHooks.class);
            MinecraftForge.EVENT_BUS.register(com.voltyx.efw.tacz.BulletFire.class);
            MinecraftForge.EVENT_BUS.register(com.voltyx.efw.hook.HookFinisherTacz.class);
        }

        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("EFW mod initialized by voltyx / neko!");
    }

    private void registerCapabilities(net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent event) {
        event.register(com.voltyx.efw.stamina.PlayerData.class);
        event.register(com.voltyx.efw.sins.SinData.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        com.voltyx.efw.notes.NotesConfig.load();
        LOGGER.info("EFW common setup complete.");
    }
}
