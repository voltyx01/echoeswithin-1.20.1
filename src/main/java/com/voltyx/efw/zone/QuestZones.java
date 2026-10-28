package com.voltyx.efw.zone;

import com.voltyx.efw.EFW;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Quest zones (port of the 1.12 zone package): protection events, the marking tool, sync to clients and the packets of the
 * tool. A zone is removed once a closed, unlocked door, trapdoor or gate into it gets opened.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class QuestZones {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EFW.MOD_ID);
    public static final RegistryObject<Item> ZONE_TOOL = ITEMS.register("zone_tool", ZoneToolItem::new);

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(EFW.MOD_ID, "zones"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private QuestZones() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.OP_BLOCKS || e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                e.accept(ZONE_TOOL);
            }
        });
        int id = 0;
        CHANNEL.registerMessage(id++, CreateZone.class, CreateZone::encode, CreateZone::decode, CreateZone::handle);
        CHANNEL.registerMessage(id++, DeleteZoneAt.class, DeleteZoneAt::encode, DeleteZoneAt::decode, DeleteZoneAt::handle);
        CHANNEL.registerMessage(id++, SyncZones.class, SyncZones::encode, SyncZones::decode, SyncZones::handle);
    }

    private static QuestZoneData data(Level level) {
        return level instanceof ServerLevel sl ? QuestZoneData.get(sl.getServer()) : null;
    }

    public static void syncToPlayer(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncZones(new ArrayList<>(QuestZoneData.get(player.server).getZones())));
    }

    public static void syncToAll(MinecraftServer server) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncZones(new ArrayList<>(QuestZoneData.get(server).getZones())));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            syncToPlayer(sp);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent e) {
        Player player = e.getPlayer();
        if (player != null && player.isCreative()) {
            return; // builders may edit any zone
        }
        if (!(e.getLevel() instanceof ServerLevel level)) {
            return;
        }
        QuestZoneData data = data(level);
        if (data != null && data.isProtected(QuestZone.dimensionOf(level), e.getPos())) {
            e.setCanceled(true);
            if (player != null) {
                player.displayClientMessage(Component.literal("Зона защищена от разрушений!").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof Player player && player.isCreative()) {
            return;
        }
        if (!(e.getLevel() instanceof ServerLevel level)) {
            return;
        }
        QuestZoneData data = data(level);
        if (data != null && data.isProtected(QuestZone.dimensionOf(level), e.getPos())) {
            e.setCanceled(true);
            if (e.getEntity() instanceof ServerPlayer sp) {
                sp.inventoryMenu.sendAllDataToRemote(); // give the item back on the client
                sp.displayClientMessage(Component.literal("Здесь нельзя строить!").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = e.getPos();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        boolean isDoor = block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || block.getClass().getName().toLowerCase().contains("door");
        if (!isDoor) {
            return;
        }
        QuestZoneData data = data(level);
        if (data == null) {
            return;
        }
        String dim = QuestZone.dimensionOf(level);
        // the OPEN state of a two-block door lives in both halves; use the lower one
        BlockPos checkPos = block instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        QuestZone zone = data.findZoneAt(dim, pos);
        if (zone == null) {
            zone = data.findZoneAt(dim, checkPos);
        }
        if (zone == null) {
            zone = data.findZoneTouching(dim, checkPos, 1); // a door in the wall right next to the room
        }
        if (zone == null) {
            return;
        }
        QuestZone target = zone;
        Player player = e.getEntity();
        boolean previouslyOpen = isOpen(level.getBlockState(checkPos));
        // check after the click has been processed: only a door that really opened lifts the protection
        level.getServer().execute(() -> {
            if (!previouslyOpen && isOpen(level.getBlockState(checkPos))) {
                data.removeZone(target.id());
                syncToAll(level.getServer());
                player.sendSystemMessage(Component.literal("[Зоны] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Дверь открыта! Защита с комнаты \"" + target.name() + "\" снята.").withStyle(ChatFormatting.GREEN)));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static boolean isOpen(BlockState state) {
        if (state.hasProperty(BlockStateProperties.OPEN)) {
            return state.getValue(BlockStateProperties.OPEN);
        }
        for (Property<?> prop : state.getProperties()) {
            if ("open".equalsIgnoreCase(prop.getName()) && prop.getValueClass() == Boolean.class) {
                return state.getValue((Property<Boolean>) prop);
            }
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosion(ExplosionEvent.Detonate e) {
        QuestZoneData data = data(e.getLevel());
        if (data != null) {
            String dim = QuestZone.dimensionOf(e.getLevel());
            e.getAffectedBlocks().removeIf(pos -> data.isProtected(dim, pos)); // rooms survive any explosion
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobGriefing(EntityMobGriefingEvent e) {
        QuestZoneData data = e.getEntity() == null ? null : data(e.getEntity().level());
        if (data != null && data.isProtected(QuestZone.dimensionOf(e.getEntity().level()), e.getEntity().blockPosition())) {
            e.setResult(Event.Result.DENY);
        }
    }

    private static boolean mayEdit(ServerPlayer player) {
        return player.isCreative() || player.hasPermissions(2);
    }

    /** Client -> server: create a zone from the two tool points. */
    public record CreateZone(String name, BlockPos pos1, BlockPos pos2) {
        static void encode(CreateZone m, FriendlyByteBuf buf) {
            buf.writeUtf(m.name);
            buf.writeBlockPos(m.pos1);
            buf.writeBlockPos(m.pos2);
        }

        static CreateZone decode(FriendlyByteBuf buf) {
            return new CreateZone(buf.readUtf(), buf.readBlockPos(), buf.readBlockPos());
        }

        static void handle(CreateZone m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer player = c.getSender();
            c.enqueueWork(() -> {
                if (player == null) {
                    return;
                }
                if (!mayEdit(player)) {
                    player.sendSystemMessage(Component.literal("У вас нет прав для создания защищенных зон!").withStyle(ChatFormatting.RED));
                    return;
                }
                String id = UUID.randomUUID().toString().substring(0, 8);
                QuestZone zone = QuestZone.of(id, m.name, QuestZone.dimensionOf(player.level()), m.pos1, m.pos2);
                QuestZoneData.get(player.server).addZone(zone);
                syncToAll(player.server);
                player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.7F, 1.2F);
                player.sendSystemMessage(Component.literal("[Зоны] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Зона \"" + m.name + "\" [ID: " + id + "] успешно создана! Размеры: "
                                + zone.sizeX() + "x" + zone.sizeY() + "x" + zone.sizeZ()).withStyle(ChatFormatting.GREEN)));
            });
            c.setPacketHandled(true);
        }
    }

    /** Client -> server: delete the zone at this block. */
    public record DeleteZoneAt(BlockPos pos) {
        static void encode(DeleteZoneAt m, FriendlyByteBuf buf) {
            buf.writeBlockPos(m.pos);
        }

        static DeleteZoneAt decode(FriendlyByteBuf buf) {
            return new DeleteZoneAt(buf.readBlockPos());
        }

        static void handle(DeleteZoneAt m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer player = c.getSender();
            c.enqueueWork(() -> {
                if (player == null) {
                    return;
                }
                if (!mayEdit(player)) {
                    player.sendSystemMessage(Component.literal("У вас нет прав для удаления защищенных зон!").withStyle(ChatFormatting.RED));
                    return;
                }
                QuestZoneData data = QuestZoneData.get(player.server);
                QuestZone existing = data.findZoneAt(QuestZone.dimensionOf(player.level()), m.pos);
                if (existing != null) {
                    data.removeZone(existing.id());
                    syncToAll(player.server);
                    player.level().playSound(null, player.blockPosition(), SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.2F);
                    player.sendSystemMessage(Component.literal("[Зоны] ").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal("Зона \"" + existing.name() + "\" [ID: " + existing.id() + "] удалена!").withStyle(ChatFormatting.YELLOW)));
                } else {
                    player.sendSystemMessage(Component.literal("[Зоны] В этой точке нет зарегистрированной зоны.").withStyle(ChatFormatting.RED));
                }
            });
            c.setPacketHandled(true);
        }
    }

    /** Server -> client: all zones, drawn while the tool is held. */
    public record SyncZones(List<QuestZone> zones) {
        static void encode(SyncZones m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.zones.size());
            for (QuestZone z : m.zones) {
                z.write(buf);
            }
        }

        static SyncZones decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<QuestZone> zones = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                zones.add(QuestZone.read(buf));
            }
            return new SyncZones(zones);
        }

        static void handle(SyncZones m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.voltyx.efw.zone.client.ZoneClient.setZones(m.zones)));
            c.setPacketHandled(true);
        }
    }
}
