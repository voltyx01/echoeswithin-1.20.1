package com.voltyx.efw.placed.client;

import com.voltyx.efw.placed.PlacedContent;
import com.voltyx.efw.placed.PlacedItemBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Settings of a placed item (GuiPlacedItemConfig): presets, rotation, position, size, the place of the loupe icon and the pickup lock.
 * The panel sits at the left so the item stays visible in the world; every change is shown at once and sent to the server.
 */
public class PlacedItemConfigScreen extends Screen {
    private final PlacedItemBlockEntity be;
    private float x, y, z;
    private float yaw, pitch, roll;
    private float scale;
    private float iconX, iconY, iconZ;
    private boolean locked;
    private Button lockButton;

    public PlacedItemConfigScreen(PlacedItemBlockEntity be) {
        super(Component.translatable("gui.efw.placed.title"));
        this.be = be;
        x = be.offsetX();
        y = be.offsetY();
        z = be.offsetZ();
        yaw = be.yaw();
        pitch = be.pitch();
        roll = be.roll();
        scale = be.scale();
        iconX = be.iconX();
        iconY = be.iconY();
        iconZ = be.iconZ();
        locked = be.isLocked();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void add(int bx, int by, int w, String label, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> {
            action.run();
            apply();
        }).bounds(bx, by, w, 18).build());
    }

    @Override
    protected void init() {
        int sx = 20;
        int row = 40;
        add(sx, row, 58, "Плашмя", () -> {
            pitch = 0;
            roll = 0;
        });
        add(sx + 62, row, 58, "На боку", () -> {
            pitch = 0;
            roll = 90;
        });
        add(sx + 124, row, 58, "К стене", () -> {
            pitch = 60;
            roll = 0;
        });
        add(sx + 186, row, 58, "Стоя", () -> {
            pitch = 90;
            roll = 0;
        });

        row += 22;
        add(sx, row, 35, "-45°", () -> yaw = angle(yaw - 45));
        add(sx + 37, row, 35, "-15°", () -> yaw = angle(yaw - 15));
        add(sx + 74, row, 50, "180°", () -> yaw = angle(yaw + 180));
        add(sx + 126, row, 35, "+15°", () -> yaw = angle(yaw + 15));
        add(sx + 163, row, 35, "+45°", () -> yaw = angle(yaw + 45));

        row += 22;
        add(sx, row, 35, "-15°", () -> pitch = angle(pitch - 15));
        add(sx + 37, row, 35, "-5°", () -> pitch = angle(pitch - 5));
        add(sx + 126, row, 35, "+5°", () -> pitch = angle(pitch + 5));
        add(sx + 163, row, 35, "+15°", () -> pitch = angle(pitch + 15));

        row += 22;
        add(sx, row, 35, "-15°", () -> roll = angle(roll - 15));
        add(sx + 37, row, 35, "-5°", () -> roll = angle(roll - 5));
        add(sx + 126, row, 35, "+5°", () -> roll = angle(roll + 5));
        add(sx + 163, row, 35, "+15°", () -> roll = angle(roll + 15));

        row += 34;
        add(sx, row, 35, "X-", () -> x = round(x - 0.05f));
        add(sx + 37, row, 35, "X+", () -> x = round(x + 0.05f));
        add(sx + 80, row, 35, "Y-", () -> y = round(y - 0.02f));
        add(sx + 117, row, 35, "Y+", () -> y = round(y + 0.02f));
        add(sx + 160, row, 35, "Z-", () -> z = round(z - 0.05f));
        add(sx + 197, row, 35, "Z+", () -> z = round(z + 0.05f));

        row += 22;
        add(sx, row, 35, "-0.1", () -> scale = Math.max(0.1f, round(scale - 0.1f)));
        add(sx + 37, row, 40, "-0.02", () -> scale = Math.max(0.1f, round(scale - 0.02f)));
        add(sx + 79, row, 40, "1.0x", () -> scale = 1.0f);
        add(sx + 121, row, 40, "+0.02", () -> scale = Math.min(5.0f, round(scale + 0.02f)));
        add(sx + 163, row, 35, "+0.1", () -> scale = Math.min(5.0f, round(scale + 0.1f)));

        row += 38;
        add(sx, row, 32, "IX-", () -> iconX = round(iconX - 0.05f));
        add(sx + 34, row, 32, "IX+", () -> iconX = round(iconX + 0.05f));
        add(sx + 70, row, 32, "IY-", () -> iconY = round(iconY - 0.05f));
        add(sx + 104, row, 32, "IY+", () -> iconY = round(iconY + 0.05f));
        add(sx + 140, row, 32, "IZ-", () -> iconZ = round(iconZ - 0.05f));
        add(sx + 174, row, 32, "IZ+", () -> iconZ = round(iconZ + 0.05f));
        add(sx + 210, row, 34, "0", () -> {
            iconX = 0;
            iconY = 0;
            iconZ = 0;
        });

        row += 25;
        lockButton = Button.builder(lockText(), b -> {
            locked = !locked;
            b.setMessage(lockText());
            apply();
        }).bounds(sx, row, 140, 20).build();
        addRenderableWidget(lockButton);
        addRenderableWidget(Button.builder(Component.literal("Готово").withStyle(ChatFormatting.GREEN), b -> {
            apply();
            onClose();
        }).bounds(sx + 150, row, 94, 20).build());
    }

    private Component lockText() {
        return locked ? Component.literal("Подбор: заблокирован").withStyle(ChatFormatting.RED)
                : Component.literal("Подбор: разрешён").withStyle(ChatFormatting.GRAY);
    }

    private static float angle(float a) {
        a = a % 360.0f;
        if (a < 0) {
            a += 360.0f;
        }
        return round(a);
    }

    private static float round(float v) {
        return Math.round(v * 100.0f) / 100.0f;
    }

    /** Shows the change on the block entity of the client at once and tells the server. */
    private void apply() {
        be.setLayout(x, y, z, yaw, pitch, roll, scale, locked, iconX, iconY, iconZ);
        PlacedContent.CHANNEL.sendToServer(new PlacedContent.UpdatePlacedItem(be.getBlockPos(), x, y, z, yaw, pitch, roll, scale, locked,
                iconX, iconY, iconZ));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(12, 12, 276, 252, 0xBB101418);
        g.fill(14, 14, 274, 250, 0x66222830);
        int sx = 20;
        g.drawString(font, Component.literal("Настройка положения предмета").withStyle(ChatFormatting.YELLOW), sx, 18, 0xFFFFFF);
        String name = be.getStack().isEmpty() ? "Пусто" : be.getStack().getHoverName().getString();
        g.drawString(font, Component.literal("Предмет: ").withStyle(ChatFormatting.GRAY).append(Component.literal(name).withStyle(ChatFormatting.WHITE)), sx, 29, 0xAAAAAA);

        g.drawString(font, "Yaw: " + (int) yaw + "°", sx + 203, 66, 0xE0E0E0);
        g.drawString(font, "Pitch: " + (int) pitch + "°", sx + 75, 88, 0xE0E0E0);
        g.drawString(font, "Roll: " + (int) roll + "°", sx + 75, 110, 0xE0E0E0);
        g.drawString(font, String.format("Поз: X:%.2f Y:%.2f Z:%.2f", x, y, z), sx, 128, 0x88CCEE);
        g.drawString(font, String.format("Размер: %.2fx", scale), sx + 200, 167, 0xFFCC44);
        g.drawString(font, String.format("Лупа: X:%.2f Y:%.2f Z:%.2f", iconX, iconY, iconZ), sx, 188, 0x77DDFF);
        super.render(g, mx, my, partial);
    }
}
