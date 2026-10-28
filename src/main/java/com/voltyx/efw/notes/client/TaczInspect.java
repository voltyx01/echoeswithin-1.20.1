package com.voltyx.efw.notes.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** TACZ items for the inspect rules, standing in for the MWC weapons, bullets and attachments of 1.12. Used only when TACZ is loaded. */
final class TaczInspect {
    private TaczInspect() {
    }

    /** TACZ draws its full model only in GUI renders (else the LOD once past GunLodRenderDistance, always at 0): this render counts as one. */
    static void fullDetail() {
        com.tacz.guns.util.RenderDistance.markGuiRenderTimestamp();
    }

    static boolean isGun(Item item) {
        return item instanceof IGun;
    }

    /** Single ammo and attachments are not inspectable (ammo boxes are, like the MWC ammo packs). */
    static boolean isBlacklisted(Item item) {
        return item instanceof IAmmo || item instanceof IAttachment;
    }

    /**
     * Extra turn for the gun: models turn their "fixed" (item frame) bone 90° one way or the other (the default TACZ pack +90, CIBR
     * -90, Suffuse both), so the +90 ones come out back to front against the CIBR ones the layout is made for.
     */
    static float yawOffset(ItemStack stack) {
        GunDisplayInstance display = TimelessAPI.getGunDisplay(stack).orElse(null);
        BedrockGunModel model = display == null ? null : display.getGunModel();
        List<BedrockPart> path = model == null ? null : model.getFixedOriginPath();
        if (path == null || path.isEmpty()) {
            return 0.0f;
        }
        return path.get(path.size() - 1).getInitRotY() > 0.0f ? 180.0f : 0.0f;
    }
}
