package com.voltyx.efw.notes.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.voltyx.efw.notes.NoteItem;
import com.voltyx.efw.notes.NotesConfig;
import com.voltyx.efw.notes.NotesContent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Creative settings of a held note: normal or quest text, text id and paper variant, with previews (port of GuiNoteConfig). */
public class NoteConfigScreen extends Screen {
    private static final int PANEL_W = 340;
    private static final int PANEL_H = 220;

    private final InteractionHand hand;
    private boolean isQuest;
    private int noteId;
    private int variant;

    private EditBox idInput;
    private float rotX = -10.0f;
    private float rotY = -160.0f;
    private float zoom = 140.0f;
    private boolean draggingPreview;
    private double textScroll;

    public NoteConfigScreen(Player player, InteractionHand hand) {
        super(Component.literal("Note"));
        this.hand = hand;
        ItemStack held = player.getItemInHand(hand);
        this.noteId = Math.max(1, NoteItem.getNoteId(held));
        this.variant = NoteItem.getVariant(held);
        if (variant < 1 || variant > 10) {
            variant = 1;
        }
        this.isQuest = NoteItem.isQuest(held);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int panelX() {
        return (width - PANEL_W) / 2;
    }

    private int panelY() {
        return (height - PANEL_H) / 2;
    }

    private Component typeLabel() {
        return isQuest ? Component.literal("Тип: Квестовая записка").withStyle(ChatFormatting.GOLD)
                : Component.literal("Тип: Обычная записка").withStyle(ChatFormatting.AQUA);
    }

    @Override
    protected void init() {
        int px = panelX();
        int py = panelY();
        addRenderableWidget(Button.builder(typeLabel(), b -> {
            readId();
            isQuest = !isQuest;
            b.setMessage(typeLabel());
            textScroll = 0;
        }).bounds(px + 16, py + 30, 160, 20).build());

        int idY = py + 70;
        addRenderableWidget(Button.builder(Component.literal("-10"), b -> shiftId(-10)).bounds(px + 16, idY, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-1"), b -> shiftId(-1)).bounds(px + 46, idY, 22, 20).build());
        idInput = addRenderableWidget(new EditBox(font, px + 72, idY + 1, 48, 18, Component.empty()));
        idInput.setMaxLength(6);
        idInput.setValue(String.valueOf(noteId));
        idInput.setFilter(s -> s.chars().allMatch(Character::isDigit));
        idInput.setResponder(s -> readId());
        addRenderableWidget(Button.builder(Component.literal("+1"), b -> shiftId(1)).bounds(px + 124, idY, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+10"), b -> shiftId(10)).bounds(px + 148, idY, 28, 20).build());

        int varY = py + 115;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> variant = variant <= 1 ? 10 : variant - 1).bounds(px + 16, varY, 26, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> variant = variant >= 10 ? 1 : variant + 1).bounds(px + 150, varY, 26, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Применить").withStyle(ChatFormatting.GREEN), b -> {
            readId();
            NotesContent.CHANNEL.sendToServer(new NotesContent.ConfigureNote(hand == InteractionHand.OFF_HAND, noteId, variant, isQuest));
            onClose();
        }).bounds(px + 16, py + 185, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть").withStyle(ChatFormatting.GRAY), b -> onClose())
                .bounds(px + 112, py + 185, 64, 20).build());
    }

    private void readId() {
        try {
            int parsed = Integer.parseInt(idInput.getValue().trim());
            if (parsed > 0) {
                noteId = parsed;
            }
        } catch (NumberFormatException ignored) {
            // keep the last valid id
        }
    }

    private void shiftId(int delta) {
        readId();
        noteId = Math.max(1, noteId + delta);
        idInput.setValue(String.valueOf(noteId));
        textScroll = 0;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!idInput.isFocused() && minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= panelX() + 188) {
            textScroll = Math.max(0, textScroll - (delta > 0 ? 12.0 : -12.0));
        } else {
            zoom = Math.max(60.0f, Math.min(260.0f, zoom + (delta > 0 ? 15.0f : -15.0f)));
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int px = panelX();
        int py = panelY();
        if (mouseX >= px + 16 && mouseX <= px + 176 && mouseY >= py + 138 && mouseY <= py + 180) {
            draggingPreview = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingPreview = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingPreview) {
            rotY += (float) dx;
            rotX = Math.max(-75f, Math.min(75f, rotX + (float) dy));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int px = panelX();
        int py = panelY();
        g.fill(px, py, px + PANEL_W, py + PANEL_H, 0xEE121418);
        g.fill(px + 2, py + 2, px + PANEL_W - 2, py + PANEL_H - 2, 0xFF1C2026);
        g.fill(px + 2, py + 2, px + PANEL_W - 2, py + 24, 0xFF242B35);
        g.drawString(font, ChatFormatting.YELLOW + "Настройка записки (Креатив)", px + 12, py + 8, 0xFFFFFF, true);
        g.drawString(font, "Номер текста (ID):", px + 16, py + 58, 0xAAAAAA, false);
        g.drawString(font, "Текстура (Вариант):", px + 16, py + 102, 0xAAAAAA, false);
        String varStr = "Вариант " + variant + " / 10";
        g.drawString(font, varStr, px + 96 - font.width(varStr) / 2, py + 121, 0xFFFFFF, false);

        // text preview on the right
        int boxX = px + 188;
        int boxY = py + 30;
        int boxW = 136;
        int boxH = 175;
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF121418);
        g.fill(boxX + 1, boxY + 1, boxX + boxW - 1, boxY + boxH - 1, 0xFF181B20);
        g.drawString(font, ChatFormatting.GRAY + "Предпросмотр текста:", boxX + 6, boxY + 6, 0xCCCCCC, false);
        List<FormattedCharSequence> lines = font.split(Component.literal(NotesConfig.getText(noteId, isQuest)), boxW - 14);
        int visibleH = boxH - 24;
        textScroll = Math.min(textScroll, Math.max(0, lines.size() * font.lineHeight - visibleH));
        g.enableScissor(boxX, boxY + 18, boxX + boxW, boxY + boxH - 6);
        int lineY = boxY + 20 - (int) textScroll;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, boxX + 6, lineY, 0xEEEEEE, false);
            lineY += font.lineHeight;
        }
        g.disableScissor();

        renderNoteModel(g, px + 96, py + 158);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderNoteModel(GuiGraphics g, int x, int y) {
        ItemStack preview = new ItemStack(NotesContent.NOTE.get());
        NoteItem.setNoteData(preview, noteId, variant, isQuest);
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 100f);
        pose.scale(zoom, -zoom, zoom);
        pose.mulPose(Axis.XP.rotationDegrees(rotX));
        pose.mulPose(Axis.YP.rotationDegrees(rotY));
        RenderSystem.enableDepthTest();
        Lighting.setupFor3DItems();
        mc.getItemRenderer().renderStatic(preview, ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY, pose,
                g.bufferSource(), mc.level, 0);
        g.flush();
        Lighting.setupForFlatItems();
        pose.popPose();
    }
}
