package com.voltyx.efw.notes.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.notes.NoteItem;
import com.voltyx.efw.notes.NotesContent;
import com.voltyx.efw.sins.SinContent;
import com.voltyx.efw.sins.client.ScreenTransition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Client side of notes and the inspect screen: reading, the creative settings screen and I to inspect a hovered item. */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class NotesClient {
    private NotesClient() {
    }

    public static String currentLanguage() {
        return Minecraft.getInstance().getLanguageManager().getSelected();
    }

    /** The paper texture follows the note's variant (1..10). */
    public static void registerItemProperties(FMLClientSetupEvent e) {
        e.enqueueWork(() -> ItemProperties.register(NotesContent.NOTE.get(), new ResourceLocation(EFW.MOD_ID, "variant"),
                (stack, level, entity, seed) -> Math.max(1, Math.min(10, NoteItem.getVariant(stack))) / 10.0f));
    }

    public static void readNote(Player player, ItemStack stack) {
        // the paper rustle plays once per player per note, the reading sound every time (1.12 behaviour)
        CompoundTag played = stack.getOrCreateTagElement(NoteItem.TAG).getCompound("playedItemSound");
        String id = player.getStringUUID();
        if (!played.contains(id)) {
            player.playSound(SinContent.ITEM_SOUND.get(), 1.5f, 1.0f);
            played.putBoolean(id, true);
            stack.getOrCreateTagElement(NoteItem.TAG).put("playedItemSound", played);
        }
        player.playSound(SinContent.NOTES.get(), 1.0f, 1.0f);
        ScreenTransition.to(new InspectScreen(stack.copy(), null));
    }

    public static void readDiary(Player player, ItemStack stack) {
        player.playSound(SinContent.DIARY_OPEN.get(), 1.5f, 1.0f);
        ScreenTransition.to(new InspectScreen(stack.copy(), null));
    }

    public static void openNoteConfig(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new NoteConfigScreen(mc.player, hand));
        }
    }

    /** True while a text field of the screen (the creative search) has the focus. */
    public static boolean typing(net.minecraft.client.gui.screens.Screen screen) {
        return screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox box && box.isFocused();
    }

    /** Items that cannot be inspected: single ammo and attachments of TACZ. */
    public static boolean isBlacklisted(ItemStack stack) {
        return ModList.get().isLoaded("tacz") && TaczInspect.isBlacklisted(stack.getItem());
    }

    /** The key hints of the 1.12 tooltips: manuals, notes and [I] Inspect on everything inspectable except guns. */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent e) {
        ItemStack stack = e.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        List<Component> tooltip = e.getToolTip();
        if (stack.getItem() instanceof SinContent.ManualItem) {
            tooltip.add(Component.translatable("tooltip.efw.use_manual"));
        }
        if (stack.getItem() instanceof NoteItem) {
            tooltip.add(Component.translatable("tooltip.efw.note_to_diary"));
            if (e.getEntity() != null && e.getEntity().isCreative()) {
                tooltip.add(Component.translatable("tooltip.efw.note_creative_config"));
            }
        }
        boolean gun = ModList.get().isLoaded("tacz") && TaczInspect.isGun(stack.getItem());
        if (!gun && !isBlacklisted(stack)) {
            tooltip.add(Component.translatable("tooltip.efw.inspect"));
        }
    }

    /** I over an item in any container screen opens the inspect screen. */
    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre e) {
        if (e.getKeyCode() != GLFW.GLFW_KEY_I || !(e.getScreen() instanceof AbstractContainerScreen<?> screen) || typing(screen)) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !slot.hasItem() || isBlacklisted(slot.getItem())) {
            return;
        }
        ScreenTransition.to(new InspectScreen(slot.getItem().copy(), screen));
        e.setCanceled(true);
    }
}
