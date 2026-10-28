package com.voltyx.efw.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.TaczCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns the body (and the head along with it) to the roll direction while rolling and eases it back afterwards.
 * Port of the yaw handling in the 1.12 AnimationTickHandler: the real yaw fields are replaced just for the render pass.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class RollRender {
    private static final Map<UUID, Boolean> WAS_ROLLING = new HashMap<>();
    private static final Map<UUID, float[]> POST_BODY = new HashMap<>();   // {prev, current}
    private static final Map<UUID, float[]> POST_HEAD = new HashMap<>();
    private static final Map<UUID, float[]> SAVED = new HashMap<>();       // yBodyRot, yBodyRotO, yHeadRot, yHeadRotO

    private RollRender() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            POST_BODY.clear();
            POST_HEAD.clear();
            WAS_ROLLING.clear();
            return;
        }
        for (Player p : mc.level.players()) {
            if (RollClient.isRolling(p)) {
                continue;
            }
            decay(POST_BODY.get(p.getUUID()));
            decay(POST_HEAD.get(p.getUUID()));
        }
    }

    private static void decay(float[] v) {
        if (v != null) {
            v[0] = v[1];
            v[1] *= 0.65f;
        }
    }

    @SubscribeEvent
    public static void onPre(RenderPlayerEvent.Pre e) {
        Player p = e.getEntity();
        UUID id = p.getUUID();
        float pt = e.getPartialTick();
        boolean rolling = RollClient.isRolling(p);
        boolean wasRolling = WAS_ROLLING.getOrDefault(id, false);
        WAS_ROLLING.put(id, rolling);

        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float headYaw = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
        Float dashYaw = RollClient.DASH_YAW.get(id);

        if (!rolling && wasRolling && dashYaw != null && !LockOnClient.isLockedOn()) {
            float target = TaczCompat.isHoldingGun(p) ? headYaw : bodyYaw;
            float body = Mth.wrapDegrees(dashYaw - target);
            float head = Mth.wrapDegrees(dashYaw - headYaw);
            float diff = Mth.wrapDegrees(head - body);
            POST_BODY.put(id, new float[] {body, body});
            POST_HEAD.put(id, new float[] {body + diff, body + diff});
        }

        float newBody = bodyYaw;
        float newHead = headYaw;
        boolean override = false;
        if (rolling && dashYaw != null) {
            Integer rem = RollClient.TICKS.get(id);
            float elapsed = (7.0f - ((rem == null ? 0 : rem) - pt)) * 108.0f;
            float blend = Math.min(1.0f, (elapsed / 500.0f) / 0.15f);
            blend = blend * blend * (3.0f - 2.0f * blend);
            newBody += Mth.wrapDegrees(dashYaw - newBody) * blend;
            newHead += Mth.wrapDegrees(newBody - newHead) * blend;
            override = true;
        } else {
            float[] pb = POST_BODY.get(id);
            float[] ph = POST_HEAD.get(id);
            float ib = pb == null ? 0 : pb[0] + (pb[1] - pb[0]) * pt;
            float ih = ph == null ? 0 : ph[0] + (ph[1] - ph[0]) * pt;
            if (Math.abs(ib) > 0.05f || Math.abs(ih) > 0.05f) {
                newBody += ib;
                newHead += ih;
                override = true;
            }
        }
        if (override) {
            SAVED.put(id, new float[] {p.yBodyRot, p.yBodyRotO, p.yHeadRot, p.yHeadRotO});
            p.yBodyRot = newBody;
            p.yBodyRotO = newBody;
            p.yHeadRot = newHead;
            p.yHeadRotO = newHead;
        }
    }

    @SubscribeEvent
    public static void onPost(RenderPlayerEvent.Post e) {
        Player p = e.getEntity();
        float[] s = SAVED.remove(p.getUUID());
        if (s != null) {
            p.yBodyRot = s[0];
            p.yBodyRotO = s[1];
            p.yHeadRot = s[2];
            p.yHeadRotO = s[3];
        }
    }
}
