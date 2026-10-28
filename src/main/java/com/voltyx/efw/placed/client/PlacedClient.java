package com.voltyx.efw.placed.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.notes.client.InspectScreen;
import com.voltyx.efw.notes.client.NotesClient;
import com.voltyx.efw.placed.PlacedContent;
import com.voltyx.efw.placed.PlacedItemBlock;
import com.voltyx.efw.placed.PlacedItemBlockEntity;
import com.voltyx.efw.sins.SinContent;
import com.voltyx.efw.sins.client.ScreenTransition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Client side of the placed items: the settings screen, the inspect key and the renderer. */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class PlacedClient {
    private static BlockPos pendingConfig;
    private static int pendingTicks;

    private PlacedClient() {
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerBlockEntityRenderer(PlacedContent.PLACED_ITEM_BE.get(), PlacedItemRenderer::new);
        }
    }

    public static void openConfig(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof PlacedItemBlockEntity placed) {
            mc.setScreen(new PlacedItemConfigScreen(placed));
        }
    }

    /** The block entity of an item that was just put down arrives with the next chunk update: wait for it. */
    public static void openConfigWhenReady(BlockPos pos) {
        pendingConfig = pos;
        pendingTicks = 60;
    }

    public static void inspect(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SinContent.ITEM_SOUND.get(), 1.5f, 1.0f);
        }
        if (!stack.isEmpty()) {
            ScreenTransition.to(new InspectScreen(stack.copy(), null));
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || pendingConfig == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pendingConfig) instanceof PlacedItemBlockEntity placed && !placed.getStack().isEmpty()) {
            mc.setScreen(new PlacedItemConfigScreen(placed));
            pendingConfig = null;
        } else if (--pendingTicks <= 0) {
            pendingConfig = null;
        }
    }

    /** I over a placed item opens the inspect screen for it. */
    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getKey() != GLFW.GLFW_KEY_I || e.getAction() != GLFW.GLFW_PRESS || mc.screen != null || mc.level == null
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        if (mc.level.getBlockEntity(hit.getBlockPos()) instanceof PlacedItemBlockEntity placed) {
            ItemStack stack = placed.getStack();
            if (!stack.isEmpty() && !NotesClient.isBlacklisted(stack)) {
                ScreenTransition.to(new InspectScreen(stack.copy(), null));
            }
        }
    }

    /** No black outline around the item: the icon above it shows that it can be used. */
    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockState(e.getTarget().getBlockPos()).getBlock() instanceof PlacedItemBlock) {
            e.setCanceled(true);
        }
    }
}
