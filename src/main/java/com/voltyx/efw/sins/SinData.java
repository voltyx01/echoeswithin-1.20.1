package com.voltyx.efw.sins;

import com.voltyx.efw.sins.stat.SinDefs;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-player SEVEN data: the chosen sin, the pages of Revelation read, the cards taken (with their rank), the burnt seals of the
 * tree (and the order they burnt in, for the petals), the manual ranks and the cards on offer. The server owns it; the client
 * gets a copy. Everything survives death.
 */
public class SinData implements INBTSerializable<CompoundTag> {
    public static final Capability<SinData> CAP = CapabilityManager.get(new CapabilityToken<>() {
    });
    public static final ResourceLocation KEY = new ResourceLocation("efw", "sin_data");

    private SinType chosenSin;
    private int pages;
    private final Map<String, Integer> cards = new LinkedHashMap<>();
    /** Bit n: node n of the tree burnt. The root (bit 0) is the sin itself and always burnt once a sin is chosen. */
    private int skills;
    private final List<Integer> unlockOrder = new ArrayList<>();
    /** Points given by quests, on top of the pages. */
    private int bonusPoints;
    /** 0: melee, 1: firearms, 2: meds, 3: lockpick, 4: tools; the number of volumes read (= rank). */
    private final int[] manuals = new int[SinDefs.MANUALS];
    /** Card ids the server offered for the next page (kept so that closing the screen does not reroll them). */
    private final List<String> offer = new ArrayList<>();
    /** The player has held Saya's doll once: until then everything about it stays veiled. */
    private boolean knowsDoll;
    /** Changes with every change, so the effect cache knows when to rebuild. Not saved. */
    private int version;

    public static SinData get(Player player) {
        return player.getCapability(CAP).orElse(null);
    }

    public int version() {
        return version;
    }

    private void changed() {
        version++;
    }

    public boolean knowsDoll() {
        return knowsDoll;
    }

    public void learnDoll() {
        knowsDoll = true;
        changed();
    }

    public SinType getChosenSin() {
        return chosenSin;
    }

    public void setChosenSin(SinType sin) {
        this.chosenSin = sin;
        skills = sin == null ? 0 : (skills | 1);
        changed();
    }

    public int getPages() {
        return pages;
    }

    /** The level shown in the screen: one plus the pages read. */
    public int getSinLevel() {
        return pages + 1;
    }

    public Map<String, Integer> getCards() {
        return Collections.unmodifiableMap(cards);
    }

    public int rank(String cardId) {
        return cards.getOrDefault(cardId, 0);
    }

    /** A page was read and this card chosen: a new card at rank I, or the next rank of one already taken. */
    public void takeCard(String cardId) {
        cards.merge(cardId, 1, Integer::sum);
        pages++;
        offer.clear();
        changed();
    }

    public boolean hasSkill(int node) {
        return node >= 0 && node < 32 && (skills & (1 << node)) != 0;
    }

    public List<Integer> getUnlockOrder() {
        return Collections.unmodifiableList(unlockOrder);
    }

    public int skillsTaken() {
        return Integer.bitCount(skills & ~1);
    }

    public int skillPoints(SinDefs defs) {
        return Math.max(0, defs.pointsForPages(pages) + bonusPoints - skillsTaken());
    }

    /** The node can burn now: a sin is chosen, a point is free, the node before it in the branch is burnt. */
    public boolean canUnlock(SinDefs defs, int node) {
        if (chosenSin == null || node <= 0 || node >= SinDefs.NODES || hasSkill(node) || skillPoints(defs) <= 0) {
            return false;
        }
        int depth = SinDefs.nodeDepth(node);
        return depth == 1 ? hasSkill(0) : hasSkill(node - 1);
    }

    public void unlock(int node) {
        skills |= 1 << node;
        if (!unlockOrder.contains(node)) {
            unlockOrder.add(node);
        }
        changed();
    }

    public void addBonusPoints(int n) {
        bonusPoints = Math.max(0, bonusPoints + n);
        changed();
    }

    public int manualRank(int category) {
        return category >= 0 && category < manuals.length ? manuals[category] : 0;
    }

    public int[] getLoreBooksProgress() {
        return manuals;
    }

    public void setLoreBookProgress(int categoryIndex, int count) {
        if (categoryIndex >= 0 && categoryIndex < manuals.length) {
            manuals[categoryIndex] = Math.max(0, Math.min(SinDefs.MANUAL_RANKS, count));
            changed();
        }
    }

    public List<String> getOffer() {
        return Collections.unmodifiableList(offer);
    }

    public void setOffer(List<String> ids) {
        offer.clear();
        offer.addAll(ids);
    }

    /** /seven reset */
    public void reset() {
        chosenSin = null;
        pages = 0;
        cards.clear();
        skills = 0;
        unlockOrder.clear();
        bonusPoints = 0;
        java.util.Arrays.fill(manuals, 0);
        offer.clear();
        changed();
    }

    public void setPages(int pages) {
        this.pages = Math.max(0, pages);
        changed();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("chosenSin", chosenSin != null ? chosenSin.getId() : "");
        nbt.putInt("pages", pages);
        CompoundTag c = new CompoundTag();
        cards.forEach(c::putInt);
        nbt.put("cards", c);
        nbt.putInt("skills", skills);
        nbt.putIntArray("unlockOrder", unlockOrder.stream().mapToInt(Integer::intValue).toArray());
        nbt.putInt("bonusPoints", bonusPoints);
        nbt.putIntArray("manuals", manuals);
        ListTag o = new ListTag();
        offer.forEach(id -> o.add(StringTag.valueOf(id)));
        nbt.put("offer", o);
        nbt.putBoolean("knowsDoll", knowsDoll);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt == null) {
            return;
        }
        chosenSin = SinType.byId(nbt.getString("chosenSin"));
        // before v2 the level started at 1 and every card raised it
        pages = nbt.contains("pages") ? nbt.getInt("pages") : Math.max(0, nbt.getInt("sinLevel") - 1);
        cards.clear();
        CompoundTag c = nbt.getCompound("cards");
        for (String id : c.getAllKeys()) {
            cards.put(id, c.getInt(id));
        }
        skills = nbt.getInt("skills") | (chosenSin != null ? 1 : 0);
        unlockOrder.clear();
        for (int n : nbt.getIntArray("unlockOrder")) {
            unlockOrder.add(n);
        }
        bonusPoints = nbt.getInt("bonusPoints");
        int[] arr = nbt.contains("manuals") ? nbt.getIntArray("manuals") : nbt.getIntArray("loreBooks");
        java.util.Arrays.fill(manuals, 0);
        for (int i = 0; i < Math.min(arr.length, manuals.length); i++) {
            manuals[i] = Math.max(0, Math.min(SinDefs.MANUAL_RANKS, arr[i]));
        }
        knowsDoll = nbt.getBoolean("knowsDoll");
        offer.clear();
        ListTag o = nbt.getList("offer", Tag.TAG_STRING);
        for (int i = 0; i < o.size(); i++) {
            offer.add(o.getString(i));
        }
        changed();
    }

    public void copyFrom(SinData other) {
        deserializeNBT(other.serializeNBT());
    }

    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        private final SinData data = new SinData();
        private final LazyOptional<SinData> optional = LazyOptional.of(() -> data);

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return CAP.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            data.deserializeNBT(nbt);
        }
    }
}
