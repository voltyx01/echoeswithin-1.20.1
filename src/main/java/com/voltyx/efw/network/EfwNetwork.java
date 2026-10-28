package com.voltyx.efw.network;

import com.voltyx.efw.EFW;
import com.voltyx.efw.stamina.PlayerData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** The packets of the stamina / roll system and the smaller systems of the mod. */
public final class EfwNetwork {
    private static final String VERSION = "17";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(EFW.MOD_ID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private EfwNetwork() {
    }

    public static void init() {
        int id = 0;
        CHANNEL.registerMessage(id++, DashRequest.class, DashRequest::encode, DashRequest::decode, DashRequest::handle);
        CHANNEL.registerMessage(id++, SyncStamina.class, SyncStamina::encode, SyncStamina::decode, SyncStamina::handle);
        CHANNEL.registerMessage(id++, SyncCooldown.class, SyncCooldown::encode, SyncCooldown::decode, SyncCooldown::handle);
        CHANNEL.registerMessage(id++, RollStart.class, RollStart::encode, RollStart::decode, RollStart::handle);
        CHANNEL.registerMessage(id++, BloodSync.class, BloodSync::encode, BloodSync::decode, BloodSync::handle);
        CHANNEL.registerMessage(id++, FireWeaponClick.class, FireWeaponClick::encode, FireWeaponClick::decode, FireWeaponClick::handle);
        CHANNEL.registerMessage(id++, GunFireSelect.class, GunFireSelect::encode, GunFireSelect::decode, GunFireSelect::handle);
        CHANNEL.registerMessage(id++, SmolderSync.class, SmolderSync::encode, SmolderSync::decode, SmolderSync::handle);
        CHANNEL.registerMessage(id++, PublicSpeech.class, PublicSpeech::encode, PublicSpeech::decode, PublicSpeech::handle);
        CHANNEL.registerMessage(id++, PersonalSpeech.class, PersonalSpeech::encode, PersonalSpeech::decode, PersonalSpeech::handle);
        CHANNEL.registerMessage(id++, DollActivate.class, DollActivate::encode, DollActivate::decode, DollActivate::handle);
        CHANNEL.registerMessage(id++, DollSync.class, DollSync::encode, DollSync::decode, DollSync::handle);
        CHANNEL.registerMessage(id++, NvgToggle.class, NvgToggle::encode, NvgToggle::decode, NvgToggle::handle);
        CHANNEL.registerMessage(id++, CothVomit.class, CothVomit::encode, CothVomit::decode, CothVomit::handle);
        CHANNEL.registerMessage(id++, com.voltyx.efw.gore.pack.PacketBloodDamage.class, com.voltyx.efw.gore.pack.PacketBloodDamage::encode,
                com.voltyx.efw.gore.pack.PacketBloodDamage::decode, com.voltyx.efw.gore.pack.PacketBloodDamage::handle);
        CHANNEL.registerMessage(id++, EyesDeath.class, EyesDeath::encode, EyesDeath::decode, EyesDeath::handle);
        CHANNEL.registerMessage(id++, HiddenFire.class, HiddenFire::encode, HiddenFire::decode, HiddenFire::handle);
        CHANNEL.registerMessage(id++, SkinUpload.class, SkinUpload::encode, SkinUpload::decode, SkinUpload::handle);
        CHANNEL.registerMessage(id++, SkinData.class, SkinData::encode, SkinData::decode, SkinData::handle);
        CHANNEL.registerMessage(id++, DollCarry.class, DollCarry::encode, DollCarry::decode, DollCarry::handle);
        CHANNEL.registerMessage(id++, RagdollLook.class, RagdollLook::encode, RagdollLook::decode, RagdollLook::handle);
        CHANNEL.registerMessage(id++, HookStun.class, HookStun::encode, HookStun::decode, HookStun::handle);
        CHANNEL.registerMessage(id++, HookStrafe.class, HookStrafe::encode, HookStrafe::decode, HookStrafe::handle);
        CHANNEL.registerMessage(id++, HookFinisher.class, HookFinisher::encode, HookFinisher::decode, HookFinisher::handle);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToTracking(Player player, Object message) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), message);
    }

    /** Client -> server: the player pressed the roll key. */
    public record DashRequest(float dirX, float dirZ) {
        static void encode(DashRequest m, FriendlyByteBuf buf) {
            buf.writeFloat(m.dirX);
            buf.writeFloat(m.dirZ);
        }

        static DashRequest decode(FriendlyByteBuf buf) {
            return new DashRequest(buf.readFloat(), buf.readFloat());
        }

        static void handle(DashRequest m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer player = c.getSender();
            c.enqueueWork(() -> {
                if (player != null) {
                    com.voltyx.efw.stamina.StaminaEvents.requestDash(player, new Vec3(m.dirX, 0, m.dirZ));
                }
            });
            c.setPacketHandled(true);
        }
    }

    /** Server -> client: a chat message of a nearby speaker (bubble above the head). */
    public record PublicSpeech(int entityId, String speaker, String text, float pitch) {
        static void encode(PublicSpeech m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entityId);
            buf.writeUtf(m.speaker, 64);
            buf.writeUtf(m.text, 1024);
            buf.writeFloat(m.pitch);
        }

        static PublicSpeech decode(FriendlyByteBuf buf) {
            return new PublicSpeech(buf.readVarInt(), buf.readUtf(64), buf.readUtf(1024), buf.readFloat());
        }

        static void handle(PublicSpeech m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                    com.voltyx.efw.speech.client.SpeechClientManager.receivePublic(m.entityId, m.speaker, m.text, m.pitch)));
            c.setPacketHandled(true);
        }
    }

    /** Server -> client: a replica above the hotbar of the player (translation key with arguments). */
    public record PersonalSpeech(String key, String[] args, float pitch) {
        static void encode(PersonalSpeech m, FriendlyByteBuf buf) {
            buf.writeUtf(m.key, 256);
            int count = Math.min(8, m.args.length);
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                buf.writeUtf(m.args[i], 512);
            }
            buf.writeFloat(m.pitch);
        }

        static PersonalSpeech decode(FriendlyByteBuf buf) {
            String key = buf.readUtf(256);
            int count = Math.max(0, Math.min(8, buf.readVarInt()));
            String[] args = new String[count];
            for (int i = 0; i < count; i++) {
                args[i] = buf.readUtf(512);
            }
            return new PersonalSpeech(key, args, buf.readFloat());
        }

        static void handle(PersonalSpeech m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                    com.voltyx.efw.speech.client.SpeechClientManager.receivePersonal(m.key, m.args, m.pitch)));
            c.setPacketHandled(true);
        }
    }

    /** Server -> client: stamina value and regeneration delay. */
    public record SyncStamina(double stamina, int regenDelay) {
        static void encode(SyncStamina m, FriendlyByteBuf buf) {
            buf.writeDouble(m.stamina);
            buf.writeInt(m.regenDelay);
        }

        static SyncStamina decode(FriendlyByteBuf buf) {
            return new SyncStamina(buf.readDouble(), buf.readInt());
        }

        static void handle(SyncStamina m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                Player player = Minecraft.getInstance().player;
                PlayerData data = player == null ? null : PlayerData.get(player);
                if (data != null) {
                    data.stamina = m.stamina;
                    data.regenDelay = m.regenDelay;
                }
            }));
            c.setPacketHandled(true);
        }
    }

    /** Server -> client: remaining roll cooldown in ticks. */
    public record SyncCooldown(int cooldown) {
        static void encode(SyncCooldown m, FriendlyByteBuf buf) {
            buf.writeInt(m.cooldown);
        }

        static SyncCooldown decode(FriendlyByteBuf buf) {
            return new SyncCooldown(buf.readInt());
        }

        static void handle(SyncCooldown m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                Player player = Minecraft.getInstance().player;
                PlayerData data = player == null ? null : PlayerData.get(player);
                if (data != null) {
                    data.cooldown = m.cooldown;
                }
            }));
            c.setPacketHandled(true);
        }
    }

    /** Server -> tracking clients: this entity starts a roll in the given direction (plays the animation). */
    public record RollStart(int entityId, float dirX, float dirZ) {
        static void encode(RollStart m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entityId);
            buf.writeFloat(m.dirX);
            buf.writeFloat(m.dirZ);
        }

        static RollStart decode(FriendlyByteBuf buf) {
            return new RollStart(buf.readVarInt(), buf.readFloat(), buf.readFloat());
        }

        static void handle(RollStart m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                Minecraft mc = Minecraft.getInstance();
                Entity e = mc.level == null ? null : mc.level.getEntity(m.entityId);
                if (e instanceof Player player) {
                    com.voltyx.efw.client.RollClient.trigger(player, new Vec3(m.dirX, 0, m.dirZ));
                }
            }));
            c.setPacketHandled(true);
        }
    }

    /** Server -> tracking clients: blood contamination (0..1) of a player. */
    public record BloodSync(java.util.UUID player, float level) {
        static void encode(BloodSync m, FriendlyByteBuf buf) {
            buf.writeUUID(m.player);
            buf.writeFloat(m.level);
        }

        static BloodSync decode(FriendlyByteBuf buf) {
            return new BloodSync(buf.readUUID(), buf.readFloat());
        }

        static void handle(BloodSync m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                    com.voltyx.efw.blood.client.BloodClient.setLevel(m.player, m.level)));
            c.setPacketHandled(true);
        }
    }

    /** Server -> tracking clients: the player carries Saya's doll somewhere (the inventory of others is not sent to clients). */
    public record DollCarry(java.util.UUID player, boolean carries) {
        static void encode(DollCarry m, FriendlyByteBuf buf) {
            buf.writeUUID(m.player);
            buf.writeBoolean(m.carries);
        }

        static DollCarry decode(FriendlyByteBuf buf) {
            return new DollCarry(buf.readUUID(), buf.readBoolean());
        }

        static void handle(DollCarry m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                    com.voltyx.efw.doll.client.DollCarriers.set(m.player, m.carries)));
            c.setPacketHandled(true);
        }
    }

    /**
     * Server -> clients: how a dead player's body looks (Ragdollified). The falling ragdoll is keyed by the dead player's entity id,
     * a settled corpse (ragdollifiedpc) by the corpse entity's UUID ({@code corpse}, else null).
     */
    public record RagdollLook(int ragdollId, @org.jetbrains.annotations.Nullable java.util.UUID corpse, float blood, boolean doll, boolean jacket) {
        static void encode(RagdollLook m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.ragdollId);
            buf.writeBoolean(m.corpse != null);
            if (m.corpse != null) {
                buf.writeUUID(m.corpse);
            }
            buf.writeFloat(m.blood);
            buf.writeBoolean(m.doll);
            buf.writeBoolean(m.jacket);
        }

        static RagdollLook decode(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            java.util.UUID corpse = buf.readBoolean() ? buf.readUUID() : null;
            return new RagdollLook(id, corpse, buf.readFloat(), buf.readBoolean(), buf.readBoolean());
        }

        static void handle(RagdollLook m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                    com.voltyx.efw.compat.client.RagdollBodies.setLook(m.corpse != null ? m.corpse : (Object) m.ragdollId, m.blood, m.doll, m.jacket)));
            c.setPacketHandled(true);
        }
    }

    /** Server -> tracking clients: a creature caught by the chain hook is stunned this many ticks more (it shakes, HookFx). */
    public record HookStun(int entityId, int ticks) {
        static void encode(HookStun m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entityId);
            buf.writeVarInt(m.ticks);
        }

        static HookStun decode(FriendlyByteBuf buf) {
            return new HookStun(buf.readVarInt(), buf.readVarInt());
        }

        static void handle(HookStun m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                    com.voltyx.efw.hook.client.HookFx.stunned(m.entityId, m.ticks)));
            c.setPacketHandled(true);
        }
    }

    /** Server -> the thrower: the finish of the chain combo, take up the shotgun from this hotbar slot and fire into the creature. */
    public record HookFinisher(int targetId, int slot) {
        static void encode(HookFinisher m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.targetId);
            buf.writeByte(m.slot);
        }

        static HookFinisher decode(FriendlyByteBuf buf) {
            return new HookFinisher(buf.readVarInt(), buf.readByte());
        }

        static void handle(HookFinisher m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                    com.voltyx.efw.hook.client.HookFinisherClient.start(m.targetId, m.slot)));
            c.setPacketHandled(true);
        }
    }

    /** Client -> server: while the chain drags him, the thrower steers round the creature (-1 right, 0, 1 left). */
    public record HookStrafe(byte dir) {
        static void encode(HookStrafe m, FriendlyByteBuf buf) {
            buf.writeByte(m.dir);
        }

        static HookStrafe decode(FriendlyByteBuf buf) {
            return new HookStrafe(buf.readByte());
        }

        static void handle(HookStrafe m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer player = c.getSender();
            c.enqueueWork(() -> {
                if (player != null) {
                    com.voltyx.efw.hook.ChainHookEntity hook = com.voltyx.efw.hook.ChainHookEntity.activeHook(player);
                    if (hook != null) {
                        hook.steer(m.dir);
                    }
                }
            });
            c.setPacketHandled(true);
        }
    }

    /**
     * Client -> server: the item on the cursor was clicked onto a weapon slot (wrap, light or soak it). In the creative screen the
     * server does not know the cursor, so the stack and the index in the player inventory ({@code invSlot}, else -1) come along.
     */
    public record FireWeaponClick(int containerId, int slot, int invSlot, net.minecraft.world.item.ItemStack carried) {
        static void encode(FireWeaponClick m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.containerId);
            buf.writeVarInt(m.slot);
            buf.writeVarInt(m.invSlot + 1);
            buf.writeItem(m.carried);
        }

        static FireWeaponClick decode(FriendlyByteBuf buf) {
            return new FireWeaponClick(buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1, buf.readItem());
        }

        static void handle(FireWeaponClick m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer player = c.getSender();
            c.enqueueWork(() -> {
                if (player != null) {
                    com.voltyx.efw.fireweapon.FireWeaponEvents.onSlotClick(player, m.containerId, m.slot, m.invSlot, m.carried);
                }
            });
            c.setPacketHandled(true);
        }
    }

    /** Client -> server: switch the fire mode of the TACZ gun in an inventory slot (the modification screen does not hold it). */
    public record GunFireSelect(int slot) {
        static void encode(GunFireSelect m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.slot);
        }

        static GunFireSelect decode(FriendlyByteBuf buf) {
            return new GunFireSelect(buf.readVarInt());
        }

        static void handle(GunFireSelect m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer player = c.getSender();
            c.enqueueWork(() -> {
                if (player != null) {
                    com.voltyx.efw.compat.TaczCompat.cycleFireMode(player, m.slot);
                }
            });
            c.setPacketHandled(true);
        }
    }

    /** Server -> tracking clients: a mob starts to smolder (rising sparks) for the given ticks. */
    public record SmolderSync(int entityId, int ticks, boolean soaked) {
        static void encode(SmolderSync m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entityId);
            buf.writeVarInt(m.ticks);
            buf.writeBoolean(m.soaked);
        }

        static SmolderSync decode(FriendlyByteBuf buf) {
            return new SmolderSync(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }

        static void handle(SmolderSync m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                    com.voltyx.efw.fireweapon.client.SmolderClient.apply(m.entityId, m.ticks, m.soaked)));
            c.setPacketHandled(true);
        }
    }

    /** Client -> server: the player took the doll into the hand while the blood is at the maximum. */
    public record DollActivate() {
        static void encode(DollActivate m, FriendlyByteBuf buf) {
        }

        static DollActivate decode(FriendlyByteBuf buf) {
            return new DollActivate();
        }

        static void handle(DollActivate m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer player = c.getSender();
            c.enqueueWork(() -> {
                if (player != null) {
                    com.voltyx.efw.doll.DollAbility.handleClientActivationRequest(player);
                }
            });
            c.setPacketHandled(true);
        }
    }

    /** Server -> client: the activation of the doll started (ticks = -1) or the frenzy has this many ticks left (0 = over). */
    public record DollSync(int ticks) {
        static void encode(DollSync m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.ticks + 1);
        }

        static DollSync decode(FriendlyByteBuf buf) {
            return new DollSync(buf.readVarInt() - 1);
        }

        static void handle(DollSync m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                    com.voltyx.efw.doll.client.DollClient.onSync(m.ticks)));
            c.setPacketHandled(true);
        }
    }

    /** Client -> server: the night vision switch was pressed. */
    public record NvgToggle() {
        static void encode(NvgToggle m, FriendlyByteBuf buf) {
        }

        static NvgToggle decode(FriendlyByteBuf buf) {
            return new NvgToggle();
        }

        static void handle(NvgToggle m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer player = c.getSender();
            c.enqueueWork(() -> {
                if (player != null) {
                    com.voltyx.efw.nvg.NvgHelmets.toggle(player);
                }
            });
            c.setPacketHandled(true);
        }
    }

    /** Server -> clients: a fit of vomiting of a player sick with the Call of the Hive (warning to the player alone, fit, stop). */
    public record CothVomit(int entityId, byte phase, int ticks) {
        static void encode(CothVomit m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entityId);
            buf.writeByte(m.phase);
            buf.writeVarInt(m.ticks);
        }

        static CothVomit decode(FriendlyByteBuf buf) {
            return new CothVomit(buf.readVarInt(), buf.readByte(), buf.readVarInt());
        }

        static void handle(CothVomit m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                    com.voltyx.efw.coth.client.CothClient.onVomit(m.entityId, m.phase, m.ticks)));
            c.setPacketHandled(true);
        }
    }

    /** Server -> client: the player died; instant = a TACZ bullet in the head, an explosion or a heavy blow (black at once, sound cut). */
    public record EyesDeath(boolean instant, boolean awake) {
        static void encode(EyesDeath m, FriendlyByteBuf buf) {
            buf.writeBoolean(m.instant);
            buf.writeBoolean(m.awake);
        }

        static EyesDeath decode(FriendlyByteBuf buf) {
            return new EyesDeath(buf.readBoolean(), buf.readBoolean());
        }

        static void handle(EyesDeath m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                    com.voltyx.efw.eyes.client.EyesOpen.died(m.instant, m.awake)));
            c.setPacketHandled(true);
        }
    }

    /** Server -> client: the entity was set on fire by an incendiary TACZ bullet, its flames are not drawn while it burns. */
    public record HiddenFire(int entityId) {
        static void encode(HiddenFire m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entityId);
        }

        static HiddenFire decode(FriendlyByteBuf buf) {
            return new HiddenFire(buf.readVarInt());
        }

        static void handle(HiddenFire m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                    com.voltyx.efw.tacz.client.BulletFireClient.hide(m.entityId)));
            c.setPacketHandled(true);
        }
    }

    /** Client -> server: the skin from the launcher (SkinSync). */
    public record SkinUpload(byte[] png, boolean slim) {
        static void encode(SkinUpload m, FriendlyByteBuf buf) {
            buf.writeByteArray(m.png);
            buf.writeBoolean(m.slim);
        }

        static SkinUpload decode(FriendlyByteBuf buf) {
            return new SkinUpload(buf.readByteArray(com.voltyx.efw.skin.SkinSync.MAX_BYTES), buf.readBoolean());
        }

        static void handle(SkinUpload m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer player = c.getSender();
            c.enqueueWork(() -> {
                if (player != null) {
                    com.voltyx.efw.skin.SkinSync.receive(player, m.png, m.slim);
                }
            });
            c.setPacketHandled(true);
        }
    }

    /** Server -> clients: a player's skin from the launcher, empty when they left. */
    public record SkinData(java.util.UUID player, byte[] png, boolean slim) {
        static void encode(SkinData m, FriendlyByteBuf buf) {
            buf.writeUUID(m.player);
            buf.writeByteArray(m.png);
            buf.writeBoolean(m.slim);
        }

        static SkinData decode(FriendlyByteBuf buf) {
            return new SkinData(buf.readUUID(), buf.readByteArray(com.voltyx.efw.skin.SkinSync.MAX_BYTES), buf.readBoolean());
        }

        static void handle(SkinData m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                    com.voltyx.efw.skin.client.ClientSkins.apply(m.player, m.png, m.slim)));
            c.setPacketHandled(true);
        }
    }
}
