package com.voltyx.efw.hook.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.hook.ChainHookEntity;
import com.voltyx.efw.hook.HookContent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client wiring of the chain: renderers and models, the hooks out this tick, and the thrower's animations (with Player Animator). */
public final class HookClient {
    private static final boolean ANIMATED = ModList.get().isLoaded("playeranimator");
    /** Thrower -> the hook out, looked up once a tick. */
    private static final Map<UUID, ChainHookEntity> HOOKS = new HashMap<>();

    private HookClient() {
    }

    public static boolean hasHookOut(Player player) {
        return HOOKS.containsKey(player.getUUID());
    }

    static ChainHookEntity hookOf(Player player) {
        return HOOKS.get(player.getUUID());
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(HookContent.HOOK.get(), ChainHookRenderer::new);
        }

        @SubscribeEvent
        public static void onLayers(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                net.minecraft.client.renderer.entity.player.PlayerRenderer renderer = e.getSkin(skin);
                if (renderer != null) {
                    renderer.addLayer(new HookSwingLayer(renderer));
                }
            }
        }

        @SubscribeEvent
        public static void onModels(ModelEvent.RegisterAdditional e) {
            e.register(ChainDraw.HOOK_MODEL);
        }

        @SubscribeEvent
        public static void onSetup(FMLClientSetupEvent e) {
            if (ANIMATED) {
                e.enqueueWork(ChainAnimation::register);
            }
        }
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            HOOKS.clear();
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity instanceof ChainHookEntity hook && hook.getOwner() instanceof Player owner) {
                        HOOKS.put(owner.getUUID(), hook);
                    }
                }
            }
            strikeOnLanding(mc);
            HookFx.tick(mc);
            if (ANIMATED) {
                ChainAnimation.tick();
            }
        }
    }

    private static int lastHookId = -1;
    private static byte lastState = -1;

    /**
     * The local player was dragged into a creature, or dragged one to himself: he strikes it the moment they meet, as a press of the
     * attack key aimed at it (the weapon in the main hand, its cooldown, swing and sounds, Better Combat's combos, all as usual).
     */
    private static void strikeOnLanding(Minecraft mc) {
        ChainHookEntity hook = mc.player != null ? HOOKS.get(mc.player.getUUID()) : null;
        int id = hook != null ? hook.getId() : -1;
        byte state = hook != null ? hook.state() : -1;
        // the landing blow (in the combo the landing is a kick, ChainAnimation) and the blow on the run through
        boolean landed = ((state == ChainHookEntity.IMPACT && !hook.combo()) || state == ChainHookEntity.PASS || state == ChainHookEntity.ARRIVED)
                && (id != lastHookId || lastState != state);
        lastHookId = id;
        lastState = state;
        if (!landed || mc.screen != null || mc.gameMode == null) {
            return;
        }
        net.minecraft.world.entity.LivingEntity target = hook.lastTarget(); // on the way past the hook is already out of it
        if (target == null || !target.isAlive()) {
            return;
        }
        mc.hitResult = new net.minecraft.world.phys.EntityHitResult(target);
        mc.crosshairPickEntity = target;
        com.voltyx.efw.mixin.MinecraftAttackInvoker attack = (com.voltyx.efw.mixin.MinecraftAttackInvoker) mc;
        attack.efw$setMissTime(0);
        attack.efw$startAttack();
    }
}
