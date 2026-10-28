package com.voltyx.efw.notes;

import com.voltyx.efw.sins.SinContent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * The Collector's Diary: stores notes (right click a note onto it, it onto a note, or a note with an empty cursor while a
 * diary is in the inventory) and pages through them in the inspect screen. Port of CDiaryItem and ContainerDiaryMixin; the
 * 1.20 bundle-style stacking hooks replace the 1.12 container mixin.
 */
public class DiaryItem extends Item {
    public DiaryItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.voltyx.efw.notes.client.NotesClient.readDiary(player, stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Diary on the cursor, right click on a note in a slot. */
    @Override
    public boolean overrideStackedOnOther(ItemStack diary, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) {
            return false;
        }
        ItemStack note = slot.getItem();
        if (!(note.getItem() instanceof NoteItem) || !slot.mayPickup(player)) {
            return false;
        }
        boolean absorbed = absorb(diary, note, player);
        if (absorbed) {
            slot.setChanged();
        }
        return absorbed;
    }

    /** Note on the cursor, right click on the diary. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack diary, ItemStack carried, Slot slot, ClickAction action, Player player, SlotAccess carriedAccess) {
        if (action != ClickAction.SECONDARY || !(carried.getItem() instanceof NoteItem)) {
            return false;
        }
        boolean absorbed = absorb(diary, carried, player);
        if (absorbed) {
            slot.setChanged();
        }
        return absorbed;
    }

    /** Moves one note into the diary's StoredNotes list. */
    public static boolean absorb(ItemStack diary, ItemStack note, Player player) {
        if (!(diary.getItem() instanceof DiaryItem) || !(note.getItem() instanceof NoteItem)) {
            return false;
        }
        int noteId = NoteItem.getNoteId(note);
        int variant = NoteItem.getVariant(note);
        boolean quest = NoteItem.isQuest(note);
        if (!NoteItem.hasNoteData(note)) {
            // a note that was never held has no text yet
            int max = NotesConfig.getEntriesCount();
            noteId = max > 0 ? (int) (Math.random() * max) + 1 : 1;
            variant = (int) (Math.random() * 10) + 1;
            quest = false;
        }
        CompoundTag entry = new CompoundTag();
        entry.putInt("noteId", noteId);
        entry.putInt("variant", variant);
        entry.putBoolean("isQuest", quest);
        CompoundTag diaryTag = diary.getOrCreateTagElement("efw_diary");
        ListTag notes = diaryTag.getList("StoredNotes", Tag.TAG_COMPOUND);
        notes.add(entry);
        diaryTag.put("StoredNotes", notes);
        note.shrink(1);
        if (player != null && !player.level().isClientSide) {
            player.level().playSound(null, player.blockPosition(), SinContent.DIARY_OPEN.get(), SoundSource.PLAYERS, 1.2f, 1.0f);
        }
        return true;
    }

    public static ListTag getStoredNotes(ItemStack diary) {
        CompoundTag tag = diary.getTagElement("efw_diary");
        return tag == null ? new ListTag() : tag.getList("StoredNotes", Tag.TAG_COMPOUND);
    }

    /** First diary in the main inventory, then the offhand (the open container is searched by the 1.12 code too). */
    public static ItemStack findDiary(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof DiaryItem) {
                return stack;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() instanceof DiaryItem) {
                return stack;
            }
        }
        if (player.containerMenu != null) {
            for (Slot s : player.containerMenu.slots) {
                if (s.getItem().getItem() instanceof DiaryItem) {
                    return s.getItem();
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
