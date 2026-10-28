package com.voltyx.efw.loot.client;

import com.voltyx.efw.EFW;
import com.voltyx.efw.tech.TechContent;
import com.voltyx.efw.tech.TechNetwork;
import com.voltyx.efw.loot.LootConfig;
import com.voltyx.efw.loot.LootContainers;
import com.voltyx.efw.loot.LootParts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * The search of a container (the container part of ClientLootingManager): the container opens (the server opens it with its own
 * lid, doors and sound, only without the window; a container that cannot open plays a door sound here), a short pause, then the
 * square ring around the crosshair fills while use is held on the block and the window opens at the end. Letting go or looking
 * away closes the door again. The ring is only drawn once the search has begun; looking at a container shows no ring.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class ContainerLooting {
    private static final int OPEN_PAUSE_TICKS = 6;
    private static final int REQUIRED_TICKS = 30;
    private static final long COOLDOWN_MS = 1500L;
    private static final long SUPPRESS_MS = 1500L;
    private static final double SUPPRESS_RADIUS_SQ = 16.0;
    private static final long STATE_TTL_MS = 5000L;
    private static final float RING_SIZE = 10.0f;
    private static final float RING_WIDTH = 2.5f;
    private static final int RING_TRACK = 0x80000000;
    private static final int RING_FILL = 0xFFFFFFFF;

    private static BlockPos target;
    private static int progress;
    private static int pause;
    private static SoundInstance activeSound;
    /** The server opened the container itself (its lid, doors and sound): no door sounds of our own. */
    private static boolean serverOpened;

    private static BlockPos lastDonePos;
    private static long lastDoneTime;
    private static ResourceLocation openSoundId;
    private static BlockPos suppressPos;
    private static long suppressUntil;

    /** Ticks the search takes for the local player (SEVEN makes it faster or slower). */
    private static int requiredTicks() {
        net.minecraft.client.player.LocalPlayer p = net.minecraft.client.Minecraft.getInstance().player;
        double speed = p == null ? 1.0 : com.voltyx.efw.sins.stat.SinStats.mult(p, com.voltyx.efw.sins.stat.Stat.LOOT_SPEED);
        return Math.max(4, (int) Math.round(REQUIRED_TICKS / speed));
    }

    /** The server's answers: is the container still to be searched, and when was that asked. */
    private static final Map<BlockPos, Boolean> WILD = new HashMap<>();
    private static final Map<BlockPos, Long> ASKED = new HashMap<>();
    private static Level stateLevel;
    private static BlockPos hovered;

    private ContainerLooting() {
    }

    public static void state(BlockPos pos, boolean wild) {
        WILD.put(pos, wild);
        if (!wild) {
            markSearched(pos);
        }
    }

    /** Searched once, searched as a whole: the other parts lose their loupe and ring without asking again. */
    private static void markSearched(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (BlockPos part : LootParts.parts(mc.level, pos)) {
                WILD.put(part, false);
            }
        }
    }

    /** What the server last said about the container at {@code pos}: true still to be searched, null not known yet. */
    static Boolean wild(BlockPos pos) {
        return WILD.get(pos);
    }

    /** The container being searched right now, or null. */
    static BlockPos searching() {
        return target;
    }

    /** Asks the server about a container unless that was done a moment ago. */
    static void ask(BlockPos pos) {
        long now = System.currentTimeMillis();
        Long asked = ASKED.get(pos);
        if (asked == null || now - asked > STATE_TTL_MS) {
            ASKED.put(pos.immutable(), now);
            TechNetwork.sendToServer(new TechNetwork.QueryContainerLoot(pos));
        }
    }

    public static void start(BlockPos pos, boolean opened) {
        if (pos.equals(lastDonePos) && System.currentTimeMillis() - lastDoneTime < COOLDOWN_MS) {
            return;
        }
        if (!pos.equals(target)) {
            stopSound();
            target = pos;
            progress = 0;
            serverOpened = opened;
            SoundEvent open = opened ? null : sound(pos, true);
            openSoundId = open == null ? null : ForgeRegistries.SOUND_EVENTS.getKey(open);
            pause = open == null && !opened ? 0 : OPEN_PAUSE_TICKS;
            play(open, pos);
        }
    }

    private static void play(SoundEvent event, BlockPos pos) {
        if (event != null) {
            Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(event, SoundSource.BLOCKS, 0.8F, 1.0F,
                    SoundInstance.createUnseededRandom(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        }
    }

    /**
     * The door sound: furniture by its kind (null when the mod has none), a barrel's or a chest's for the rest; for a container
     * that is neither a chest nor a barrel the chest sound only when the config allows it.
     */
    private static SoundEvent sound(BlockPos pos, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        BlockEntity be = mc.level == null ? null : mc.level.getBlockEntity(pos);
        if (be == null) {
            return null;
        }
        if (isCyst(be)) {
            // SRP's cysts and sacs are flesh: slid open and shut with its own wet sounds
            SoundEvent flesh = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("srparasites", open ? "lite_flesh.slide" : "block.flesh.dig"));
            return flesh != null ? flesh : SoundEvents.SLIME_BLOCK_STEP;
        }
        if (!be.getClass().getName().startsWith("com.mrcrayfish.furniture")) {
            if (LootParts.isVehicle(be.getBlockState())) {
                return open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE;
            }
            if (be instanceof BarrelBlockEntity) {
                return open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE;
            }
            if (!(be instanceof ChestBlockEntity) && !LootConfig.CHEST_SOUND_FOR_UNKNOWN.get()) {
                return null;
            }
            return open ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE;
        }
        String type = "cabinet";
        for (Class<?> c = be.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            String n = c.getSimpleName();
            if (n.contains("KitchenDrawer")) {
                type = "kitchen_drawer";
            } else if (n.contains("Drawer")) {
                type = "drawer";
            } else if (n.contains("Freezer")) {
                type = "freezer";
            } else if (n.contains("Fridge")) {
                type = "fridge";
            } else if (n.contains("Cooler")) {
                type = "cooler";
            } else {
                continue;
            }
            break;
        }
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("refurbished_furniture", "block." + type + (open ? ".open" : ".close")));
    }

    /** A cyst or a bladder sac of SRP: the containers that are flesh. */
    private static boolean isCyst(BlockEntity be) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(be.getBlockState().getBlock());
        return id != null && id.getNamespace().equals("srparasites") && (id.getPath().contains("cyst") || id.getPath().contains("canister_sac"));
    }

    /** The sound of the search itself: the chewing of a cyst, or the usual. */
    private static SoundEvent progressSound(BlockPos pos) {
        BlockEntity be = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getBlockEntity(pos);
        if (be != null && isCyst(be)) {
            SoundEvent eating = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("srparasites", "cyst.eating"));
            if (eating != null) {
                return eating;
            }
        }
        return TechContent.LOOT_PROGRESS.get();
    }

    private static void stopSound() {
        if (activeSound != null) {
            Minecraft.getInstance().getSoundManager().stop(activeSound);
            activeSound = null;
        }
    }

    private static void reset() {
        stopSound();
        target = null;
        progress = 0;
        pause = 0;
    }

    /** No swinging or repeated use on the container being searched. */
    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (target != null && e.isUseItem() && mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(target)) {
            e.setSwingHand(false);
            e.setCanceled(true);
        }
    }

    /** Keeps track of the container under the crosshair and asks the server about it once in a while. */
    private static void trackHover(Minecraft mc) {
        if (mc.level != stateLevel) {
            stateLevel = mc.level;
            WILD.clear();
            ASKED.clear();
        }
        hovered = null;
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (!LootContainers.isLootContainer(mc.level.getBlockEntity(pos))) {
            return;
        }
        hovered = pos;
        ask(pos);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        trackHover(mc);
        if (target == null) {
            return;
        }
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        boolean looking = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK
                && ((BlockHitResult) mc.hitResult).getBlockPos().equals(target);
        if (!mc.options.keyUse.isDown() || !looking) {
            if (!serverOpened) {
                play(sound(target, false), target);
            }
            TechNetwork.sendToServer(new TechNetwork.AbortContainerLoot());
            reset();
            return;
        }
        if (pause > 0) {
            pause--;
            return;
        }
        progress++;
        if (progress == 1) {
            activeSound = new SimpleSoundInstance(progressSound(target), SoundSource.BLOCKS, 1.0F, 1.0F,
                    SoundInstance.createUnseededRandom(), target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
            mc.getSoundManager().play(activeSound);
        }
        if (progress >= requiredTicks()) {
            TechNetwork.sendToServer(new TechNetwork.ContainerLooted(target));
            WILD.put(target, false);
            markSearched(target);
            // the window opens with its own door sound, which the search already played (an opened container stays open, silent)
            if (!serverOpened) {
                suppressPos = target;
                suppressUntil = System.currentTimeMillis() + SUPPRESS_MS;
            }
            lastDonePos = target;
            lastDoneTime = System.currentTimeMillis();
            reset();
        }
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent e) {
        SoundInstance sound = e.getSound();
        if (sound == null || suppressPos == null) {
            return;
        }
        if (System.currentTimeMillis() > suppressUntil) {
            suppressPos = null;
            return;
        }
        double dx = sound.getX() - (suppressPos.getX() + 0.5);
        double dy = sound.getY() - (suppressPos.getY() + 0.5);
        double dz = sound.getZ() - (suppressPos.getZ() + 0.5);
        if (dx * dx + dy * dy + dz * dz <= SUPPRESS_RADIUS_SQ
                && (sound.getLocation().equals(openSoundId) || sound.getLocation().getPath().contains(".open"))) {
            e.setSound(null);
            suppressPos = null;
        }
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post e) {
        if (e.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) {
            return;
        }
        boolean searching = target != null;
        if (!searching) {
            return;
        }
        GuiGraphics g = e.getGuiGraphics();
        float offset = LootConfig.RING_OFFSET.get().floatValue();
        float cx = g.guiWidth() / 2 + offset;
        float cy = g.guiHeight() / 2 + offset;
        ring(g, cx, cy, 1.0f, RING_TRACK);
        ring(g, cx, cy, Mth.clamp(progress / (float) requiredTicks(), 0.0f, 1.0f), RING_FILL);
        Component text = Component.translatable("gui.efw.looting");
        var font = Minecraft.getInstance().font;
        g.drawString(font, text, (int) (cx - font.width(text) / 2.0f), (int) (cy + RING_SIZE + 12), 0xFFFFFF, true);
    }

    /** The square ring round the crosshair, drawn clockwise from the top left corner up to {@code progress}. */
    private static void ring(GuiGraphics g, float cx, float cy, float progress, int colour) {
        if (progress <= 0.0f) {
            return;
        }
        float s = RING_SIZE;
        float t = RING_WIDTH;
        float d = Math.min(progress, 1.0f) * 8.0f * s;
        rect(g, cx - s, cy - s, cx - s + Math.min(d, 2.0f * s), cy - s + t, colour);
        if (d > 2.0f * s) {
            rect(g, cx + s - t, cy - s + t, cx + s, cy - s + Math.min(d - 2.0f * s, 2.0f * s), colour);
        }
        if (d > 4.0f * s) {
            rect(g, cx + s - t - Math.min(d - 4.0f * s, 2.0f * s - t), cy + s - t, cx + s - t, cy + s, colour);
        }
        if (d > 6.0f * s) {
            rect(g, cx - s, cy + s - t - Math.min(d - 6.0f * s, 2.0f * s - 2.0f * t), cx - s + t, cy + s - t, colour);
        }
    }

    private static void rect(GuiGraphics g, float x0, float y0, float x1, float y1, int colour) {
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x0, y0, 0.0f);
        g.pose().scale(x1 - x0, y1 - y0, 1.0f);
        g.fill(0, 0, 1, 1, colour);
        g.pose().popPose();
    }
}
