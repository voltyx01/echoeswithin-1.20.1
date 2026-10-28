package com.voltyx.efw.notes.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Picks the items of a custom inspect group: every item of the creative search tab (so each TACZ gun, attachment and ammo is its own
 * entry), filtered by name or id. A click adds or removes the item; shift-click takes the whole item id with all its variants.
 */
public class InspectItemPicker extends Screen {
    private static final int CELL = 20;

    private final Screen parent;
    private final InspectLayout.Group group;
    private final List<ItemStack> all = new ArrayList<>();
    private final List<ItemStack> shown = new ArrayList<>();
    private EditBox search;
    private boolean onlyChosen;
    private int scrollRows;
    private int gridX, gridY, cols, rows;

    public InspectItemPicker(Screen parent, InspectLayout.Group group) {
        super(Component.literal("Предметы группы"));
        this.parent = parent;
        this.group = group;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        if (all.isEmpty()) {
            collect();
        }
        int w = Math.min(width - 40, 420);
        gridX = (width - w) / 2;
        search = new EditBox(font, gridX, 26, w - 130, 16, Component.literal("Поиск"));
        search.setResponder(v -> {
            scrollRows = 0;
            filter();
        });
        addRenderableWidget(search);
        setInitialFocus(search);
        addRenderableWidget(Button.builder(chosenLabel(), b -> {
            onlyChosen = !onlyChosen;
            b.setMessage(chosenLabel());
            scrollRows = 0;
            filter();
        }).bounds(gridX + w - 126, 26, 70, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Готово").withStyle(ChatFormatting.GREEN), b -> onClose())
                .bounds(gridX + w - 54, 26, 54, 16).build());
        gridY = 50;
        cols = Math.max(1, w / CELL);
        rows = Math.max(1, (height - gridY - 22) / CELL);
        filter();
    }

    private Component chosenLabel() {
        return Component.literal(onlyChosen ? "Выбранные" : "Все");
    }

    private void collect() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), mc.player.canUseGameMasterBlocks(),
                    mc.level.registryAccess());
        }
        Map<String, ItemStack> unique = new LinkedHashMap<>();
        for (ItemStack stack : CreativeModeTabs.searchTab().getDisplayItems()) {
            unique.putIfAbsent(InspectLayout.keyOf(stack), stack);
        }
        all.addAll(unique.values());
    }

    private boolean chosen(ItemStack stack) {
        return InspectLayout.groupMember(group, InspectLayout.keyOf(stack));
    }

    private void filter() {
        shown.clear();
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        for (ItemStack stack : all) {
            if (onlyChosen && !chosen(stack)) {
                continue;
            }
            if (!q.isEmpty() && !stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
                    && !InspectLayout.keyOf(stack).contains(q)) {
                continue;
            }
            shown.add(stack);
        }
    }

    private int maxScroll() {
        return Math.max(0, (shown.size() + cols - 1) / cols - rows);
    }

    private ItemStack at(double mouseX, double mouseY) {
        int cx = (int) Math.floor((mouseX - gridX) / CELL);
        int cy = (int) Math.floor((mouseY - gridY) / CELL);
        if (cx < 0 || cx >= cols || cy < 0 || cy >= rows) {
            return null;
        }
        int i = (cy + scrollRows) * cols + cx;
        return i < shown.size() ? shown.get(i) : null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ItemStack stack = at(mouseX, mouseY);
        if (stack != null && button == 0) {
            String key = hasShiftDown() ? InspectLayout.idOf(stack) : InspectLayout.keyOf(stack);
            if (!group.items.remove(key)) {
                group.items.add(key);
            }
            if (onlyChosen) {
                filter();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollRows = Mth.clamp(scrollRows - (int) Math.signum(delta), 0, maxScroll());
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        g.fill(0, 0, width, height, 0xF0050608);
        g.drawString(font, "Предметы группы «" + group.name + "»: " + group.items.size(), gridX, 10, 0xC8B86A, true);
        g.drawString(font, "клик: добавить/убрать · shift+клик: все варианты предмета", gridX, height - 14, 0x666660, false);
        ItemStack hovered = null;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = (r + scrollRows) * cols + c;
                if (i >= shown.size()) {
                    break;
                }
                ItemStack stack = shown.get(i);
                int x = gridX + c * CELL;
                int y = gridY + r * CELL;
                boolean in = chosen(stack);
                boolean over = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
                g.fill(x, y, x + CELL - 1, y + CELL - 1, in ? 0x9040A040 : over ? 0x50FFFFFF : 0x30FFFFFF);
                g.renderItem(stack, x + 1, y + 1);
                if (over) {
                    hovered = stack;
                }
            }
        }
        int max = maxScroll();
        if (max > 0) {
            int barH = rows * CELL;
            int knob = Math.max(10, barH * rows / (rows + max));
            int knobY = gridY + (barH - knob) * scrollRows / max;
            int bx = gridX + cols * CELL + 3;
            g.fill(bx, gridY, bx + 2, gridY + barH, 0x44FFFFFF);
            g.fill(bx, knobY, bx + 2, knobY + knob, 0xFFFFFFFF);
        }
        super.render(g, mouseX, mouseY, partialTicks);
        if (hovered != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(hovered.getHoverName());
            tip.add(Component.literal(InspectLayout.keyOf(hovered)).withStyle(ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }
}
