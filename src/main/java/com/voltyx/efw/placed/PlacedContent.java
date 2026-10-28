package com.voltyx.efw.placed;

import com.voltyx.efw.EFW;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/** The placed item block, the placer tool and their packets. */
public final class PlacedContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, EFW.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EFW.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, EFW.MOD_ID);

    public static final RegistryObject<Block> PLACED_ITEM = BLOCKS.register("placed_item", PlacedItemBlock::new);
    public static final RegistryObject<BlockEntityType<PlacedItemBlockEntity>> PLACED_ITEM_BE = BLOCK_ENTITIES.register("placed_item",
            () -> BlockEntityType.Builder.of(PlacedItemBlockEntity::new, PLACED_ITEM.get()).build(null));
    public static final RegistryObject<Item> PLACER_TOOL = ITEMS.register("placer_tool", PlacerToolItem::new);

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(EFW.MOD_ID, "placed"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private PlacedContent() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(PlacedContent::onCreativeTab);
        int id = 0;
        CHANNEL.registerMessage(id++, OpenConfig.class, (m, b) -> b.writeBlockPos(m.pos), b -> new OpenConfig(b.readBlockPos()), OpenConfig::handle);
        CHANNEL.registerMessage(id++, OpenInspect.class, (m, b) -> b.writeItem(m.stack), b -> new OpenInspect(b.readItem()), OpenInspect::handle);
        CHANNEL.registerMessage(id++, UpdatePlacedItem.class, UpdatePlacedItem::encode, UpdatePlacedItem::decode, UpdatePlacedItem::handle);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(PLACER_TOOL);
        }
    }

    /** Server -> client: the item was just put down, open its settings. */
    public record OpenConfig(BlockPos pos) {
        static void handle(OpenConfig m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.voltyx.efw.placed.client.PlacedClient.openConfigWhenReady(m.pos)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> client: an item that is worth a look was picked up (PacketOpenInspect). */
    public record OpenInspect(ItemStack stack) {
        static void handle(OpenInspect m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.voltyx.efw.placed.client.PlacedClient.inspect(m.stack)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Client -> server: the settings of a placed item (PacketUpdatePlacedItem). */
    public record UpdatePlacedItem(BlockPos pos, float offsetX, float offsetY, float offsetZ, float yaw, float pitch, float roll, float scale,
                                   boolean locked, float iconX, float iconY, float iconZ) {
        static void encode(UpdatePlacedItem m, FriendlyByteBuf b) {
            b.writeBlockPos(m.pos);
            b.writeFloat(m.offsetX).writeFloat(m.offsetY).writeFloat(m.offsetZ);
            b.writeFloat(m.yaw).writeFloat(m.pitch).writeFloat(m.roll).writeFloat(m.scale);
            b.writeBoolean(m.locked);
            b.writeFloat(m.iconX).writeFloat(m.iconY).writeFloat(m.iconZ);
        }

        static UpdatePlacedItem decode(FriendlyByteBuf b) {
            return new UpdatePlacedItem(b.readBlockPos(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(),
                    b.readFloat(), b.readFloat(), b.readBoolean(), b.readFloat(), b.readFloat(), b.readFloat());
        }

        static void handle(UpdatePlacedItem m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player == null || !player.level().isLoaded(m.pos) || player.distanceToSqr(m.pos.getCenter()) > 100.0
                        || !(player.getMainHandItem().getItem() instanceof PlacerToolItem || player.getOffhandItem().getItem() instanceof PlacerToolItem)
                        || !(player.level().getBlockEntity(m.pos) instanceof PlacedItemBlockEntity placed)) {
                    return;
                }
                placed.setLayout(m.offsetX, m.offsetY, m.offsetZ, m.yaw, m.pitch, m.roll, m.scale, m.locked, m.iconX, m.iconY, m.iconZ);
                BlockState state = placed.getBlockState();
                player.level().sendBlockUpdated(m.pos, state, state, 3);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
