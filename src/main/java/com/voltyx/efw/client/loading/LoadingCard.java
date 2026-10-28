package com.voltyx.efw.client.loading;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The item card of the loading screen: icon, name, description and lore of a random config entry.
 * The pick is kept across the loading screens of one world join and reset by {@link LoadingScreenHooks}.
 */
public final class LoadingCard {
    private static final int FRAME_WIDTH = 260;
    private static final int LINE_HEIGHT = 12;
    private static final float ITEM_SCALE = 2.0f;
    private static final int TITLE_COLOR = 0xFFC8B86A;
    private static final int DESC_COLOR = 0xFF888880;
    private static final int LORE_COLOR = 0xFF555550;
    private static final int DIVIDER_COLOR = 0xFF2E2E2E;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final Random RANDOM = new Random();
    private static final boolean TACZ = ModList.get().isLoaded("tacz");

    private static LoadingEntry entry;
    private static ItemStack stack = ItemStack.EMPTY;
    private static boolean picked;

    private LoadingCard() {
    }

    public static boolean hasPicked() {
        return picked;
    }

    public static void reset() {
        picked = false;
        entry = null;
        stack = ItemStack.EMPTY;
    }

    private static void pick() {
        picked = true;
        entry = null;
        stack = ItemStack.EMPTY;
        List<LoadingEntry> usable = new ArrayList<>();
        for (LoadingEntry e : LoadingConfig.entries()) {
            if (e != null && stackOf(e) != null) {
                usable.add(e);
            }
        }
        if (usable.isEmpty()) {
            return;
        }
        entry = usable.get(RANDOM.nextInt(usable.size()));
        stack = stackOf(entry);
    }

    private static ItemStack stackOf(LoadingEntry e) {
        ResourceLocation id = e.item == null ? null : ResourceLocation.tryParse(e.item);
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            return null;
        }
        ItemStack result = new ItemStack(item);
        if (e.nbt != null && !e.nbt.isBlank()) {
            try {
                result.setTag(TagParser.parseTag(e.nbt));
            } catch (CommandSyntaxException ex) {
                return null; // a broken tag would show the wrong item, so the entry is skipped
            }
        }
        return ready(result) ? result : null;
    }

    private static boolean ready(ItemStack stack) {
        return !TACZ || TaczLoadingCheck.ready(stack);
    }

    /** Draws the card centered on the screen. */
    public static void draw(GuiGraphics g, int screenW, int screenH) {
        if (!picked || (!stack.isEmpty() && !ready(stack))) {
            // the TACZ index is cleared on leaving a world, so a gun picked while it was there is picked again
            pick();
        }
        if (entry == null || stack.isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int textWidth = FRAME_WIDTH - 24;
        int frameHeight = 56;
        List<FormattedCharSequence> desc = split(font, entry.description, textWidth);
        List<FormattedCharSequence> lore = split(font, entry.lore, textWidth);
        if (!desc.isEmpty()) {
            frameHeight += desc.size() * LINE_HEIGHT + 14;
        }
        if (!lore.isEmpty()) {
            frameHeight += lore.size() * LINE_HEIGHT + 14;
        }
        int x = (screenW - FRAME_WIDTH) / 2;
        int y = (screenH - frameHeight) / 2;

        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.fill(0, 0, FRAME_WIDTH, frameHeight, 0x90000000);
        g.fill(0, 0, FRAME_WIDTH, 1, BORDER_COLOR);
        g.fill(0, frameHeight - 1, FRAME_WIDTH, frameHeight, BORDER_COLOR);
        g.fill(0, 0, 1, frameHeight, BORDER_COLOR);
        g.fill(FRAME_WIDTH - 1, 0, FRAME_WIDTH, frameHeight, BORDER_COLOR);

        g.pose().pushPose();
        g.pose().translate(16, 13, 0);
        g.pose().scale(ITEM_SCALE, ITEM_SCALE, 1.0f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();

        g.drawString(font, stack.getHoverName(), 60, 24, TITLE_COLOR, false);

        int cy = 56;
        if (!desc.isEmpty()) {
            g.fill(12, cy - 8, FRAME_WIDTH - 12, cy - 7, DIVIDER_COLOR);
            for (int i = 0; i < desc.size(); i++) {
                g.drawString(font, desc.get(i), 12, cy + i * LINE_HEIGHT, DESC_COLOR, false);
            }
            cy += desc.size() * LINE_HEIGHT + 14;
        }
        if (!lore.isEmpty()) {
            g.fill(12, cy - 8, FRAME_WIDTH / 2, cy - 7, 0xFF282828);
            for (int i = 0; i < lore.size(); i++) {
                g.drawString(font, lore.get(i), 12, cy + i * LINE_HEIGHT, LORE_COLOR, false);
            }
        }
        g.pose().popPose();
    }

    private static List<FormattedCharSequence> split(Font font, String text, int width) {
        return text == null || text.isEmpty() ? List.of() : font.split(Component.literal(text), width);
    }
}
