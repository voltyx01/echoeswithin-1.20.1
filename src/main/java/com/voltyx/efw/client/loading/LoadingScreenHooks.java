package com.voltyx.efw.client.loading;

import com.voltyx.efw.EFW;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Swaps the vanilla world loading screens for the efw ones and resets the item pick once the player is in the world. */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class LoadingScreenHooks {
    /** The plain message screens of the world loading that get the efw picture ("Reading world data..."). */
    private static final java.util.Set<String> MESSAGE_KEYS = java.util.Set.of("selectWorld.data_read");

    private LoadingScreenHooks() {
    }

    @SubscribeEvent
    public static void onOpening(ScreenEvent.Opening event) {
        Object screen = event.getNewScreen();
        if (screen != null && screen.getClass() == GenericDirtMessageScreen.class
                && ((GenericDirtMessageScreen) screen).getTitle().getContents() instanceof TranslatableContents tc
                && MESSAGE_KEYS.contains(tc.getKey())) {
            event.setNewScreen(new EfwMessageScreen(((GenericDirtMessageScreen) screen).getTitle()));
        } else if (screen != null && screen.getClass() == ReceivingLevelScreen.class) {
            event.setNewScreen(new EfwReceivingLevelScreen());
        } else if (screen != null && screen.getClass() == LevelLoadingScreen.class) {
            // field progressListener (SRG f_96138_); constructor argument is not kept anywhere else
            StoringChunkProgressListener listener = ObfuscationReflectionHelper.getPrivateValue(LevelLoadingScreen.class,
                    (LevelLoadingScreen) screen, "f_96138_");
            if (listener != null) {
                event.setNewScreen(new EfwLevelLoadingScreen(listener));
            }
        } else if (screen != null && screen.getClass() == ProgressScreen.class) {
            // The "Joining world" screen, and the blank one vanilla puts up while the world is cleared and the integrated server starts
            // (a second of plain dirt right after "Reading world data"). Other progress screens are driven through the instance their
            // creator holds.
            ProgressScreen original = (ProgressScreen) screen;
            Component header = ObfuscationReflectionHelper.getPrivateValue(ProgressScreen.class, original, "f_96506_");
            if (header == null || header.getContents() instanceof TranslatableContents tc && tc.getKey().equals("connect.joining")) {
                Component stage = ObfuscationReflectionHelper.getPrivateValue(ProgressScreen.class, original, "f_96507_");
                boolean clear = Boolean.TRUE.equals(ObfuscationReflectionHelper.getPrivateValue(ProgressScreen.class, original, "f_169362_"));
                event.setNewScreen(new EfwProgressScreen(clear, header, stage));
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !LoadingCard.hasPicked()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean playing = mc.player != null && mc.level != null && mc.player.tickCount > 10 && mc.screen == null;
        if (playing || mc.screen instanceof TitleScreen) {
            LoadingCard.reset();
        }
    }
}
