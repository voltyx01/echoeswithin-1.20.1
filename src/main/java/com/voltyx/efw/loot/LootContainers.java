package com.voltyx.efw.loot;

import com.voltyx.efw.EFW;
import com.voltyx.efw.tech.TechNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Searching of storage found in the world (port of LootContainerHandler): a container that was not placed by a player and was not
 * searched yet does not open at once. The server tells the client to run the search bar, and the window opens when the client
 * reports that it was finished. Both marks live in the persistent data of the block entity, so they travel with it into structures.
 * Searched are the storage furniture of Refurbished Furniture and every vanilla-style loot container: chests and barrels of Lost
 * Cities and the crates, fridges and bins of the MCreator mods (Doomsday Decoration, Survival Instinct and the like) are all
 * RandomizableContainerBlockEntity.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class LootContainers {
    public static final String PLAYER_PLACED = "efw_player_placed";
    public static final String UNLOCKED = "efw_loot_unlocked";

    /**
     * The storage blocks of Refurbished Furniture: cabinets, drawers, crates, fridges, coolers, mailboxes (all of them are rowed
     * storage) and the freezer. The generator, the stove, the microwave and the other machines are containers too, but not searched.
     */
    private static final String[] STORAGE_BASES = {
            "com.mrcrayfish.furniture.refurbished.blockentity.RowedStorageBlockEntity",
            "com.mrcrayfish.furniture.refurbished.blockentity.FreezerBlockEntity",
            // Doomsday Functionality gives the props of Doomsday Decoration (cars, cabinets, crates, remains) an inventory
            DoomsdayLoot.BLOCK_ENTITY
    };

    /**
     * Loot containers that hold no loot: the hopper and the dispenser (the dropper is one) move items, and MCreator gives its
     * technical blocks (Horror Element's atmosphere and sound blocks) an inventory they never show.
     */
    private static final String[] NOT_STORAGE = {"Atmosphere", "SoundBlock", "Spawner", "Furnace", "Generator", "Machine"};

    private static final Map<Class<?>, Boolean> CACHE = new ConcurrentHashMap<>();

    private LootContainers() {
    }

    public static boolean isLootContainer(BlockEntity be) {
        if (be == null) {
            return false;
        }
        var block = be.getBlockState().getBlock();
        if (LootConfig.blacklisted(block)) {
            return false;
        }
        if (LootConfig.whitelisted(block)) {
            return be instanceof net.minecraft.world.Container;
        }
        return CACHE.computeIfAbsent(be.getClass(), LootContainers::matches);
    }

    private static boolean matches(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (String base : STORAGE_BASES) {
                if (c.getName().equals(base)) {
                    return true;
                }
            }
        }
        if (!RandomizableContainerBlockEntity.class.isAssignableFrom(type)
                || HopperBlockEntity.class.isAssignableFrom(type) || DispenserBlockEntity.class.isAssignableFrom(type)) {
            return false;
        }
        for (String word : NOT_STORAGE) {
            if (type.getSimpleName().contains(word)) {
                return false;
            }
        }
        return true;
    }

    /** Not placed by a player and not searched yet: opening it runs the search. */
    public static boolean isWild(BlockEntity be) {
        var data = be.getPersistentData();
        return !data.getBoolean(PLAYER_PLACED) && !data.getBoolean(UNLOCKED);
    }

    /** The whole object is still to be searched: none of its parts was searched or placed by a player. */
    public static boolean isWildAt(Level level, BlockPos pos) {
        BlockEntity self = level.getBlockEntity(pos);
        if (!isLootContainer(self) || !isWild(self)) {
            return false;
        }
        for (BlockPos part : LootParts.parts(level, pos)) {
            BlockEntity be = level.getBlockEntity(part);
            if (be != null && isLootContainer(be) && !isWild(be)) {
                return false;
            }
        }
        return true;
    }

    /** A block placed by a player is theirs: no search. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Player)) {
            return;
        }
        BlockEntity be = e.getLevel().getBlockEntity(e.getPos());
        if (isLootContainer(be)) {
            be.getPersistentData().putBoolean(PLAYER_PLACED, true);
            be.setChanged();
        }
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        BlockPos pos = LootParts.lootPos(e.getLevel(), e.getPos());
        if (pos == null || !isWildAt(e.getLevel(), pos)) {
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        // the event comes once for each hand; only the main hand starts the search
        if (e.getHand() == InteractionHand.MAIN_HAND) {
            boolean opened = beginSearch(player, pos);
            TechNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TechNetwork.StartContainerLoot(e.getPos(), opened));
        }
    }

    /** The client looks at a container and asks whether it still has to be searched (for the ring around the crosshair). */
    public static void query(ServerPlayer player, BlockPos pos) {
        Level level = player.level();
        if (!level.isLoaded(pos) || player.distanceToSqr(pos.getCenter()) > 100.0) {
            return;
        }
        BlockPos loot = LootParts.lootPos(level, pos);
        boolean wild = loot != null && isWildAt(level, loot);
        TechNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TechNetwork.ContainerLootState(pos, wild));
    }

    /** The client finished the search bar: every part of the container is open for good and its window opens. */
    public static void searched(ServerPlayer player, BlockPos clicked) {
        Level level = player.level();
        BlockPos pos = level.isLoaded(clicked) && player.distanceToSqr(clicked.getCenter()) <= 64.0 ? LootParts.lootPos(level, clicked) : null;
        if (pos == null || !isWildAt(level, pos)) {
            endSearch(player);
            return;
        }
        for (BlockPos part : LootParts.parts(level, pos)) {
            BlockEntity be = level.getBlockEntity(part);
            if (be != null && isLootContainer(be)) {
                be.getPersistentData().putBoolean(UNLOCKED, true);
                be.setChanged();
            }
        }
        if (!DoomsdayLoot.open(player, level.getBlockEntity(clicked), clicked)) {
            BlockState state = level.getBlockState(clicked);
            state.use(level, player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false));
        }
        com.voltyx.efw.sins.stat.SinHooks.onSearched(player, level, LootParts.parts(level, pos));
        // the window holds the container open now: the search lets go of it only afterwards, so it does not shut and reopen
        endSearch(player);
    }

    // ---- the search holds the container open without its window -------------------------------------------------------------

    private record Search(Level level, List<BlockPos> parts, long started, boolean opened) {
    }

    private static final Map<UUID, Search> SEARCHES = new ConcurrentHashMap<>();
    private static final long SEARCH_TIMEOUT_TICKS = 20 * 20;

    /**
     * Opens every part of the container the way its window would (lid, doors, the block's own sound and animation), only without
     * the window. @return whether anything opened by itself; otherwise the client plays a door sound of its own.
     */
    public static boolean beginSearch(ServerPlayer player, BlockPos pos) {
        Level level = player.level();
        Search running = SEARCHES.get(player.getUUID());
        // use held on the block repeats the click: the running search goes on (reopening would shut and open the lid every time)
        if (running != null && running.level == level && running.parts.contains(pos)) {
            return running.opened;
        }
        endSearch(player);
        List<BlockPos> parts = LootParts.parts(level, pos);
        boolean opened = false;
        for (BlockPos part : parts) {
            if (level.getBlockEntity(part) instanceof Container c && LootParts.opensItself(c)) {
                opened = true;
            }
        }
        SEARCHES.put(player.getUUID(), new Search(level, parts, level.getGameTime(), opened));
        for (BlockPos part : parts) {
            if (level.getBlockEntity(part) instanceof Container c && LootParts.opensItself(c)) {
                c.startOpen(player);
            }
        }
        return opened;
    }

    /** The search ended (finished, given up, too far, logged out): the parts are let go again. */
    public static void endSearch(ServerPlayer player) {
        Search s = SEARCHES.remove(player.getUUID());
        if (s == null) {
            return;
        }
        for (BlockPos part : s.parts) {
            if (s.level.isLoaded(part) && s.level.getBlockEntity(part) instanceof Container c && LootParts.opensItself(c)) {
                c.stopOpen(player);
            }
        }
    }

    /** Players searching the container at this position, for the openers count of its lid. */
    public static int searchersAt(Level level, BlockPos pos) {
        if (SEARCHES.isEmpty() || level.isClientSide()) {
            return 0;
        }
        int n = 0;
        for (Search s : SEARCHES.values()) {
            if (s.level == level && s.parts.contains(pos)) {
                n++;
            }
        }
        return n;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || SEARCHES.isEmpty() || e.getServer().getTickCount() % 10 != 0) {
            return;
        }
        for (Map.Entry<UUID, Search> entry : List.copyOf(SEARCHES.entrySet())) {
            ServerPlayer player = e.getServer().getPlayerList().getPlayer(entry.getKey());
            Search s = entry.getValue();
            if (player == null) {
                SEARCHES.remove(entry.getKey());
                continue;
            }
            boolean far = player.level() != s.level || player.distanceToSqr(s.parts.get(0).getCenter()) > 64.0;
            if (far || s.level.getGameTime() - s.started > SEARCH_TIMEOUT_TICKS) {
                endSearch(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer player) {
            endSearch(player);
        }
    }
}
