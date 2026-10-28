package com.voltyx.efw.notes.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * The inspect maker (port of GuiItemInspectMaker), opened with M in the inspect screen: the real inspect screen with a panel on the
 * left. It edits, live, the transform of this item alone, of a custom group of chosen items, of an automatic group, or the text
 * layout; the preview shows the transform being edited. Everything is saved to config/efw/inspect_layout.json.
 */
public class InspectMakerScreen extends InspectScreen {
    enum Mode {
        ITEM("Предмет"), GROUP("Группа"), AUTO("Авто"), TEXT("Текст");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    private static final int PANEL_X = 6;
    private static final int PANEL_W = 200;
    private static final int GOLD = 0xC8B86A;
    private static final int GREY = 0xA0A098;

    private static Mode lastMode = Mode.ITEM;

    private final String key;
    private Mode mode = lastMode;
    private int groupIndex;
    private int autoIndex;
    private boolean panelDrag;
    private EditBox nameBox;
    private final List<String> notes = new ArrayList<>();
    private int notesY;

    public InspectMakerScreen(ItemStack stack, Screen parent) {
        super(stack, parent);
        key = InspectLayout.keyOf(stack);
        autoIndex = group.ordinal();
        InspectLayout.Group own = InspectLayout.groupOf(key);
        groupIndex = own != null ? Math.max(0, InspectLayout.groups().indexOf(own)) : 0;
        resetView();
    }

    private InspectScreen.Group autoGroup() {
        return InspectScreen.Group.values()[autoIndex];
    }

    private InspectLayout.Group currentGroup() {
        List<InspectLayout.Group> groups = InspectLayout.groups();
        if (groups.isEmpty()) {
            return null;
        }
        groupIndex = Mth.clamp(groupIndex, 0, groups.size() - 1);
        return groups.get(groupIndex);
    }

    /** The transform being edited, null when nothing is (then the preview shows what the item really uses). */
    private InspectLayout.Transform editing() {
        if (mode == Mode.ITEM) {
            return InspectLayout.item(key);
        }
        if (mode == Mode.GROUP) {
            InspectLayout.Group g = currentGroup();
            return g == null ? null : g.transform;
        }
        if (mode == Mode.AUTO) {
            return InspectLayout.auto(autoGroup());
        }
        return null;
    }

    @Override
    protected InspectLayout.Transform transform() {
        // the constructor of InspectScreen asks before the fields of this class are set
        InspectLayout.Transform t = key == null ? null : editing();
        return t != null ? t : super.transform();
    }

    /** No idle turn while tuning: the start angles are what is being set. */
    @Override
    protected float idleSpin(InspectLayout.Transform t) {
        return 0.0f;
    }

    static String autoName(InspectScreen.Group g) {
        return switch (g) {
            case NOTE -> "Записки";
            case DIARY -> "Дневник";
            case DPOR -> "Страница Откровения";
            case HELMET -> "Шлемы";
            case ARMOR -> "Броня";
            case TACZ -> "Оружие TACZ";
            case TOOLS_2D -> "Оружие и инструменты";
            case ITEMS_2D -> "Предметы";
            case BLOCKS -> "Блоки";
        };
    }

    /** What the item really uses now, for the panel. */
    private String usedNow() {
        if (InspectLayout.item(key) != null) {
            return "своя настройка";
        }
        InspectLayout.Group g = InspectLayout.groupOf(key);
        if (g != null) {
            return "группа «" + g.name + "»";
        }
        return "авто «" + autoName(group) + "»";
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        super.init();
        notes.clear();
        nameBox = null;
        int x = PANEL_X + 6;
        int w = PANEL_W - 12;
        int y = 22;
        int tabW = (w - 6) / 4;
        for (Mode m : Mode.values()) {
            Component label = m == mode ? Component.literal(m.label).withStyle(ChatFormatting.GOLD) : Component.literal(m.label);
            addRenderableWidget(Button.builder(label, b -> {
                mode = m;
                lastMode = m;
                resetView();
                rebuildWidgets();
            }).bounds(x + m.ordinal() * (tabW + 2), y, tabW, 16).build());
        }
        y += 20;
        notesY = y;
        notes.add("Сейчас у предмета: " + usedNow());
        y += 12;

        switch (mode) {
            case ITEM -> y = initItem(x, y, w);
            case GROUP -> y = initGroup(x, y, w);
            case AUTO -> y = initAuto(x, y, w);
            case TEXT -> y = initText(x, y, w);
        }

        int bottom = height - 24;
        addRenderableWidget(Button.builder(Component.literal("Сбросить вид"), b -> resetView())
                .bounds(x, bottom - 18, w, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Сохранить").withStyle(ChatFormatting.GREEN), b -> InspectLayout.save())
                .bounds(x, bottom, w / 2 - 1, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> close())
                .bounds(x + w / 2 + 1, bottom, w / 2 - 1, 18).build());
    }

    private int initItem(int x, int y, int w) {
        InspectLayout.Transform own = InspectLayout.item(key);
        if (own == null) {
            addRenderableWidget(Button.builder(Component.literal("Своя настройка для предмета"), b -> {
                InspectLayout.createItem(key, InspectLayout.resolve(targetStack, group));
                resetView();
                rebuildWidgets();
            }).bounds(x, y, w, 16).build());
            notes.add("Копирует то, что предмет");
            notes.add("использует сейчас.");
            return y + 20;
        }
        addRenderableWidget(Button.builder(Component.literal("Убрать свою настройку").withStyle(ChatFormatting.RED), b -> {
            InspectLayout.removeItem(key);
            resetView();
            rebuildWidgets();
        }).bounds(x, y, w, 16).build());
        y += 20;
        return transformSliders(x, y, w, own);
    }

    private int initGroup(int x, int y, int w) {
        InspectLayout.Group g = currentGroup();
        if (g == null) {
            addRenderableWidget(Button.builder(Component.literal("Создать группу"), b -> newGroup()).bounds(x, y, w, 16).build());
            notes.add("Группа: свои предметы с одной");
            notes.add("общей настройкой.");
            return y + 20;
        }
        int count = InspectLayout.groups().size();
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            groupIndex = (groupIndex - 1 + count) % count;
            resetView();
            rebuildWidgets();
        }).bounds(x, y, 16, 16).build());
        nameBox = new EditBox(font, x + 19, y + 1, w - 38, 14, Component.literal("Название"));
        nameBox.setMaxLength(40);
        nameBox.setValue(g.name);
        nameBox.setResponder(v -> g.name = v);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            groupIndex = (groupIndex + 1) % count;
            resetView();
            rebuildWidgets();
        }).bounds(x + w - 16, y, 16, 16).build());
        y += 18;

        addRenderableWidget(Button.builder(Component.literal("Новая"), b -> newGroup()).bounds(x, y, w / 2 - 1, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Удалить").withStyle(ChatFormatting.RED), b -> {
            InspectLayout.groups().remove(g);
            groupIndex = Math.max(0, groupIndex - 1);
            resetView();
            rebuildWidgets();
        }).bounds(x + w / 2 + 1, y, w / 2 - 1, 16).build());
        y += 18;

        boolean in = g.items.contains(key);
        addRenderableWidget(Button.builder(Component.literal(in ? "Этот предмет: в группе ✔" : "Добавить этот предмет")
                .withStyle(in ? ChatFormatting.GREEN : ChatFormatting.WHITE), b -> {
            if (!g.items.remove(key)) {
                g.items.add(key);
            }
            rebuildWidgets();
        }).bounds(x, y, w, 16).build());
        y += 18;
        addRenderableWidget(Button.builder(Component.literal("Выбрать предметы… (" + g.items.size() + ")"),
                b -> minecraft.setScreen(new InspectItemPicker(this, g))).bounds(x, y, w, 16).build());
        y += 20;
        if (InspectLayout.item(key) != null) {
            notes.add("§cУ предмета своя настройка:");
            notes.add("§cв осмотре важнее группы.");
        }
        return transformSliders(x, y, w, g.transform);
    }

    private void newGroup() {
        InspectLayout.Group g = new InspectLayout.Group();
        g.name = "Группа " + (InspectLayout.groups().size() + 1);
        g.transform = InspectLayout.resolve(targetStack, group).copy();
        g.items.add(key);
        InspectLayout.groups().add(g);
        groupIndex = InspectLayout.groups().size() - 1;
        resetView();
        rebuildWidgets();
    }

    private int initAuto(int x, int y, int w) {
        int count = InspectScreen.Group.values().length;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            autoIndex = (autoIndex - 1 + count) % count;
            resetView();
            rebuildWidgets();
        }).bounds(x, y, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal(autoName(autoGroup())), b -> {
        }).bounds(x + 18, y, w - 36, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            autoIndex = (autoIndex + 1) % count;
            resetView();
            rebuildWidgets();
        }).bounds(x + w - 16, y, 16, 16).build());
        y += 18;
        addRenderableWidget(Button.builder(Component.literal("Сброс группы"), b -> {
            InspectLayout.resetAuto(autoGroup());
            resetView();
            rebuildWidgets();
        }).bounds(x, y, w, 16).build());
        y += 20;
        if (autoGroup() != group) {
            notes.add("§7Этот предмет не из этой группы:");
            notes.add("§7показан как её предметы.");
        }
        return transformSliders(x, y, w, InspectLayout.auto(autoGroup()));
    }

    private int initText(int x, int y, int w) {
        addRenderableWidget(Button.builder(Component.literal("Сброс текста"), b -> {
            InspectLayout.resetText();
            rebuildWidgets();
        }).bounds(x, y, w, 16).build());
        y += 20;
        InspectLayout.Text t = InspectLayout.text();
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Размер названия", 0.5, 3.0, 0.05, "%.2f", () -> t.titleScale, v -> t.titleScale = (float) v));
        rows.add(new Row("Название X", -200, 200, 1, "%.0f", () -> t.titleX, v -> t.titleX = (int) v));
        rows.add(new Row("Название Y", -100, 100, 1, "%.0f", () -> t.titleY, v -> t.titleY = (int) v));
        rows.add(new Row("Размер описания", 0.5, 2.0, 0.05, "%.2f", () -> t.descScale, v -> t.descScale = (float) v));
        rows.add(new Row("Описание X", -200, 200, 1, "%.0f", () -> t.descX, v -> t.descX = (int) v));
        rows.add(new Row("Описание Y", -100, 100, 1, "%.0f", () -> t.descY, v -> t.descY = (int) v));
        rows.add(new Row("Высота текста", -320, -10, 1, "%.0f", () -> t.blockY, v -> t.blockY = (int) v));
        rows.add(new Row("Ширина линии", 0, 600, 2, "%.0f", () -> t.dividerWidth, v -> t.dividerWidth = (int) v));
        rows.add(new Row("Отступ до линии", 0, 60, 1, "%.0f", () -> t.dividerGap, v -> t.dividerGap = (int) v));
        rows.add(new Row("Отступ после линии", 0, 40, 1, "%.0f", () -> t.descGap, v -> t.descGap = (int) v));
        rows.add(new Row("Ширина описания", 100, 800, 2, "%.0f", () -> t.maxWidth, v -> t.maxWidth = (int) v));
        rows.add(new Row("Строк описания", 1, 20, 1, "%.0f", () -> t.maxLines, v -> t.maxLines = (int) v));
        rows.add(new Row("Строк у записок", 1, 20, 1, "%.0f", () -> t.paperLines, v -> t.paperLines = (int) v));
        rows.add(new Row("Высота предмета", -200, 200, 1, "%.0f", () -> t.itemY, v -> t.itemY = (int) v));
        return addRows(x, y, w, rows, false);
    }

    private int transformSliders(int x, int y, int w, InspectLayout.Transform t) {
        addRenderableWidget(Button.builder(Component.literal("Автоподгон размера: " + (t.fit ? "вкл" : "выкл"))
                .withStyle(t.fit ? ChatFormatting.GREEN : ChatFormatting.GRAY), b -> {
            t.fit = !t.fit;
            resetView();
            rebuildWidgets();
        }).bounds(x, y, t.fit ? w / 2 + 30 : w, 14).build());
        if (t.fit) {
            addRenderableWidget(Button.builder(Component.literal("Вдоль: " + (t.alignLongest ? "да" : "нет")), b -> {
                t.alignLongest = !t.alignLongest;
                rebuildWidgets();
            }).bounds(x + w / 2 + 32, y, w / 2 - 32, 14).build());
        }
        y += 16;
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Масштаб", 0.1, 8.0, 0.05, "%.2f", () -> t.scale, v -> t.scale = (float) v));
        rows.add(new Row("Позиция X", -300, 300, 1, "%.0f", () -> t.posX, v -> t.posX = (float) v));
        rows.add(new Row("Позиция Y", -300, 300, 1, "%.0f", () -> t.posY, v -> t.posY = (float) v));
        rows.add(new Row("Центр X", -1, 1, 0.01, "%.2f", () -> t.pivotX, v -> t.pivotX = (float) v));
        rows.add(new Row("Центр Y", -1, 1, 0.01, "%.2f", () -> t.pivotY, v -> t.pivotY = (float) v));
        rows.add(new Row("Центр Z", -1, 1, 0.01, "%.2f", () -> t.pivotZ, v -> t.pivotZ = (float) v));
        rows.add(new Row("Поворот", -180, 180, 1, "%.0f°", () -> t.yaw, v -> t.yaw = (float) v));
        rows.add(new Row("Наклон", -90, 90, 1, "%.0f°", () -> t.pitch, v -> t.pitch = (float) v));
        rows.add(new Row("Крен", -180, 180, 1, "%.0f°", () -> t.roll, v -> t.roll = (float) v));
        rows.add(new Row("Толщина", 0.05, 2.0, 0.05, "%.2f", () -> t.depth, v -> t.depth = (float) v));
        rows.add(new Row("Вращение", 0, 60, 1, "%.0f°/с", () -> t.spin, v -> t.spin = (float) v));
        return addRows(x, y, w, rows, true);
    }

    private record Row(String label, double min, double max, double step, String format, DoubleSupplier get, DoubleConsumer set) {
    }

    private int addRows(int x, int y, int w, List<Row> rows, boolean angles) {
        int room = height - 24 - 22 - 10 * Math.max(0, notes.size() - 1) - y;
        int rowH = Mth.clamp(room / Math.max(1, rows.size()), 11, 16);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            // the start angles are the view: moving them turns the preview there
            boolean view = angles && (i == 6 || i == 7);
            addRenderableWidget(new ValueSlider(x, y, w, rowH - 2, r, view ? this::resetView : null));
            y += rowH;
        }
        return y;
    }

    /** A slider over a value; the wheel moves it a step at a time. */
    private static final class ValueSlider extends AbstractSliderButton {
        private final Row row;
        private final Runnable changed;

        ValueSlider(int x, int y, int w, int h, Row row, Runnable changed) {
            super(x, y, w, h, Component.empty(), 0.0);
            this.row = row;
            this.changed = changed;
            value = toSlider(row.get().getAsDouble());
            updateMessage();
        }

        private double toSlider(double v) {
            return Mth.clamp((v - row.min()) / (row.max() - row.min()), 0.0, 1.0);
        }

        private double real() {
            double v = row.min() + value * (row.max() - row.min());
            return Mth.clamp(Math.round(v / row.step()) * row.step(), row.min(), row.max());
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(row.label() + ": " + String.format(row.format(), row.get().getAsDouble())));
        }

        @Override
        protected void applyValue() {
            row.set().accept(real());
            if (changed != null) {
                changed.run();
            }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            double v = Mth.clamp(row.get().getAsDouble() + (delta > 0 ? row.step() : -row.step()), row.min(), row.max());
            row.set().accept(Math.round(v / row.step()) * row.step());
            value = toSlider(row.get().getAsDouble());
            if (changed != null) {
                changed.run();
            }
            updateMessage();
            return true;
        }
    }

    // ---------------------------------------------------------------- input

    private static boolean inPanel(double mouseX) {
        return mouseX >= PANEL_X && mouseX <= PANEL_X + PANEL_W;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inPanel(mouseX)) {
            panelDrag = true;
            return widgetClicked(mouseX, mouseY, button);
        }
        if (nameBox != null) {
            nameBox.setFocused(false);
        }
        setFocused(null);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (panelDrag) {
            panelDrag = false;
            return widgetReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (panelDrag) {
            return widgetDragged(mouseX, mouseY, button, dx, dy);
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (inPanel(mouseX)) {
            widgetScrolled(mouseX, mouseY, delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameBox != null && nameBox.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            return widgetKey(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_M) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Saves and goes back to the plain inspect screen of the item. */
    private void close() {
        InspectLayout.save();
        if (minecraft != null) {
            minecraft.setScreen(new InspectScreen(targetStack, parentScreen));
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        super.render(g, mouseX, mouseY, partialTicks);
        g.fill(PANEL_X, 4, PANEL_X + PANEL_W, height - 4, 0xC0000000);
        g.fill(PANEL_X, 4, PANEL_X + PANEL_W, 5, 0xFF444444);
        g.fill(PANEL_X, height - 5, PANEL_X + PANEL_W, height - 4, 0xFF444444);
        g.drawString(font, "Мейкер осмотра", PANEL_X + 6, 9, GOLD, true);
        String shortKey = font.plainSubstrByWidth(key, PANEL_W - 100);
        g.drawString(font, shortKey, PANEL_X + PANEL_W - 6 - font.width(shortKey), 9, 0x666660, false);
        widgetRender(g, mouseX, mouseY, partialTicks);
        // the first note under the tabs, the others (hints and warnings) above the bottom buttons
        for (int i = 0; i < notes.size(); i++) {
            int y = i == 0 ? notesY : height - 24 - 20 - 10 * (notes.size() - i);
            g.drawString(font, font.plainSubstrByWidth(notes.get(i), PANEL_W - 12), PANEL_X + 6, y, GREY, false);
        }
    }
}
