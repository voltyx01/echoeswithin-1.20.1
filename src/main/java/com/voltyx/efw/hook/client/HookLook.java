package com.voltyx.efw.hook.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.ShoulderCompat;
import com.voltyx.efw.hook.ChainHookEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Once the hook is in a creature the thrower cannot look away from it until the chain lets go: every frame the view is pulled onto
 * it (quickly, not snapped), the mouse only moves it a little in between. Under Shoulder Surfing the camera is aimed so the
 * crosshair lands on it, as the lock-on does.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class HookLook {
    /** Part of the way to the target covered each frame. */
    private static final float PULL = 0.35F;

    private HookLook() {
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (event.phase != TickEvent.Phase.START || player == null || mc.level == null || mc.isPaused()) {
            return;
        }
        ChainHookEntity hook = HookClient.hookOf(player);
        if (hook == null) {
            return;
        }
        byte state = hook.state();
        LivingEntity target = hook.hooked();
        // held on it until he is past it (the combo's run through goes on away from it)
        if (target == null || !ChainHookEntity.engaged(state) || state == ChainHookEntity.PASS) {
            return;
        }
        float pt = event.renderTickTime;
        Vec3 eye = player.getEyePosition(pt);
        Vec3 focus = new Vec3(Mth.lerp(pt, target.xo, target.getX()), Mth.lerp(pt, target.yo, target.getY()) + target.getBbHeight() * 0.7,
                Mth.lerp(pt, target.zo, target.getZ()));
        Vec3 dir = focus.subtract(eye);
        if (dir.lengthSqr() < 0.25) {
            return; // inside it: no spinning round
        }
        float wantYaw = (float) Math.toDegrees(Mth.atan2(-dir.x, dir.z));
        float wantPitch = (float) Math.toDegrees(-Math.asin(Mth.clamp(dir.normalize().y, -1.0, 1.0)));
        float yaw = player.getYRot() + Mth.wrapDegrees(wantYaw - player.getYRot()) * PULL;
        float pitch = player.getXRot() + (wantPitch - player.getXRot()) * PULL;
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setXRot(pitch);
        player.xRotO = pitch;
        player.yHeadRot = yaw;
        player.yHeadRotO = yaw;

        float[] cam = ShoulderCompat.cameraRotation();
        if (cam != null) {
            // the camera sits beside the player: aim it so the crosshair lands on the creature
            Vec3 off = ShoulderCompat.cameraOffset();
            double dist = Math.max(dir.length(), 0.3);
            float camYaw = wantYaw + (float) Math.toDegrees(Math.atan(off.x / dist));
            float camPitch = wantPitch + (float) Math.toDegrees(Math.atan(off.y / dist));
            ShoulderCompat.setCameraRotation(cam[0] + Mth.wrapDegrees(camYaw - cam[0]) * PULL, cam[1] + (camPitch - cam[1]) * PULL);
        }
    }
}
