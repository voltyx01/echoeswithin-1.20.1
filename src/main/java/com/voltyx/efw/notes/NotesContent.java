package com.voltyx.efw.notes;

import com.voltyx.efw.EFW;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/** Note and diary items and the creative note settings packet. */
public final class NotesContent {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EFW.MOD_ID);
    public static final RegistryObject<Item> NOTE = ITEMS.register("note", NoteItem::new);
    public static final RegistryObject<Item> C_DIARY = ITEMS.register("c_diary", DiaryItem::new);

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(EFW.MOD_ID, "notes"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private NotesContent() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(NotesContent::onCreativeTab);
        CHANNEL.registerMessage(0, ConfigureNote.class, ConfigureNote::encode, ConfigureNote::decode, ConfigureNote::handle);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(NOTE);
            event.accept(C_DIARY);
        }
    }

    /** Client -> server, creative only: sets the text id, paper variant and quest flag of the held note. */
    public record ConfigureNote(boolean offHand, int noteId, int variant, boolean quest) {
        static void encode(ConfigureNote m, FriendlyByteBuf buf) {
            buf.writeBoolean(m.offHand);
            buf.writeInt(m.noteId);
            buf.writeInt(m.variant);
            buf.writeBoolean(m.quest);
        }

        static ConfigureNote decode(FriendlyByteBuf buf) {
            return new ConfigureNote(buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readBoolean());
        }

        static void handle(ConfigureNote m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer player = c.getSender();
            c.enqueueWork(() -> {
                if (player != null && player.isCreative()) {
                    ItemStack stack = player.getItemInHand(m.offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                    if (stack.getItem() instanceof NoteItem) {
                        NoteItem.setNoteData(stack, Math.max(1, m.noteId), Math.max(1, Math.min(10, m.variant)), m.quest);
                        player.containerMenu.broadcastChanges();
                    }
                }
            });
            c.setPacketHandled(true);
        }
    }
}
