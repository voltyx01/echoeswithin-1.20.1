package com.voltyx.efw.config;

import com.voltyx.efw.client.LockOnConfig;
import com.voltyx.efw.config.ConfigRegistry.Entry;
import com.voltyx.efw.stamina.EfwConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * In-game settings for everything configurable in efw: stamina, low stamina effects, roll, HUD and lock-on.
 * Reached from Mods -> EFW -> Config. Values are written to the toml files on Done.
 */
public class EfwConfigScreen extends Screen {
    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private ConfigList list;
    private Component status = CommonComponents.EMPTY;

    public EfwConfigScreen(Screen parent) {
        super(Component.translatable("efw.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        LockOnConfig.SPEC.getClass();
        com.voltyx.efw.client.dark.DarkGui.SPEC.getClass();
        com.voltyx.efw.client.autohud.AutoHudConfig.SPEC.getClass();
        com.voltyx.efw.coth.CothConfig.SPEC.getClass();
        com.voltyx.efw.gore.ModConfigurationClient.SPEC.getClass();
        com.voltyx.efw.sins.SevenConfig.SPEC.getClass();
        com.voltyx.efw.headshot.HeadshotConfig.SPEC.getClass();
        com.voltyx.efw.heartbeat.HeartbeatConfig.SPEC.getClass();
        com.voltyx.efw.tech.DeviceConfig.SPEC.getClass();
        com.voltyx.efw.doll.DollConfig.COMMON_SPEC.getClass();
        com.voltyx.efw.doll.DollConfig.CLIENT_SPEC.getClass();
        com.voltyx.efw.speech.SpeechConfig.SPEC.getClass(); // make sure the client specs registered their entries
        rows.clear();
        list = new ConfigList(minecraft, width, height, 32, height - 36, 24);
        String lastSection = null;
        for (Entry e : ConfigRegistry.ENTRIES) {
            if (!e.section().equals(lastSection)) {
                lastSection = e.section();
                list.add(new Header(e.section()));
            }
            Row row = new Row(e);
            rows.add(row);
            list.add(row);
        }
        addWidget(list);
        int y = height - 28;
        addRenderableWidget(Button.builder(Component.translatable("efw.config.done"), b -> {
            if (save()) {
                onClose();
            }
        }).bounds(width / 2 - 154, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("efw.config.defaults"), b -> {
            for (Row r : rows) {
                r.reset();
            }
        }).bounds(width / 2 - 50, y, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(width / 2 + 54, y, 100, 20).build());
    }

    private boolean save() {
        for (Row r : rows) {
            if (!r.valid()) {
                status = Component.translatable("efw.config.invalid", r.label());
                return false;
            }
        }
        Set<ForgeConfigSpec> specs = new HashSet<>();
        for (Row r : rows) {
            r.apply();
            specs.add(r.entry.spec());
        }
        for (Row r : rows) {
            if (specs.remove(r.entry.spec())) {
                r.entry.value().save();
            }
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        list.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        if (status != CommonComponents.EMPTY) {
            g.drawCenteredString(font, status, width / 2, height - 46, 0xFF5555);
        }
        super.render(g, mouseX, mouseY, partialTick);
        for (Row r : rows) {
            if (r.hoveredTooltip != null) {
                g.renderTooltip(font, font.split(Component.literal(r.hoveredTooltip), 220), mouseX, mouseY);
                r.hoveredTooltip = null;
                break;
            }
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private static String pretty(String camel) {
        String s = camel.replaceAll("([a-z])([A-Z])", "$1 $2");
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private final class ConfigList extends ContainerObjectSelectionList<ListEntry> {
        ConfigList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
            setRenderBackground(false);
            setRenderTopAndBottom(false);
        }

        void add(ListEntry e) {
            addEntry(e);
        }

        @Override
        public int getRowWidth() {
            return 340;
        }

        @Override
        protected int getScrollbarPosition() {
            return width / 2 + 178;
        }
    }

    private abstract static class ListEntry extends ContainerObjectSelectionList.Entry<ListEntry> {
    }

    private static final class Header extends ListEntry {
        private final String text;

        Header(String text) {
            this.text = text;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float pt) {
            g.drawString(Minecraft.getInstance().font, Component.literal(text).withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD),
                    left, top + 10, 0xFFFFFF);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    private final class Row extends ListEntry {
        private final Entry entry;
        private final AbstractWidget widget;
        private boolean boolValue;
        private String hoveredTooltip;

        @SuppressWarnings("unchecked")
        Row(Entry entry) {
            this.entry = entry;
            Minecraft mc = Minecraft.getInstance();
            if (entry.isBoolean()) {
                boolValue = (Boolean) entry.value().get();
                widget = CycleButton.onOffBuilder(boolValue).displayOnlyValue()
                        .create(0, 0, 80, 20, Component.empty(), (btn, v) -> boolValue = v);
            } else {
                EditBox box = new EditBox(mc.font, 0, 0, 80, 20, Component.empty());
                box.setMaxLength(12);
                box.setValue(format(entry.value().get()));
                widget = box;
            }
        }

        String label() {
            return pretty(entry.name());
        }

        private String format(Object v) {
            if (v instanceof Double d) {
                String s = String.format(Locale.ROOT, "%.3f", d);
                return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
            }
            return String.valueOf(v);
        }

        boolean valid() {
            if (entry.isBoolean()) {
                return true;
            }
            try {
                double v = Double.parseDouble(((EditBox) widget).getValue().trim().replace(',', '.'));
                return v >= entry.min() && v <= entry.max() && (!entry.isInteger() || v == Math.rint(v));
            } catch (NumberFormatException ex) {
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        void apply() {
            if (entry.isBoolean()) {
                ((ForgeConfigSpec.ConfigValue<Boolean>) entry.value()).set(boolValue);
                return;
            }
            double v = Double.parseDouble(((EditBox) widget).getValue().trim().replace(',', '.'));
            if (entry.isInteger()) {
                ((ForgeConfigSpec.ConfigValue<Integer>) entry.value()).set((int) Math.rint(v));
            } else {
                ((ForgeConfigSpec.ConfigValue<Double>) entry.value()).set(v);
            }
        }

        void reset() {
            Object def = entry.value().getDefault();
            if (entry.isBoolean()) {
                boolValue = (Boolean) def;
                ((CycleButton<Boolean>) widget).setValue(boolValue);
            } else {
                ((EditBox) widget).setValue(format(def));
            }
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float pt) {
            Minecraft mc = Minecraft.getInstance();
            boolean bad = !valid();
            g.drawString(mc.font, label(), left, top + 6, bad ? 0xFF5555 : 0xE0E0E0);
            widget.setX(left + width - 84);
            widget.setY(top);
            widget.render(g, mouseX, mouseY, pt);
            if (hovered && !entry.comment().isEmpty() && mouseX < left + width - 90) {
                hoveredTooltip = entry.comment() + (entry.isBoolean() ? "" : "  [" + format(entry.min()) + " .. " + format(entry.max()) + "]");
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(widget);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(widget);
        }
    }
}
