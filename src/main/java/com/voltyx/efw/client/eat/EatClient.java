package com.voltyx.efw.client.eat;

import com.voltyx.efw.mixin.SpriteSourcesInvoker;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Client set-up of the procedural eating animation. The sprite source type has to exist before the first resource load (in the
 * mod constructor), the atlas json that uses it is assets/minecraft/atlases/blocks.json.
 */
public final class EatClient {
    private EatClient() {
    }

    public static void init(IEventBus modBus) {
        BiteSprites.TYPE = SpriteSourcesInvoker.efw$register("efw_bitten_food", BiteSprites.CODEC);
        modBus.addListener(EatClient::onStitched);
    }

    private static void onStitched(TextureStitchEvent.Post e) {
        if (e.getAtlas().location().equals(TextureAtlas.LOCATION_BLOCKS)) {
            BittenModels.clear();
        }
    }
}
