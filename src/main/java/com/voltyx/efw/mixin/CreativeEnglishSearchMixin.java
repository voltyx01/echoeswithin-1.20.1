package com.voltyx.efw.mixin;

import com.voltyx.efw.search.EnglishNames;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The search tab of the creative inventory also finds items by their English name (the 1.12 MixinGuiContainerCreative). The names of
 * the search tab's items are looked up once per item list (not on every key press), and what the vanilla search already found is
 * indexed by item, so a query that matches thousands of items does not compare every pair.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeEnglishSearchMixin {
    @Shadow
    private EditBox searchBox;

    @Unique
    private static Collection<ItemStack> efw$namedFor;
    @Unique
    private static ItemStack[] efw$stacks = new ItemStack[0];
    @Unique
    private static String[] efw$names = new String[0];

    @Inject(method = "refreshSearchResults", at = @At("TAIL"))
    private void efw$englishSearch(CallbackInfo ci) {
        if (searchBox == null) {
            return;
        }
        String query = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty() || query.startsWith("#")) {
            return;
        }
        Collection<ItemStack> all = CreativeModeTabs.searchTab().getSearchTabDisplayItems();
        if (all != efw$namedFor) {
            efw$stacks = all.toArray(new ItemStack[0]);
            efw$names = new String[efw$stacks.length];
            for (int i = 0; i < efw$stacks.length; i++) {
                efw$names[i] = EnglishNames.of(efw$stacks[i]);
            }
            efw$namedFor = all;
        }
        CreativeModeInventoryScreen.ItemPickerMenu menu = ((CreativeModeInventoryScreen) (Object) this).getMenu();
        Map<Item, List<ItemStack>> present = null;
        for (int i = 0; i < efw$stacks.length; i++) {
            if (!efw$names[i].contains(query)) {
                continue;
            }
            if (present == null) {
                present = new HashMap<>();
                for (ItemStack existing : menu.items) {
                    present.computeIfAbsent(existing.getItem(), k -> new ArrayList<>(1)).add(existing);
                }
            }
            ItemStack stack = efw$stacks[i];
            List<ItemStack> same = present.computeIfAbsent(stack.getItem(), k -> new ArrayList<>(1));
            boolean found = false;
            for (ItemStack existing : same) {
                if (ItemStack.isSameItemSameTags(existing, stack)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                ItemStack copy = stack.copy();
                menu.items.add(copy);
                same.add(copy);
            }
        }
        menu.scrollTo(0.0F);
    }
}
