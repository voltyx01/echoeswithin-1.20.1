package com.voltyx.efw.tech.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.voltyx.efw.EFW;
import com.voltyx.efw.notes.client.InspectScreen;
import com.voltyx.efw.sins.SinContent;
import com.voltyx.efw.sins.client.ScreenTransition;
import com.voltyx.efw.tech.OldRadioBlock;
import com.voltyx.efw.tech.TechContent;
import com.voltyx.efw.tech.TechNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Taking the board out of an old radio (the radio part of ClientLootingManager): hold use with the screwdriver on the radio
 * for 1.5 s; the square progress bar runs around the crosshair, letting go or looking away interrupts.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class RadioLooting {
    private static final int REQUIRED_TICKS = 30;
    private static final long COOLDOWN_MS = 1500L;

    private static BlockPos targetBlock;
    private static int progress;
    private static BlockPos lastCompletedPos;
    private static long lastCompletedTime;
    private static SoundInstance activeSound;

    private RadioLooting() {
    }

    public static void start(BlockPos pos) {
        if (pos.equals(lastCompletedPos) && System.currentTimeMillis() - lastCompletedTime < COOLDOWN_MS) {
            return;
        }
        if (!pos.equals(targetBlock)) {
            stopSound();
            targetBlock = pos;
            progress = 0;
        }
    }

    private static void stopSound() {
        if (activeSound != null) {
            Minecraft.getInstance().getSoundManager().stop(activeSound);
            activeSound = null;
        }
    }

    private static void reset() {
        stopSound();
        targetBlock = null;
        progress = 0;
    }

    /** No swinging or repeated use on the radio being worked on. */
    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (targetBlock != null && e.isUseItem() && mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(targetBlock)) {
            e.setSwingHand(false);
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || targetBlock == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        boolean looking = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK
                && ((BlockHitResult) mc.hitResult).getBlockPos().equals(targetBlock);
        if (!mc.options.keyUse.isDown() || !looking) {
            reset();
            return;
        }
        progress++;
        if (progress == 1) {
            activeSound = new SimpleSoundInstance(TechContent.LOOT_PROGRESS.get(), SoundSource.BLOCKS, 1.0F, 1.0F,
                    SoundInstance.createUnseededRandom(), targetBlock.getX() + 0.5, targetBlock.getY() + 0.5, targetBlock.getZ() + 0.5);
            mc.getSoundManager().play(activeSound);
        }
        if (progress >= REQUIRED_TICKS) {
            boolean radio = mc.level.getBlockState(targetBlock).getBlock() instanceof OldRadioBlock;
            TechNetwork.sendToServer(new TechNetwork.RadioLooted(targetBlock));
            if (radio) {
                mc.player.playSound(SinContent.ITEM_SOUND.get(), 1.5f, 1.0f);
                ScreenTransition.to(new InspectScreen(new ItemStack(TechContent.RADIO_BOARD.get()), null));
            }
            lastCompletedPos = targetBlock;
            lastCompletedTime = System.currentTimeMillis();
            reset();
        }
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post e) {
        if (targetBlock == null || e.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) {
            return;
        }
        GuiGraphics g = e.getGuiGraphics();
        float progressF = Mth.clamp(progress / (float) REQUIRED_TICKS, 0.0f, 1.0f);
        drawSquareProgressBar(g, g.guiWidth() / 2 + 0.5f, g.guiHeight() / 2 + 0.5f, 7.5f, 2.0f, progressF);
    }

    /** White square outline filled clockwise from the top left corner. */
    public static void drawSquareProgressBar(GuiGraphics g, float cx, float cy, float s, float t, float progress) {
        if (progress <= 0.0F) {
            return;
        }
        float d = Math.min(progress, 1.0F) * 8.0F * s;
        RenderSystem.disableBlend();
        float d1 = Math.min(d, 2.0F * s);
        rect(g, cx - s, cy - s, cx - s + d1, cy - s + t);
        if (d > 2.0F * s) {
            float d2 = Math.min(d - 2.0F * s, 2.0F * s);
            rect(g, cx + s - t, cy - s, cx + s, cy - s + d2);
        }
        if (d > 4.0F * s) {
            float d3 = Math.min(d - 4.0F * s, 2.0F * s);
            rect(g, cx + s - d3, cy + s - t, cx + s, cy + s);
        }
        if (d > 6.0F * s) {
            float d4 = Math.min(d - 6.0F * s, 2.0F * s);
            rect(g, cx - s, cy + s - d4, cx - s + t, cy + s);
        }
        RenderSystem.enableBlend();
    }

    private static void rect(GuiGraphics g, float x0, float y0, float x1, float y1) {
        g.pose().pushPose();
        float minX = Math.min(x0, x1), minY = Math.min(y0, y1);
        g.pose().translate(minX, minY, 0);
        g.pose().scale(Math.max(x0, x1) - minX, Math.max(y0, y1) - minY, 1);
        g.fill(0, 0, 1, 1, 0xFFFFFFFF);
        g.pose().popPose();
    }
}
