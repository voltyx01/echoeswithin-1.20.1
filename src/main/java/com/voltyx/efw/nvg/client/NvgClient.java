package com.voltyx.efw.nvg.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.voltyx.efw.EFW;
import com.voltyx.efw.network.EfwNetwork;
import com.voltyx.efw.nvg.NvgHelmets;
import com.voltyx.efw.tech.BatteryHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/** The switch (N), the green screen with the battery line, and the tooltip of the night vision helmets (NVGKeyHandler, NightVisionOverlayHandler). */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class NvgClient {
    public static final KeyMapping TOGGLE = new KeyMapping("key.efw.nvg_toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.efw");
    private static final ResourceLocation OVERLAY = new ResourceLocation("survival_instinct", "textures/screens/night_vision_hunter.png");
    private static final ResourceLocation VIGNETTE = new ResourceLocation("textures/misc/vignette.png");

    private NvgClient() {
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        while (TOGGLE.consumeClick()) {
            if (player == null || mc.screen != null) {
                continue;
            }
            ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
            if (!NvgHelmets.isNvgHelmet(helmet)) {
                continue;
            }
            if (!NvgHelmets.isActive(helmet) && NvgHelmets.charge(helmet) <= 0) {
                player.displayClientMessage(Component.translatable("tooltip.efw.battery.required").withStyle(ChatFormatting.RED), true);
                player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 0.6F);
                continue;
            }
            EfwNetwork.CHANNEL.sendToServer(new EfwNetwork.NvgToggle());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        NvgAnimation.clear();
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!NvgHelmets.isNvgHelmet(stack)) {
            return;
        }
        int percent = BatteryHelper.getChargePercent(stack);
        ItemStack installed = BatteryHelper.getInstalledBattery(stack);
        if (installed.isEmpty() || percent <= 0) {
            event.getToolTip().add(Component.translatable("tooltip.efw.battery.required").withStyle(ChatFormatting.RED));
        } else {
            ChatFormatting color = percent > 50 ? ChatFormatting.GREEN : (percent > 20 ? ChatFormatting.YELLOW : ChatFormatting.RED);
            event.getToolTip().add(Component.translatable("tooltip.efw.battery.charge", percent).withStyle(color)
                    .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                    .append(installed.getHoverName().copy().withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY)));
        }
        event.getToolTip().add(Component.translatable(NvgHelmets.isActive(stack) ? "tooltip.efw.nvg_on" : "tooltip.efw.nvg_off"));
        event.getToolTip().add(Component.translatable("tooltip.efw.nvg_toggle", "§e" + TOGGLE.getTranslatedKeyMessage().getString()));
    }

    // ---- the screen ------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onHelmetOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HELMET.id())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!NvgHelmets.isNvgHelmet(helmet) || !NvgHelmets.isActive(helmet)) {
            return;
        }
        float progress = NvgAnimation.progress(player, true);
        if (progress <= 0.01F) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // the green screen, in the first and the third person
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, progress);
        g.blit(OVERLAY, 0, 0, -90, 0.0F, 0.0F, w, h, w, h);
        // the shadow of the optic tube: a vignette, in the first person only
        if (mc.options.getCameraType().isFirstPerson()) {
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            Matrix4f m = g.pose().last().pose();
            float full = Math.min(1.0F, progress);
            for (float strength : new float[] {full, full * 0.75F}) {
                RenderSystem.setShaderColor(strength, strength, strength, 1.0F);
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.setShaderTexture(0, VIGNETTE);
                BufferBuilder buf = Tesselator.getInstance().getBuilder();
                buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                buf.vertex(m, 0, h, -90).uv(0, 1).endVertex();
                buf.vertex(m, w, h, -90).uv(1, 1).endVertex();
                buf.vertex(m, w, 0, -90).uv(1, 0).endVertex();
                buf.vertex(m, 0, 0, -90).uv(0, 0).endVertex();
                BufferUploader.drawWithShader(buf.end());
            }
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        // the battery line while the visor is down
        if (progress > 0.5F) {
            int percent = Math.max(0, Math.min(100, (int) (NvgHelmets.charge(helmet) / 48000.0F * 100.0F)));
            String text = "BAT: " + percent + "%";
            int color = percent > 50 ? 0x55FF55 : (percent > 20 ? 0xFFFF55 : 0xFF5555);
            g.drawString(mc.font, text, w - mc.font.width(text) - 16, 16, color, true);
        }
    }
}
