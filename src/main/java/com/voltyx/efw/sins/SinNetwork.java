package com.voltyx.efw.sins;

import com.voltyx.efw.EFW;
import com.voltyx.efw.sins.stat.SinDefs;
import com.voltyx.efw.sins.stat.SinRuntime;
import com.voltyx.efw.sins.stat.Trigger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/** Packets of the SEVEN progression. The server decides everything; the client only asks. */
public final class SinNetwork {
    private static final String VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(EFW.MOD_ID, "sins"),
            () -> VERSION, VERSION::equals, VERSION::equals);
    private static final int MAX_BUNDLE = 1 << 21;
    private static final Random RNG = new Random();

    private SinNetwork() {
    }

    public static void init() {
        int id = 0;
        CHANNEL.registerMessage(id++, SelectSin.class, SelectSin::encode, SelectSin::decode, SelectSin::handle);
        CHANNEL.registerMessage(id++, LevelUpRequest.class, (m, b) -> {
        }, b -> new LevelUpRequest(), LevelUpRequest::handle);
        CHANNEL.registerMessage(id++, LevelUpCardsOffer.class, LevelUpCardsOffer::encode, LevelUpCardsOffer::decode, LevelUpCardsOffer::handle);
        CHANNEL.registerMessage(id++, AcceptCard.class, AcceptCard::encode, AcceptCard::decode, AcceptCard::handle);
        CHANNEL.registerMessage(id++, SyncSinData.class, SyncSinData::encode, SyncSinData::decode, SyncSinData::handle);
        CHANNEL.registerMessage(id++, UseManual.class, UseManual::encode, UseManual::decode, UseManual::handle);
        CHANNEL.registerMessage(id++, UnlockSkill.class, UnlockSkill::encode, UnlockSkill::decode, UnlockSkill::handle);
        CHANNEL.registerMessage(id++, SyncDefs.class, SyncDefs::encode, SyncDefs::decode, SyncDefs::handle);
        CHANNEL.registerMessage(id++, TriggerFired.class, TriggerFired::encode, TriggerFired::decode, TriggerFired::handle);
        CHANNEL.registerMessage(id++, PulseReport.class, PulseReport::encode, PulseReport::decode, PulseReport::handle);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    /** Client -> server: the player picked a sin in the class select screen (only once: a sin is not changed). */
    public record SelectSin(String sinId) {
        static void encode(SelectSin m, FriendlyByteBuf buf) {
            buf.writeUtf(m.sinId != null ? m.sinId : "");
        }

        static SelectSin decode(FriendlyByteBuf buf) {
            return new SelectSin(buf.readUtf());
        }

        static void handle(SelectSin m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer player = c.getSender();
            c.enqueueWork(() -> {
                SinData data = player == null ? null : SinData.get(player);
                SinType sin = SinType.byId(m.sinId);
                if (data != null && sin != null && data.getChosenSin() == null) {
                    data.setChosenSin(sin);
                    SinEvents.sync(player);
                }
            });
            c.setPacketHandled(true);
        }
    }

    /** Client -> server: the player wants to read a page; the server answers with the cards (the same ones until one is taken). */
    public record LevelUpRequest() {
        static void handle(LevelUpRequest m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer player = c.getSender();
            c.enqueueWork(() -> {
                SinData data = player == null ? null : SinData.get(player);
                if (data == null || data.getChosenSin() == null || data.getPages() >= SinDefs.get().maxPages) {
                    return;
                }
                if (!player.isCreative() && !hasDpor(player)) {
                    return;
                }
                if (data.getOffer().isEmpty()) {
                    data.setOffer(generateOffer(data, SinDefs.get()));
                }
                if (!data.getOffer().isEmpty()) {
                    sendToPlayer(player, new LevelUpCardsOffer(data.getOffer()));
                }
            });
            c.setPacketHandled(true);
        }
    }

    /** Three cards: two of the sin and one common, or three of grace on the grace pages. Cards at the top rank are not offered. */
    static List<String> generateOffer(SinData data, SinDefs defs) {
        int page = data.getPages() + 1;
        List<String> out = new ArrayList<>();
        if (defs.gracePages.contains(page)) {
            pick(out, defs.graceCards, data, defs, 3);
        } else {
            SinDefs.SinDef sin = defs.sins.get(data.getChosenSin());
            if (sin != null) {
                pick(out, sin.cards(), data, defs, defs.sinCardsOffered);
            }
            pick(out, defs.commonCards, data, defs, 3 - out.size());
            if (sin != null && out.size() < 3) {
                pick(out, sin.cards(), data, defs, 3 - out.size());
            }
        }
        if (out.size() < 3) {
            pick(out, defs.graceCards, data, defs, 3 - out.size());
        }
        return out;
    }

    private static void pick(List<String> out, List<SinDefs.CardDef> pool, SinData data, SinDefs defs, int count) {
        List<SinDefs.CardDef> free = new ArrayList<>();
        for (SinDefs.CardDef c : pool) {
            if (data.rank(c.id()) < defs.maxRank && !out.contains(c.id()) && (data.knowsDoll() || !c.isAboutDoll())) {
                free.add(c);
            }
        }
        Collections.shuffle(free, RNG);
        for (int i = 0; i < Math.min(count, free.size()); i++) {
            out.add(free.get(i).id());
        }
    }

    /** Server -> client: the cards to choose from (ids; the client has the content). */
    public record LevelUpCardsOffer(List<String> cards) {
        static void encode(LevelUpCardsOffer m, FriendlyByteBuf buf) {
            buf.writeCollection(m.cards, FriendlyByteBuf::writeUtf);
        }

        static LevelUpCardsOffer decode(FriendlyByteBuf buf) {
            return new LevelUpCardsOffer(buf.readList(FriendlyByteBuf::readUtf));
        }

        static void handle(LevelUpCardsOffer m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.voltyx.efw.sins.client.SinClient.receiveCards(m.cards)));
            c.setPacketHandled(true);
        }
    }

    /** Client -> server: the card at this place of the offer; the server spends a page (free in creative). */
    public record AcceptCard(int index) {
        static void encode(AcceptCard m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.index);
        }

        static AcceptCard decode(FriendlyByteBuf buf) {
            return new AcceptCard(buf.readVarInt());
        }

        static void handle(AcceptCard m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer player = c.getSender();
            c.enqueueWork(() -> {
                SinData data = player == null ? null : SinData.get(player);
                if (data == null || data.getChosenSin() == null || m.index < 0 || m.index >= data.getOffer().size()) {
                    return;
                }
                if (player.isCreative() || consumeDpor(player)) {
                    int pointsBefore = data.skillPoints(SinDefs.get());
                    data.takeCard(data.getOffer().get(m.index));
                    SinEvents.sync(player);
                    if (data.skillPoints(SinDefs.get()) > pointsBefore) {
                        player.level().playSound(null, player.blockPosition(), SinContent.SKILL_UPGRADE.get(), SoundSource.PLAYERS, 0.6f, 0.8f);
                    }
                }
            });
            c.setPacketHandled(true);
        }
    }

    private static boolean hasDpor(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == SinContent.DPOR.get()) {
                return true;
            }
        }
        return false;
    }

    private static boolean consumeDpor(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == SinContent.DPOR.get()) {
                stack.shrink(1);
                player.inventoryMenu.broadcastChanges();
                return true;
            }
        }
        return false;
    }

    /** Client -> server: burn this seal of the tree. */
    public record UnlockSkill(int node) {
        static void encode(UnlockSkill m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.node);
        }

        static UnlockSkill decode(FriendlyByteBuf buf) {
            return new UnlockSkill(buf.readVarInt());
        }

        static void handle(UnlockSkill m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer player = c.getSender();
            c.enqueueWork(() -> {
                SinData data = player == null ? null : SinData.get(player);
                if (data != null && data.canUnlock(SinDefs.get(), m.node)) {
                    data.unlock(m.node);
                    SinEvents.sync(player);
                } else if (player != null) {
                    SinEvents.sync(player); // the client guessed wrong: put its picture right
                }
            });
            c.setPacketHandled(true);
        }
    }

    /** Server -> client: full SEVEN data of the player. */
    public record SyncSinData(CompoundTag nbt) {
        static void encode(SyncSinData m, FriendlyByteBuf buf) {
            buf.writeNbt(m.nbt);
        }

        static SyncSinData decode(FriendlyByteBuf buf) {
            return new SyncSinData(buf.readNbt());
        }

        static void handle(SyncSinData m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.voltyx.efw.sins.client.SinClient.receiveData(m.nbt)));
            c.setPacketHandled(true);
        }
    }

    /** Server -> client: the content files of the server (cards, trees, manuals). */
    public record SyncDefs(String json) {
        static void encode(SyncDefs m, FriendlyByteBuf buf) {
            buf.writeUtf(m.json, MAX_BUNDLE);
        }

        static SyncDefs decode(FriendlyByteBuf buf) {
            return new SyncDefs(buf.readUtf(MAX_BUNDLE));
        }

        static void handle(SyncDefs m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            // in single player the server already holds the same content in this JVM
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (!m.json.equals(SinDefs.bundle())) {
                    SinDefs.apply(m.json);
                }
            }));
            c.setPacketHandled(true);
        }
    }

    /** Server -> client: a trigger fired (the client opens the same windows and moves its pulse). */
    public record TriggerFired(int trigger) {
        static void encode(TriggerFired m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.trigger);
        }

        static TriggerFired decode(FriendlyByteBuf buf) {
            return new TriggerFired(buf.readVarInt());
        }

        static void handle(TriggerFired m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (m.trigger >= 0 && m.trigger < Trigger.values().length) {
                    com.voltyx.efw.sins.client.SinClient.triggerFired(Trigger.values()[m.trigger]);
                }
            }));
            c.setPacketHandled(true);
        }
    }

    /** Client -> server: the pulse of the player (the heartbeat runs on the client), for the conditions the server checks. */
    public record PulseReport(float bpm) {
        static void encode(PulseReport m, FriendlyByteBuf buf) {
            buf.writeFloat(m.bpm);
        }

        static PulseReport decode(FriendlyByteBuf buf) {
            return new PulseReport(buf.readFloat());
        }

        static void handle(PulseReport m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer player = c.getSender();
            c.enqueueWork(() -> {
                if (player != null && Float.isFinite(m.bpm)) {
                    SinRuntime.get(player).reportedPulse = Math.max(0.0f, Math.min(220.0f, m.bpm));
                }
            });
            c.setPacketHandled(true);
        }
    }

    /** Client -> server: the player studies a manual of this category (X over it in the inventory). */
    public record UseManual(int categoryIndex) {
        static void encode(UseManual m, FriendlyByteBuf buf) {
            buf.writeInt(m.categoryIndex);
        }

        static UseManual decode(FriendlyByteBuf buf) {
            return new UseManual(buf.readInt());
        }

        static void handle(UseManual m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer player = c.getSender();
            c.enqueueWork(() -> {
                SinData data = player == null ? null : SinData.get(player);
                Item expected = SinContent.manualForCategory(m.categoryIndex);
                if (data == null || expected == null) {
                    return;
                }
                int current = data.manualRank(m.categoryIndex);
                if (current >= SinDefs.MANUAL_RANKS) {
                    return; // already maxed: the manual is not spent
                }
                // the server finds the manual itself instead of trusting a slot from the client
                for (ItemStack stack : player.getInventory().items) {
                    if (!stack.isEmpty() && stack.getItem() == expected) {
                        if (!player.isCreative()) {
                            stack.shrink(1);
                            player.inventoryMenu.broadcastChanges();
                        }
                        data.setLoreBookProgress(m.categoryIndex, current + 1);
                        SinEvents.sync(player);
                        player.level().playSound(null, player.blockPosition(), SinContent.NOTES.get(), SoundSource.PLAYERS, 1.0f, 0.9f);
                        return;
                    }
                }
            });
            c.setPacketHandled(true);
        }
    }
}
