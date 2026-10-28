package com.voltyx.efw.tech.client;

import com.mojang.brigadier.Command;
import com.voltyx.efw.EFW;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Debug tuning of where the bodycam films from (/bodycamdebug): the live feed of yourself or of the one you looked at, and the mount of
 * each stance (height, forward, to the side, pitch, yaw) edited live, saved to config/efw/bodycam_pose.json.
 */
public class BodycamDebugScreen extends Screen {
    private static final int PANEL_X = 6;
    private static final int PANEL_W = 200;

    private static BodycamPose.Stance stance = BodycamPose.Stance.STAND;
    private static boolean forceStance = true;

    private final Entity looked;
    private boolean onLooked;

    public BodycamDebugScreen(Entity looked) {
        super(Component.literal("Бодикамера: позиция съёмки"));
        this.looked = looked;
        this.onLooked = looked != null;
    }

    /** The entity whose bodycam is previewed. */
    Entity carrier() {
        Minecraft mc = Minecraft.getInstance();
        return onLooked && looked != null && looked.isAlive() ? looked : mc.player;
    }

    /** The stance shown, or null to follow what the carrier really does. */
    BodycamPose.Stance forcedStance() {
        return forceStance ? stance : null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int x = PANEL_X + 6;
        int w = PANEL_W - 12;
        int y = 22;
        int tabW = (w - 4) / 3;
        for (BodycamPose.Stance s : BodycamPose.Stance.values()) {
            Component label = s == stance ? Component.literal(s.label).withStyle(ChatFormatting.GOLD) : Component.literal(s.label);
            addRenderableWidget(Button.builder(label, b -> {
                stance = s;
                rebuildWidgets();
            }).bounds(x + s.ordinal() * (tabW + 2), y, tabW, 16).build());
        }
        y += 20;
        addRenderableWidget(Button.builder(Component.literal(forceStance ? "Показ: выбранная поза" : "Показ: как стоит носитель"), b -> {
            forceStance = !forceStance;
            rebuildWidgets();
        }).bounds(x, y, w, 16).build());
        y += 18;
        String who = looked == null ? "Носитель: я" : onLooked ? "Носитель: " + looked.getName().getString() : "Носитель: я";
        Button carrierButton = Button.builder(Component.literal(who), b -> {
            onLooked = !onLooked;
            rebuildWidgets();
        }).bounds(x, y, w, 16).build();
        carrierButton.active = looked != null;
        addRenderableWidget(carrierButton);
        y += 22;

        BodycamPose.Mount m = BodycamPose.get(stance);
        y = slider(x, y, w, "Высота", -0.5, 3.0, 0.01, "%.2f", () -> m.height, v -> m.height = v);
        y = slider(x, y, w, "Вперёд", -1.5, 1.5, 0.01, "%.2f", () -> m.forward, v -> m.forward = v);
        y = slider(x, y, w, "Влево (− вправо)", -1.5, 1.5, 0.01, "%.2f", () -> m.left, v -> m.left = v);
        y = slider(x, y, w, "Наклон", -90.0, 90.0, 0.5, "%.1f°", () -> m.pitch, v -> m.pitch = (float) v);
        y = slider(x, y, w, "Поворот", -180.0, 180.0, 0.5, "%.1f°", () -> m.yaw, v -> m.yaw = (float) v);
        y = slider(x, y, w, "Крен", -45.0, 45.0, 0.5, "%.1f°", () -> m.roll, v -> m.roll = (float) v);
        y += 4;
        y = slider(x, y, w, "Угол обзора (все позы)", 30.0, 140.0, 1.0, "%.0f°", BodycamPose::fov, v -> BodycamPose.setFov((float) v));
        addRenderableWidget(Button.builder(Component.literal("Руки носителя: " + (BodycamPose.hideArms() ? "скрыты" : "видны")), b -> {
            BodycamPose.setHideArms(!BodycamPose.hideArms());
            rebuildWidgets();
        }).bounds(x, y, w, 16).build());
        y += 18;
        addRenderableWidget(Button.builder(Component.literal("Предметы в руках: " + (BodycamPose.hideHeld() ? "скрыты" : "видны")), b -> {
            BodycamPose.setHideHeld(!BodycamPose.hideHeld());
            rebuildWidgets();
        }).bounds(x, y, w, 16).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.literal("Сброс позы «" + stance.label + "»"), b -> {
            BodycamPose.reset(stance);
            rebuildWidgets();
        }).bounds(x, y, w, 16).build());

        int bottom = height - 24;
        addRenderableWidget(Button.builder(Component.literal("Сохранить").withStyle(ChatFormatting.GREEN), b -> BodycamPose.save())
                .bounds(x, bottom, w / 2 - 1, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
                .bounds(x + w / 2 + 1, bottom, w / 2 - 1, 18).build());
    }

    private int slider(int x, int y, int w, String label, double min, double max, double step, String format, DoubleSupplier get,
                       DoubleConsumer set) {
        addRenderableWidget(new ValueSlider(x, y, w, 16, label, min, max, step, format, get, set));
        return y + 18;
    }

    @Override
    public void onClose() {
        BodycamPose.save();
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        g.fill(0, 0, width, height, 0xFF050608);
        g.fill(PANEL_X, 4, PANEL_X + PANEL_W, height - 4, 0xC0000000);
        g.drawString(font, "Бодикамера: позиция съёмки", PANEL_X + 6, 9, 0xC8B86A, true);

        // the feed, as large as fits, upside down as render targets are
        int areaX = PANEL_X + PANEL_W + 10;
        int areaW = width - areaX - 10;
        int areaH = height - 20;
        float aspect = BodycamFeed.feedWidth() / (float) BodycamFeed.feedHeight();
        int fw = Math.min(areaW, (int) (areaH * aspect));
        int fh = (int) (fw / aspect);
        int fx = areaX + (areaW - fw) / 2;
        int fy = 10 + (areaH - fh) / 2;
        g.fill(fx - 1, fy - 1, fx + fw + 1, fy + fh + 1, 0xFF444444);
        if (BodycamFeed.hasValidTexture()) {
            g.blit(BodycamFeed.TEXTURE, fx, fy, fw, fh, 0.0f, BodycamFeed.feedHeight(), BodycamFeed.feedWidth(),
                    -BodycamFeed.feedHeight(), BodycamFeed.feedWidth(), BodycamFeed.feedHeight());
        } else {
            g.fill(fx, fy, fx + fw, fy + fh, 0xFF000000);
            g.drawCenteredString(font, "Нет картинки (Fabulous-графика или шейдеры без поддержки)", fx + fw / 2, fy + fh / 2, 0xA0A098);
        }
        int cx = fx + fw / 2;
        int cy = fy + fh / 2;
        g.fill(cx - 8, cy, cx + 9, cy + 1, 0x66FFFFFF);
        g.fill(cx, cy - 8, cx + 1, cy + 9, 0x66FFFFFF);
        Entity c = carrier();
        if (c != null) {
            String now = "Сейчас носитель: " + BodycamFeed.stanceOf(c).label + "  ·  FOV " + (int) BodycamPose.fov();
            g.drawString(font, now, fx, fy + fh + 4 > height - 10 ? fy + 4 : fy + fh + 4, 0x666660, false);
        }
        super.render(g, mouseX, mouseY, partialTicks);
    }

    /** A slider over a value; the wheel moves it a step at a time. */
    private static final class ValueSlider extends AbstractSliderButton {
        private final String label;
        private final double min, max, step;
        private final String format;
        private final DoubleSupplier get;
        private final DoubleConsumer set;

        ValueSlider(int x, int y, int w, int h, String label, double min, double max, double step, String format, DoubleSupplier get,
                    DoubleConsumer set) {
            super(x, y, w, h, Component.empty(), 0.0);
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.format = format;
            this.get = get;
            this.set = set;
            value = Mth.clamp((get.getAsDouble() - min) / (max - min), 0.0, 1.0);
            updateMessage();
        }

        private void put(double v) {
            set.accept(Mth.clamp(Math.round(v / step) * step, min, max));
            value = Mth.clamp((get.getAsDouble() - min) / (max - min), 0.0, 1.0);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + String.format(format, get.getAsDouble())));
        }

        @Override
        protected void applyValue() {
            set.accept(Mth.clamp(Math.round((min + value * (max - min)) / step) * step, min, max));
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            put(get.getAsDouble() + (delta > 0 ? step : -step));
            updateMessage();
            return true;
        }
    }

    /** /bodycamdebug opens the screen (on the next tick, once the chat has closed). */
    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
    public static final class Opener {
        private static boolean pending;

        private Opener() {
        }

        @SubscribeEvent
        public static void onCommands(RegisterClientCommandsEvent e) {
            e.getDispatcher().register(Commands.literal("bodycamdebug").executes(c -> {
                pending = true;
                return Command.SINGLE_SUCCESS;
            }));
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent e) {
            if (!pending || e.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) {
                return;
            }
            pending = false;
            Entity looked = mc.crosshairPickEntity instanceof LivingEntity living && living != mc.player ? living : null;
            mc.setScreen(new BodycamDebugScreen(looked));
        }
    }
}
