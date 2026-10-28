package com.voltyx.efw.hook.client;

import com.voltyx.efw.hook.ChainHookItem;
import com.voltyx.efw.hook.HookFinisher;
import com.voltyx.efw.sins.SinContent;
import com.voltyx.efw.sins.SinData;
import com.voltyx.efw.sins.stat.SinDefs;
import com.voltyx.efw.sins.stat.SinEffect;
import com.voltyx.efw.sins.stat.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The chain hook's tricks under its tooltip: each one learnt is named and told in full; each one not yet is named with what it takes,
 * with Shift held (the manual volume that teaches it is found in the manuals' files, so it follows them if they change).
 */
public final class HookTooltip {
    private HookTooltip() {
    }

    private record Need(int category, int rank) {
    }

    /** Lines of the detailed description of a learnt trick (tooltip.efw.chain_hook.trick.<id>.desc1 ...). */
    private static final int DESC_LINES = 4;

    public static void appendTricks(List<Component> tooltip) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        Need combo = source(Stat.HOOK_COMBO);
        List<Need> finish = new ArrayList<>();
        finish.add(combo);
        finish.add(new Need(SinContent.ManualType.FIREARMS.categoryIndex, HookFinisher.FIREARMS_RANK));

        List<Component> lines = new ArrayList<>();
        trick(lines, "two_handed", ChainHookItem.twoHandedGrip(player), List.of(source(Stat.HOOK_TWO_HANDED)), player, shift);
        trick(lines, "combo", ChainHookItem.combo(player), List.of(combo), player, shift);
        trick(lines, "finisher", ChainHookItem.finisher(player), finish, player, shift);
        if (lines.isEmpty()) {
            return;
        }
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.efw.chain_hook.tricks"));
        tooltip.addAll(lines);
    }

    /**
     * Learnt: "Name" in gold when collapsed; "Name:" and its detailed description under it with Shift held.
     * Not yet: "Name (needs: ...)", only with Shift held, each need green once met.
     */
    private static void trick(List<Component> out, String id, boolean learnt, List<Need> needs, Player player, boolean shift) {
        String key = "tooltip.efw.chain_hook.trick." + id;
        if (learnt) {
            if (shift) {
                out.add(Component.translatable(key).append(":").withStyle(ChatFormatting.GOLD));
                for (int i = 1; i <= DESC_LINES; i++) {
                    String lineKey = key + ".desc" + i;
                    if (!net.minecraft.client.resources.language.I18n.exists(lineKey)) {
                        break;
                    }
                    out.add(Component.literal("  ").append(Component.translatable(lineKey)).withStyle(ChatFormatting.GRAY));
                }
            } else {
                out.add(Component.translatable(key).withStyle(ChatFormatting.GOLD));
            }
            return;
        }
        if (!shift) {
            return;
        }
        SinData data = SinData.get(player);
        MutableComponent line = Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY);
        line.append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY));
        line.append(Component.translatable("tooltip.efw.chain_hook.needs").withStyle(ChatFormatting.DARK_GRAY));
        boolean first = true;
        for (Need need : needs) {
            if (need == null) {
                continue;
            }
            boolean done = data != null && data.manualRank(need.category) >= need.rank;
            ChatFormatting color = done ? ChatFormatting.DARK_GREEN : ChatFormatting.GRAY;
            line.append(Component.literal(first ? " " : "; ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.translatable(manualItem(need.category)).withStyle(color));
            line.append(Component.translatable("tooltip.efw.chain_hook.volume", need.rank).withStyle(color));
            first = false;
        }
        line.append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
        out.add(line);
    }

    /** The first manual volume whose effects give the stat; null when no manual gives it (a skill or a card does). */
    private static Need source(Stat stat) {
        SinDefs defs = SinDefs.get();
        for (int cat = 0; cat < SinDefs.MANUALS; cat++) {
            for (int r = 0; r < SinDefs.MANUAL_RANKS; r++) {
                SinDefs.RankDef rank = defs.manuals[cat][r];
                if (rank == null) {
                    continue;
                }
                for (SinEffect e : rank.effects()) {
                    if (e.stat() == stat && e.value() > 0.0) {
                        return new Need(cat, r + 1);
                    }
                }
            }
        }
        return null;
    }

    private static String manualItem(int category) {
        for (SinContent.ManualType type : SinContent.ManualType.values()) {
            if (type.categoryIndex == category) {
                return "item.efw." + type.id;
            }
        }
        return "item.efw.manual_melee";
    }
}
