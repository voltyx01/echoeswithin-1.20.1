package com.voltyx.efw.client.autohud;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.voltyx.efw.EFW;
import com.voltyx.efw.compat.LsoSurvival;
import com.voltyx.efw.compat.TaczCompat;
import com.voltyx.efw.speech.client.SpeechClientManager;
import com.voltyx.efw.speech.client.SurvivalReplicas;
import com.voltyx.efw.stamina.EfwConfig;
import com.voltyx.efw.stamina.PlayerData;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The auto-hiding HUD (port of Auto HUD by Crendgrim): every part of the HUD slides away and fades out while nothing happens to it,
 * and comes back when its value changes (health, food, ammo...) or the player does something with it. The parts are the Forge GUI
 * overlays, wrapped from both sides: moved and faded before they draw, restored after. The replicas above the hotbar are drawn
 * outside the overlays and are never touched.
 */
public final class AutoHud {
    public static final KeyMapping TOGGLE = new KeyMapping("key.efw.autohud_toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.efw");
    public static final KeyMapping PEEK = new KeyMapping("key.efw.autohud_peek", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.efw");

    private static final List<HudPart> PARTS = new ArrayList<>();
    private static final Map<ResourceLocation, HudPart> OVERLAYS = new HashMap<>();
    /** The overlays wrapped right now (begun before they drew, to be restored after). */
    private static final Map<ResourceLocation, HudPart> BEGUN = new HashMap<>();

    private static final HudPart HOTBAR;
    private static final HudPart HEALTH;
    private static final HudPart ARMOR;
    private static final HudPart FOOD;
    private static final HudPart AIR;
    private static final HudPart EXPERIENCE;
    private static final HudPart MOUNT_HEALTH;
    private static final HudPart JUMP_BAR;
    private static final HudPart STAMINA;
    private static final HudPart THIRST;
    private static final HudPart TEMPERATURE;
    private static final HudPart TACZ_AMMO;

    private static boolean toggledOff;
    private static boolean wasPeeking;
    private static int lastSlot = -1;
    private static ItemStack lastMain = ItemStack.EMPTY;
    private static ItemStack lastOff = ItemStack.EMPTY;
    private static boolean wasSneaking;
    private static boolean hadScreen;

    static {
        HOTBAR = part("hotbar", AutoHudConfig.HOTBAR, true, null);
        HEALTH = part("health", AutoHudConfig.HEALTH, true, () -> player(p -> new HudPart.Level(Math.round(p.getHealth()), Math.round(p.getMaxHealth()))));
        ARMOR = part("armor", AutoHudConfig.ARMOR, true, () -> player(p -> new HudPart.Level(p.getArmorValue(), 20)));
        FOOD = part("food", AutoHudConfig.FOOD, true, () -> player(p -> new HudPart.Level(p.getFoodData().getFoodLevel(), 20)));
        AIR = part("air", AutoHudConfig.AIR, true, () -> player(p -> new HudPart.Level(p.getAirSupply(), p.getMaxAirSupply())));
        EXPERIENCE = part("experience", AutoHudConfig.EXPERIENCE, true, () -> player(p -> new HudPart.Level(p.totalExperience, Integer.MAX_VALUE)));
        MOUNT_HEALTH = part("mountHealth", AutoHudConfig.MOUNT_HEALTH, true, () -> player(p -> p.getVehicle() instanceof LivingEntity v
                ? new HudPart.Level(Math.round(v.getHealth()), Math.round(v.getMaxHealth())) : null));
        JUMP_BAR = part("jumpBar", AutoHudConfig.JUMP_BAR, true, () -> player(p -> new HudPart.Level(Math.round(p.getJumpRidingScale() * 100), 100)));
        STAMINA = part("stamina", AutoHudConfig.STAMINA, true, () -> player(p -> {
            PlayerData data = PlayerData.get(p);
            int max = (int) Math.round(com.voltyx.efw.stamina.StaminaEvents.maxStamina(p) * 10);
            return data == null ? null : new HudPart.Level((int) Math.round(Math.min(data.stamina * 10, max)), max);
        }));
        THIRST = part("thirst", AutoHudConfig.THIRST, true, () -> player(p -> {
            int h = LsoSurvival.hydration(p);
            return h < 0 ? null : new HudPart.Level(h, 20);
        }));
        TEMPERATURE = part("temperature", AutoHudConfig.TEMPERATURE, true, () -> player(p -> {
            // getting wet is told by the replica, so only the temperature itself brings the part out
            return LsoSurvival.temperature(p);
        }));
        TACZ_AMMO = part("taczAmmo", AutoHudConfig.TACZ_AMMO, false, () -> player(p -> {
            ItemStack held = p.getMainHandItem();
            // the gun, its rounds, fire mode and attachments are all in its tag
            return TaczCompat.isGun(held) ? held.getItem() + "#" + Objects.hashCode(held.getTag()) : "none";
        }));

        // the parts drawn above another one stay up with it, so none slides through the others (Auto HUD's stack components)
        HEALTH.stack(ARMOR);
        FOOD.stack(AIR, THIRST, STAMINA);
        EXPERIENCE.stack(HEALTH, FOOD, MOUNT_HEALTH, TEMPERATURE);
        HOTBAR.stack(EXPERIENCE, JUMP_BAR);

        map(VanillaGuiOverlay.HOTBAR.id(), HOTBAR);
        map(VanillaGuiOverlay.PLAYER_HEALTH.id(), HEALTH);
        map(com.voltyx.efw.client.hearts.HeartsHud.ID, HEALTH);
        map(VanillaGuiOverlay.ARMOR_LEVEL.id(), ARMOR);
        map(VanillaGuiOverlay.FOOD_LEVEL.id(), FOOD);
        map(VanillaGuiOverlay.AIR_LEVEL.id(), AIR);
        map(VanillaGuiOverlay.EXPERIENCE_BAR.id(), EXPERIENCE);
        map(VanillaGuiOverlay.MOUNT_HEALTH.id(), MOUNT_HEALTH);
        map(VanillaGuiOverlay.JUMP_BAR.id(), JUMP_BAR);
        map(new ResourceLocation(EFW.MOD_ID, "stamina"), STAMINA);
        String lso = "legendarysurvivaloverhaul";
        map(new ResourceLocation(lso, "health_overhaul"), HEALTH);
        map(new ResourceLocation(lso, "body_damage"), HEALTH);
        map(new ResourceLocation(lso, "cold_hunger"), FOOD);
        map(new ResourceLocation(lso, "thirst"), THIRST);
        map(new ResourceLocation(lso, "temperature"), TEMPERATURE);
        map(new ResourceLocation(lso, "wetness"), TEMPERATURE);
        map(new ResourceLocation("tacz", "tac_gun_hud_overlay"), TACZ_AMMO);
        map(new ResourceLocation("tacz", "tac_heat_bar"), TACZ_AMMO);
    }

    private AutoHud() {
    }

    private static HudPart part(String name, AutoHudConfig.Part config, boolean mainHud, Supplier<Object> value) {
        HudPart p = new HudPart(name, config, mainHud, value);
        PARTS.add(p);
        return p;
    }

    private static void map(ResourceLocation overlay, HudPart part) {
        OVERLAYS.put(overlay, part);
    }

    private static Object player(java.util.function.Function<LocalPlayer, Object> read) {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? null : read.apply(p);
    }

    static List<HudPart> parts() {
        return PARTS;
    }

    /** The HUD hides by itself: on in the config and not switched off with the key (peeking shows everything for a moment). */
    static boolean dynamic() {
        return AutoHudConfig.ENABLED.get() && !toggledOff;
    }

    private static void revealAll() {
        PARTS.forEach(HudPart::revealCombined);
    }

    /**
     * How far the whole bottom HUD is gone: 1 when every part of it is hidden, 0 when any part is fully up (and when the HUD does not
     * hide by itself at all). The replica's dark gradient only shows over an empty bottom of the screen.
     */
    public static float hiddenFactor() {
        if (!dynamic()) {
            return 0.0F;
        }
        double hidden = 1.0;
        for (HudPart p : PARTS) {
            if (!p.mainHud) {
                continue;
            }
            if (!p.isActive()) {
                return 0.0F;
            }
            hidden = Math.min(hidden, 1.0 - p.alpha);
        }
        return (float) hidden;
    }

    /** Swapping items brings up the whole bottom HUD with the hotbar: hearts, armour, food, stamina, LSO's bars, experience. */
    private static void revealBottom() {
        int ticks = AutoHudConfig.TICKS_REVEALED.get();
        PARTS.stream().filter(p -> p.mainHud).forEach(p -> p.reveal(ticks));
    }

    private static void hideAll() {
        PARTS.forEach(HudPart::forceHide);
    }

    // ---- ticking ---------------------------------------------------------------------------------------------------

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) {
                lastSlot = -1;
                return;
            }
            while (TOGGLE.consumeClick()) {
                toggledOff = !toggledOff;
                if (!dynamic()) {
                    PARTS.forEach(HudPart::revealNow);
                }
            }
            if (mc.isPaused()) {
                return;
            }
            boolean peeking = PEEK.isDown();
            if (peeking) {
                revealAll();
            } else if (wasPeeking) {
                hideAll();
            }
            wasPeeking = peeking;

            tickHotbar(player);
            tickEvents(mc, player);
            boolean holding = AutoHudConfig.CONSUMABLES.get();
            ItemStack main = player.getMainHandItem();
            HEALTH.update(false);
            ARMOR.update(holding && main.getItem() instanceof ArmorItem);
            // hunger, thirst, cold, heat and wetness are told by the character's replica (SurvivalReplicas): while one is said,
            // and while such a need lasts, those bars do not come out by themselves (taking food in hand still shows the food bar)
            boolean speaking = SpeechClientManager.getPersonalLine() != null || SpeechClientManager.getPersonalAlpha() > 0.04F;
            boolean held = holding && main.isEdible() && player.getFoodData().getFoodLevel() < 20;
            FOOD.update(held, !held && (speaking || SurvivalReplicas.hungry(player)));
            AIR.update(false);
            EXPERIENCE.update(false);
            MOUNT_HEALTH.update(false);
            JUMP_BAR.update(false);
            PlayerData data = PlayerData.get(player);
            STAMINA.update(data != null && data.cooldown > 0);
            THIRST.update(false, speaking || SurvivalReplicas.thirsty(player));
            TEMPERATURE.update(false, speaking || SurvivalReplicas.uncomfortable(player));
            TACZ_AMMO.update(false);

            for (HudPart p : PARTS) {
                if (!p.isActive()) {
                    p.reveal(AutoHudConfig.TICKS_REVEALED.get());
                }
                p.tick();
            }
        }

        private static void tickHotbar(LocalPlayer player) {
            int slot = player.getInventory().selected;
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            if (lastSlot >= 0) {
                if (slot != lastSlot) {
                    if (AutoHudConfig.HOTBAR_ON_SLOT.get()) {
                        revealBottom();
                    }
                } else {
                    checkHand(main, lastMain);
                    checkHand(off, lastOff);
                }
            }
            lastSlot = slot;
            lastMain = main.copy();
            lastOff = off.copy();
            int percent = AutoHudConfig.HOTBAR_DURABILITY.get();
            if (percent > 0 && (lowDurability(main, percent) || lowDurability(off, percent))) {
                HOTBAR.revealCombined();
            }
        }

        private static void checkHand(ItemStack now, ItemStack was) {
            if (!ItemStack.isSameItem(now, was)) {
                if (AutoHudConfig.HOTBAR_ON_ITEM.get()) {
                    revealBottom();
                }
            } else if (now.getCount() != was.getCount() && AutoHudConfig.HOTBAR_ON_COUNT.get()) {
                revealBottom();
            }
        }

        private static boolean lowDurability(ItemStack stack, int percent) {
            return stack.isDamageableItem() && stack.getMaxDamage() > 0
                    && stack.getMaxDamage() - stack.getDamageValue() <= stack.getMaxDamage() * percent / 100;
        }

        private static void tickEvents(Minecraft mc, LocalPlayer player) {
            boolean screen = mc.screen != null && !(mc.screen instanceof ChatScreen);
            policy(AutoHudConfig.ON_SCREEN.get(), screen, hadScreen);
            hadScreen = screen;
            boolean sneaking = player.isShiftKeyDown();
            policy(AutoHudConfig.ON_SNEAK.get(), sneaking, wasSneaking);
            wasSneaking = sneaking;
            if ((AutoHudConfig.ON_USING_ITEM.get() && player.isUsingItem())
                    || (AutoHudConfig.ON_MINING.get() && mc.gameMode != null && mc.gameMode.isDestroying())) {
                revealAll();
            }
        }

        /** While the state lasts the HUD shows (or hides); when it ends the parts go back to their own rules. */
        private static void policy(AutoHudConfig.EventPolicy policy, boolean now, boolean was) {
            switch (policy) {
                case REVEAL -> {
                    if (now) {
                        revealAll();
                    }
                }
                case HIDE -> {
                    if (now) {
                        hideAll();
                    } else if (was) {
                        PARTS.forEach(p -> p.reveal(1));
                    }
                }
                case NOTHING -> {
                }
            }
        }

        @SubscribeEvent
        public static void onAttack(InputEvent.InteractionKeyMappingTriggered e) {
            Minecraft mc = Minecraft.getInstance();
            if (e.isAttack() && AutoHudConfig.ON_ATTACKING.get() && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) {
                revealAll();
            }
        }

        // ---- drawing: every overlay of a part is moved and faded, then put back ------------------------------------

        @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
        public static void onOverlayPre(RenderGuiOverlayEvent.Pre e) {
            ResourceLocation id = e.getOverlay().id();
            HudPart part = OVERLAYS.get(id);
            if (part == null || e.isCanceled() || !part.isActive()) {
                return;
            }
            if (!part.shouldRender()) {
                e.setCanceled(true);
                return;
            }
            float partial = e.getPartialTick();
            var pose = e.getGuiGraphics().pose();
            pose.pushPose();
            if (!part.fullyRevealed()) {
                pose.translate(part.offsetX(partial), part.offsetY(partial), 0);
            }
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, part.alpha(partial));
            BEGUN.put(id, part);
        }

        /** Another mod cancelled an overlay that was already wrapped: it does not draw, so it is put back here. */
        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public static void onOverlayCancelled(RenderGuiOverlayEvent.Pre e) {
            if (e.isCanceled()) {
                end(e.getOverlay().id(), e.getGuiGraphics());
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public static void onOverlayPost(RenderGuiOverlayEvent.Post e) {
            end(e.getOverlay().id(), e.getGuiGraphics());
        }

        private static void end(ResourceLocation id, net.minecraft.client.gui.GuiGraphics g) {
            if (BEGUN.remove(id) != null) {
                g.pose().popPose();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }
        }
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent e) {
            e.register(TOGGLE);
            e.register(PEEK);
        }
    }
}
