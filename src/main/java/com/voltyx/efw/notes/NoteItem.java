package com.voltyx.efw.notes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * A note: gets a random text id and paper variant (1..10) the first time it is held. Right click reads it in the inspect
 * screen; sneak + right click in creative opens its settings. Right click on it in an inventory files it into a diary.
 */
public class NoteItem extends Item {
    public static final String TAG = "efw_note";

    public NoteItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    static CompoundTag noteTag(ItemStack stack) {
        return stack.getOrCreateTagElement(TAG);
    }

    private static void ensureAssigned(ItemStack stack) {
        CompoundTag nbt = noteTag(stack);
        if (!nbt.contains("noteId")) {
            int max = NotesConfig.getEntriesCount();
            nbt.putInt("noteId", max > 0 ? (int) (Math.random() * max) + 1 : 1);
            nbt.putInt("variant", (int) (Math.random() * 10) + 1);
            nbt.putBoolean("isQuest", false);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide) {
            ensureAssigned(stack);
        }
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            ensureAssigned(stack);
        }
        open(level, player, hand, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() == null) {
            return InteractionResult.PASS;
        }
        open(ctx.getLevel(), ctx.getPlayer(), ctx.getHand(), ctx.getItemInHand());
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    private static void open(Level level, Player player, InteractionHand hand, ItemStack stack) {
        if (!level.isClientSide) {
            return;
        }
        boolean configure = player.isCreative() && player.isShiftKeyDown();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (configure) {
                com.voltyx.efw.notes.client.NotesClient.openNoteConfig(hand);
            } else {
                com.voltyx.efw.notes.client.NotesClient.readNote(player, stack);
            }
        });
    }

    /** Right click with an empty cursor on a note: it goes into the first diary of the player. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack note, ItemStack carried, Slot slot, ClickAction action, Player player, SlotAccess carriedAccess) {
        if (action != ClickAction.SECONDARY || !carried.isEmpty() || !slot.mayPickup(player)) {
            return false;
        }
        ItemStack diary = DiaryItem.findDiary(player);
        return !diary.isEmpty() && DiaryItem.absorb(diary, note, player);
    }

    public static boolean isQuest(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null && tag.getBoolean("isQuest");
    }

    public static int getVariant(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null && tag.contains("variant") ? tag.getInt("variant") : 1;
    }

    public static int getNoteId(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null && tag.contains("noteId") ? tag.getInt("noteId") : 1;
    }

    public static boolean hasNoteData(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null && tag.contains("noteId");
    }

    public static void setNoteData(ItemStack stack, int noteId, int variant, boolean quest) {
        CompoundTag nbt = noteTag(stack);
        nbt.putInt("noteId", noteId);
        nbt.putInt("variant", variant);
        nbt.putBoolean("isQuest", quest);
    }
}
