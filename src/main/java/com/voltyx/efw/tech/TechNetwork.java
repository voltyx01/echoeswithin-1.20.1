package com.voltyx.efw.tech;

import com.voltyx.efw.EFW;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Packets of the terminal, the bodycam, the safe and the radio (ports of the 1.12 terminal/antenna/radio packets). */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class TechNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(EFW.MOD_ID, "tech"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private TechNetwork() {
    }

    static void init() {
        int id = 0;
        CHANNEL.registerMessage(id++, CloseTerminal.class, (m, b) -> b.writeBlockPos(m.pos), b -> new CloseTerminal(b.readBlockPos()), CloseTerminal::handle);
        CHANNEL.registerMessage(id++, InstallBodycamDriver.class, (m, b) -> b.writeBlockPos(m.pos), b -> new InstallBodycamDriver(b.readBlockPos()), InstallBodycamDriver::handle);
        CHANNEL.registerMessage(id++, RequestBodycamList.class, (m, b) -> {
        }, b -> new RequestBodycamList(), RequestBodycamList::handle);
        CHANNEL.registerMessage(id++, BodycamList.class, BodycamList::encode, BodycamList::decode, BodycamList::handle);
        CHANNEL.registerMessage(id++, ToggleBodycam.class, (m, b) -> b.writeInt(m.slotId), b -> new ToggleBodycam(b.readInt()), ToggleBodycam::handle);
        CHANNEL.registerMessage(id++, AntennaPin.class, (m, b) -> {
            b.writeBlockPos(m.pos);
            b.writeUtf(m.pin);
        }, b -> new AntennaPin(b.readBlockPos(), b.readUtf()), AntennaPin::handle);
        CHANNEL.registerMessage(id++, CloseAntenna.class, (m, b) -> b.writeBlockPos(m.pos), b -> new CloseAntenna(b.readBlockPos()), CloseAntenna::handle);
        CHANNEL.registerMessage(id++, RadioLooted.class, (m, b) -> b.writeBlockPos(m.pos), b -> new RadioLooted(b.readBlockPos()), RadioLooted::handle);
        CHANNEL.registerMessage(id++, StartContainerLoot.class, (m, b) -> {
            b.writeBlockPos(m.pos);
            b.writeBoolean(m.opened);
        }, b -> new StartContainerLoot(b.readBlockPos(), b.readBoolean()), StartContainerLoot::handle);
        CHANNEL.registerMessage(id++, ContainerLooted.class, (m, b) -> b.writeBlockPos(m.pos), b -> new ContainerLooted(b.readBlockPos()), ContainerLooted::handle);
        CHANNEL.registerMessage(id++, QueryContainerLoot.class, (m, b) -> b.writeBlockPos(m.pos), b -> new QueryContainerLoot(b.readBlockPos()), QueryContainerLoot::handle);
        CHANNEL.registerMessage(id++, ContainerLootState.class, (m, b) -> {
            b.writeBlockPos(m.pos);
            b.writeBoolean(m.wild);
        }, b -> new ContainerLootState(b.readBlockPos(), b.readBoolean()), ContainerLootState::handle);
        CHANNEL.registerMessage(id++, AbortContainerLoot.class, (m, b) -> {}, b -> new AbortContainerLoot(), AbortContainerLoot::handle);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    private static boolean near(ServerPlayer player, BlockPos pos) {
        return pos != null && player.level().isLoaded(pos) && player.distanceToSqr(pos.getCenter()) <= 64.0;
    }

    /** Client -> server: the user stepped away from the terminal. */
    public record CloseTerminal(BlockPos pos) {
        static void handle(CloseTerminal m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null && near(player, m.pos) && player.level().getBlockEntity(m.pos) instanceof TerminalBlockEntity t) {
                    t.close();
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: "apt install bodycam-driver" succeeded on this terminal. */
    public record InstallBodycamDriver(BlockPos pos) {
        static void handle(InstallBodycamDriver m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null && near(player, m.pos) && player.level().getBlockEntity(m.pos) instanceof TerminalBlockEntity t
                        && t.hasInternetModule()) {
                    t.installBodycamDriver();
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: list every player's camera. */
    public record RequestBodycamList() {
        static void handle(RequestBodycamList m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player == null) {
                    return;
                }
                List<BodycamEntry> list = new ArrayList<>();
                for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                    ItemStack cam = BodycamItem.getStreaming(p);
                    if (cam.getItem() instanceof BodycamItem) {
                        boolean online = BodycamItem.isPowerEnabled(cam) && BodycamItem.getCharge(cam) > 0;
                        list.add(new BodycamEntry(BodycamItem.getCamId(cam), p.getName().getString(), p.getId(), online,
                                BodycamItem.terminalPercent(cam)));
                    }
                }
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BodycamList(list));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> client: the cameras for the terminal session. */
    public record BodycamList(List<BodycamEntry> cameras) {
        static void encode(BodycamList m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.cameras.size());
            for (BodycamEntry e : m.cameras) {
                e.write(buf);
            }
        }

        static BodycamList decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<BodycamEntry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(BodycamEntry.read(buf));
            }
            return new BodycamList(list);
        }

        static void handle(BodycamList m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.voltyx.efw.tech.client.TechClientHooks.receiveCameras(m.cameras)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: X over a bodycam in a container toggles its power. */
    public record ToggleBodycam(int slotId) {
        static void handle(ToggleBodycam m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null) {
                    BodycamItem.toggleFromKey(player, m.slotId);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: a code typed on the safe's keypad. */
    public record AntennaPin(BlockPos pos, String pin) {
        static void handle(AntennaPin m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player == null || !near(player, m.pos) || !(player.level().getBlockEntity(m.pos) instanceof AntennaBlockEntity safe)
                        || safe.isLooted()) {
                    return;
                }
                if (safe.getPinCode().equals(m.pin)) {
                    safe.setUnlocked(true);
                    safe.setOpen(true);
                    safe.setLooted(true);
                    ItemStack module = new ItemStack(TechContent.INTERNET_MODULE.get());
                    if (!player.getInventory().add(module)) {
                        player.drop(module, false);
                    }
                    player.inventoryMenu.broadcastChanges();
                    player.level().playSound(null, m.pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, 1.2F);
                    player.level().playSound(null, m.pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0F, 2.0F);
                } else {
                    player.level().playSound(null, m.pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, 0.6F);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: the user stepped away from the safe. */
    public record CloseAntenna(BlockPos pos) {
        static void handle(CloseAntenna m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null && near(player, m.pos) && player.level().getBlockEntity(m.pos) instanceof AntennaBlockEntity safe) {
                    safe.close();
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: the screwdriver work on an old radio finished (PacketLootingComplete). */
    public record RadioLooted(BlockPos pos) {
        static void handle(RadioLooted m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null && near(player, m.pos) && player.getMainHandItem().is(TechContent.SCREWDRIVER.get())
                        && player.level().getBlockEntity(m.pos) instanceof OldRadioBlockEntity radio && !radio.isDisassembled()) {
                    radio.setDisassembled(true);
                    ItemStack board = new ItemStack(TechContent.RADIO_BOARD.get());
                    if (!player.getInventory().add(board)) {
                        player.drop(board, false);
                    }
                    player.inventoryMenu.broadcastChanges();
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * Server -> client: the container is not searched yet, run the search bar on it. {@code opened}: the server opened it already
     * (lid, doors, its own sound), the client plays no door sound of its own.
     */
    public record StartContainerLoot(BlockPos pos, boolean opened) {
        static void handle(StartContainerLoot m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.voltyx.efw.loot.client.ContainerLooting.start(m.pos, m.opened)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: the search bar of a container ran out (PacketLootingComplete). */
    public record ContainerLooted(BlockPos pos) {
        static void handle(ContainerLooted m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null) {
                    com.voltyx.efw.loot.LootContainers.searched(player, m.pos);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: does the container under the crosshair still have to be searched? */
    public record QueryContainerLoot(BlockPos pos) {
        static void handle(QueryContainerLoot m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null) {
                    com.voltyx.efw.loot.LootContainers.query(player, m.pos);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: the search was given up (use let go, looked away): the container shuts again. */
    public record AbortContainerLoot() {
        static void handle(AbortContainerLoot m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null) {
                    com.voltyx.efw.loot.LootContainers.endSearch(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> client: the answer to {@link QueryContainerLoot}. */
    public record ContainerLootState(BlockPos pos, boolean wild) {
        static void handle(ContainerLootState m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.voltyx.efw.loot.client.ContainerLooting.state(m.pos, m.wild)));
            ctx.get().setPacketHandled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && !e.player.level().isClientSide) {
            BodycamItem.tickWorn(e.player);
            PortableMapItem.tickWorn(e.player);
        }
    }

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent e) {
        ItemStack stack = e.getItem().getItem();
        if (!e.getEntity().level().isClientSide && stack.getItem() instanceof BodycamItem && !BodycamItem.hasCamId(stack)) {
            BodycamItem.assignUniqueCamId(stack);
        }
    }
}
