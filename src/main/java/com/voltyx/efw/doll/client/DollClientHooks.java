package com.voltyx.efw.doll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.voltyx.efw.EFW;
import com.voltyx.efw.doll.DollAbility;
import com.voltyx.efw.doll.DollConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The doll as an item (inventory, ground, third person hand) and the belt layer on the players. */
public final class DollClientHooks {
    private static IClientItemExtensions extensions;

    private DollClientHooks() {
    }

    public static IClientItemExtensions itemExtensions() {
        if (extensions == null) {
            extensions = new IClientItemExtensions() {
                private DollItemRenderer renderer;

                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) {
                        Minecraft mc = Minecraft.getInstance();
                        renderer = new DollItemRenderer(mc);
                    }
                    return renderer;
                }
            };
        }
        return extensions;
    }

    /** Draws the 3D doll for the item model (builtin/entity): the pose of the raised doll, blinking red when it is ready. */
    private static final class DollItemRenderer extends BlockEntityWithoutLevelRenderer {
        DollItemRenderer(Minecraft mc) {
            super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            DollModel model = DollClient.model();
            if (!model.isLoaded()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            float scale = DollConfig.GUI_SCALE.get().floatValue();
            pose.scale(scale, -scale, scale);
            pose.translate(0.0, DollConfig.GUI_OFFSET_Y.get(), 0.0);
            model.applyAnimation("animation", 0.625F);

            boolean ready = mc.player != null && DollAbility.isReady(mc.player);
            float pulse = ready ? (float) (Math.sin((System.currentTimeMillis() % 800L) / 800.0 * Math.PI * 2.0) * 0.5 + 0.5) : 0.0F;
            float gb = 1.0F - pulse * 0.85F;
            VertexConsumer buf = buffers.getBuffer(RenderType.entityCutoutNoCull(DollClient.texture()));
            model.render(pose, buf, light, overlay, 1.0F, gb, gb, 1.0F);
            if (ready) {
                // a crimson glow on top for the blinking
                VertexConsumer glow = buffers.getBuffer(RenderType.eyes(DollClient.texture()));
                model.render(pose, glow, light, overlay, 1.0F, 0.15F, 0.15F, (1.0F - gb) * 0.7F);
            }
            pose.popPose();
        }
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onLayers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                PlayerRenderer renderer = event.getSkin(skin);
                if (renderer != null) {
                    renderer.addLayer(new DollBeltLayer(renderer));
                }
            }
        }
    }
}
