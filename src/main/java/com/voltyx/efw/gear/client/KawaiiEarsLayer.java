package com.voltyx.efw.gear.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voltyx.efw.EFW;
import com.voltyx.efw.gear.KawaiiEarsConfig;
import com.voltyx.efw.gear.KawaiiEarsItem;
import com.voltyx.efw.init.EfwContent;
import com.voltyx.efw.tech.client.BodycamFeed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The kawaii ears on the head of a player (a layer of the player renderers), moved by {@link KawaiiEarsAnimator}. */
public class KawaiiEarsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static KawaiiEarsRenderer renderer;
    private static ItemStack earsStack;

    public KawaiiEarsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /** Whether the player shows ears: by the mode of the config, the item worn in the head curio slot. */
    public static boolean shows(Player player) {
        KawaiiEarsConfig.Mode mode = KawaiiEarsConfig.MODE.get();
        if (mode == KawaiiEarsConfig.Mode.OFF) {
            return false;
        }
        return mode == KawaiiEarsConfig.Mode.ALWAYS || !KawaiiEarsItem.worn(player).isEmpty();
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float age, float headYaw, float headPitch) {
        if (BodycamFeed.isRendering() || player.isInvisible() || player.isSpectator() || !shows(player)) {
            return;
        }
        if (renderer == null) {
            renderer = new KawaiiEarsRenderer();
            earsStack = new ItemStack(EfwContent.KAWAII_EARS.get());
        }
        ItemStack worn = KawaiiEarsItem.worn(player);
        renderer.prepForRender(player, worn.isEmpty() ? earsStack : worn, EquipmentSlot.HEAD, getParentModel());
        ResourceLocation texture = renderer.getTextureLocation(renderer.getAnimatable());
        renderer.renderToBuffer(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY,
                1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onLayers(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                PlayerRenderer player = e.getSkin(skin);
                if (player != null) {
                    player.addLayer(new KawaiiEarsLayer(player));
                }
            }
        }
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
    public static final class ForgeEvents {
        private ForgeEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END || KawaiiEarsConfig.MODE.get() == KawaiiEarsConfig.Mode.OFF) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.isPaused()) {
                return;
            }
            for (Player player : mc.level.players()) {
                if (shows(player)) {
                    KawaiiEarsAnimator.update(player);
                }
            }
        }
    }
}
