package com.voltyx.efw.tech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voltyx.efw.EFW;
import com.voltyx.efw.gender.api.IGenderArmor;
import com.voltyx.efw.gender.main.GenderPlayer;
import com.voltyx.efw.gender.main.WildfireGender;
import com.voltyx.efw.gender.main.WildfireHelper;
import com.voltyx.efw.tech.BodycamItem;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;


/**
 * The worn bodycam on the chest (port of BodycamLayer): lit texture while it streams, dark when off; pushed forward over a
 * chest piece and over the breasts of the breasts of the built-in gender system.
 */
public class BodycamLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final BedrockModel MODEL = new BedrockModel();
    private static final ResourceLocation GEO = new ResourceLocation(EFW.MOD_ID, "bedrock/bodycam.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(EFW.MOD_ID, "textures/models/armor/bodycam.png");
    private static final ResourceLocation TEXTURE_OFF = new ResourceLocation(EFW.MOD_ID, "textures/models/armor/bodycam_off.png");

    public BodycamLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float age, float headYaw, float headPitch) {
        if (BodycamFeed.isRendering() || player.isInvisible()) {
            return;
        }
        ItemStack cam = BodycamItem.getEquipped(player);
        if (cam.isEmpty() || !BodycamItem.shown(player)) {
            return;
        }
        MODEL.load(GEO, null);
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        ResourceLocation tex = BodycamItem.isActive(player) ? TEXTURE : TEXTURE_OFF;
        MODEL.renderAsJavaPart(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), "bodycam",
                breastZShift(player, partialTick), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** How far the camera moves forward (model pixels): chest armour thickness plus the breast size (BodycamLayer.getBreastZShift). */
    static float breastZShift(AbstractClientPlayer player, float partialTicks) {
        ItemStack armor = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean wearsArmor = !armor.isEmpty() && !(armor.getItem() instanceof BodycamItem);
        float armorShift = wearsArmor ? 0.125F : 0.0F;
        GenderPlayer plr = WildfireGender.getPlayerById(player.getUUID());
        if (plr == null || !plr.getGender().canHaveBreasts()) {
            return armorShift;
        }
        IGenderArmor genderArmor = wearsArmor ? WildfireHelper.getArmorConfig(armor) : null;
        boolean covered = genderArmor != null && genderArmor.coversBreasts();
        boolean hide = genderArmor != null && (genderArmor.alwaysHidesBreasts() || (!plr.showBreastsInArmor() && covered));
        if (wearsArmor) {
            var key = ForgeRegistries.ITEMS.getKey(armor.getItem());
            String id = key != null ? key.toString() : "";
            if (id.contains("juggernaut") || id.contains("hazmat") || id.contains("guillie") || id.contains("heavy")) {
                hide = true;
            }
        }
        float shift = armorShift;
        if (!hide) {
            float size = plr.getLeftBreastPhysics().getBreastSize(partialTicks);
            if (size > 0.02F) {
                shift += size * 0.625F + Math.max(0.0F, size - 1.0F) * 6.0F; // bust above 1.0 also pushes the breasts forward
                shift += plr.getBreasts().getZOffset() * 0.375F;
            }
        }
        return shift;
    }
}
