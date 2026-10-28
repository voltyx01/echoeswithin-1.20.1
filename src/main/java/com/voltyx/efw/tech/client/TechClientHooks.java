package com.voltyx.efw.tech.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.notes.client.NotesClient;
import com.voltyx.efw.tech.AntennaBlockEntity;
import com.voltyx.efw.tech.BodycamEntry;
import com.voltyx.efw.tech.BodycamItem;
import com.voltyx.efw.tech.TechContent;
import com.voltyx.efw.tech.TechNetwork;
import com.voltyx.efw.tech.TerminalBlockEntity;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Client entry points of the tech blocks and the bodycam (called from common code through DistExecutor). */
public final class TechClientHooks {
    private TechClientHooks() {
    }

    public static void toggleTerminal(BlockPos pos, Direction facing, TerminalBlockEntity terminal) {
        if (TerminalCamera.isActive()) {
            TerminalCamera.close();
        } else {
            TerminalCamera.open(pos, facing, terminal);
        }
    }

    /** Sneak + use: the terminal shuts down and its session ends. */
    public static void shutdownTerminal() {
        if (TerminalCamera.isActive()) {
            TerminalCamera.close();
        }
        TerminalSession.get().reset();
    }

    public static void toggleAntenna(BlockPos pos, Direction facing, AntennaBlockEntity antenna) {
        if (AntennaCamera.isActive()) {
            AntennaCamera.close();
        } else {
            AntennaCamera.open(pos, facing, antenna);
        }
    }

    public static void startRadioLoot(BlockPos pos) {
        RadioLooting.start(pos);
    }

    public static void receiveCameras(List<BodycamEntry> cameras) {
        TerminalSession.get().setAvailableCameras(cameras);
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeEvents {
        private ForgeEvents() {
        }

        /** Leaving the world ends the console sessions. */
        @SubscribeEvent
        public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) {
            TerminalCamera.close();
            AntennaCamera.close();
            TerminalSession.get().reset();
        }

        /** X over a bodycam in any container toggles its power. */
        @SubscribeEvent
        public static void onKey(ScreenEvent.KeyPressed.Pre e) {
            if (e.getKeyCode() != GLFW.GLFW_KEY_X || !(e.getScreen() instanceof AbstractContainerScreen<?> screen) || NotesClient.typing(screen)) {
                return;
            }
            Slot slot = screen.getSlotUnderMouse();
            if (slot != null && slot.getItem().getItem() instanceof BodycamItem) {
                TechNetwork.sendToServer(new TechNetwork.ToggleBodycam(slot.index));
                e.setCanceled(true);
            }
        }
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerBlockEntityRenderer(TechContent.TERMINAL_BE.get(), TerminalRenderer::new);
            e.registerBlockEntityRenderer(TechContent.ANTENNA_BE.get(), AntennaRenderer::new);
            e.registerBlockEntityRenderer(TechContent.OLD_RADIO_BE.get(), OldRadioRenderer::new);
        }

        @SubscribeEvent
        public static void onLayers(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                PlayerRenderer renderer = e.getSkin(skin);
                if (renderer != null) {
                    renderer.addLayer(new BodycamLayer(renderer));
                }
            }
        }
    }
}
