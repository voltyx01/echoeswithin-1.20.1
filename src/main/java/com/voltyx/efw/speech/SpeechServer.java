package com.voltyx.efw.speech;

import com.voltyx.efw.EFW;
import com.voltyx.efw.gender.main.GenderPlayer;
import com.voltyx.efw.gender.main.WildfireGender;
import com.voltyx.efw.network.EfwNetwork;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the speech systems: a chat message does not go to the whole chat but only to the players within hearing range, as a
 * bubble above the head of the speaker; personal replicas are sent to a single player.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class SpeechServer {
    private static final Map<UUID, Long> LAST_MESSAGE_TICK = new HashMap<>();

    private SpeechServer() {
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_MESSAGE_TICK.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer sender = event.getPlayer();
        String text = sanitize(event.getRawText());
        if (text.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        long tick = sender.level().getGameTime();
        Long previous = LAST_MESSAGE_TICK.put(sender.getUUID(), tick);
        if (previous != null && tick - previous < 4L) {
            return;
        }
        EFW.LOGGER.info("<{}> {}", sender.getGameProfile().getName(), text);

        float pitch = getPlayerPitch(sender.getUUID());
        EfwNetwork.PublicSpeech packet = new EfwNetwork.PublicSpeech(sender.getId(), sender.getGameProfile().getName(), text, pitch);
        double radiusSq = SpeechConfig.publicHiddenDistance() * (double) SpeechConfig.publicHiddenDistance();
        for (ServerPlayer recipient : sender.server.getPlayerList().getPlayers()) {
            if (recipient.level() == sender.level() && recipient.distanceToSqr(sender) <= radiusSq) {
                EfwNetwork.sendToPlayer(recipient, packet);
            }
        }
    }

    /** A replica above the hotbar of one player (translation key and arguments are resolved by the client). */
    public static void sendPersonal(ServerPlayer player, String translationKey, String... arguments) {
        if (player == null || translationKey == null || translationKey.isEmpty()) {
            return;
        }
        String[] args = arguments == null ? new String[0] : arguments;
        int count = Math.min(8, args.length);
        String[] limited = new String[count];
        System.arraycopy(args, 0, limited, 0, count);
        EfwNetwork.sendToPlayer(player, new EfwNetwork.PersonalSpeech(translationKey, limited, getPlayerPitch(player.getUUID())));
    }

    public static float getPlayerPitch(UUID uuid) {
        long hash = uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 23);
        float variation = (((hash >>> 11) & 0xFFFFL) / 65535.0F - 0.5F) * SpeechConfig.publicPitchVariation();
        float pitch = 1.0F + variation;
        try {
            GenderPlayer gender = WildfireGender.getPlayerById(uuid);
            if (gender != null && gender.getGender() == GenderPlayer.Gender.FEMALE) {
                pitch += SpeechConfig.femalePitchOffset();
            }
        } catch (Throwable ignored) {
            // no gender, no shift
        }
        return Math.max(0.75F, Math.min(1.25F, pitch));
    }

    static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = ChatFormatting.stripFormatting(raw).replace('\r', ' ').replace('\n', ' ').replace('\u0000', ' ').trim();
        int codePoints = cleaned.codePointCount(0, cleaned.length());
        if (codePoints > SpeechConfig.maxMessageLength()) {
            cleaned = cleaned.substring(0, cleaned.offsetByCodePoints(0, SpeechConfig.maxMessageLength()));
        }
        return cleaned;
    }

    /** /speechtest: a personal replica for yourself. */
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("speechtest").executes(c -> {
            sendPersonal(c.getSource().getPlayerOrException(), "efw.speech.test_personal");
            return 1;
        }));
    }
}
