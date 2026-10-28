package com.voltyx.efw.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.TaczCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TACZ hides the off hand at once while a gun is held. The first person renderer now lets the off hand item go down (and come back)
 * smoothly; while it still holds an item it is drawn, so the animation is seen. Once it is down the renderer holds nothing there.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class GunOffhandLowering {
    private GunOffhandLowering() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderHand(RenderHandEvent e) {
        // TACZ's own condition for hiding it: the gun it draws (also while one is being put away)
        // the chain hook is not lowered: scrolling over guns started and cut that animation every tick, and it jerked before going
        if (e.getItemStack().getItem() instanceof com.voltyx.efw.hook.ChainHookItem) {
            return;
        }
        if (e.getHand() == InteractionHand.OFF_HAND && e.isCanceled() && !e.getItemStack().isEmpty()
                && (TaczCompat.isHoldingGun(Minecraft.getInstance().player) || TaczCompat.keepsGunInHand())) {
            e.setCanceled(false);
        }
    }
}
