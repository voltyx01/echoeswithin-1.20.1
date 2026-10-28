package com.voltyx.efw.sins;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.voltyx.efw.EFW;
import com.voltyx.efw.sins.stat.SinDefs;
import com.voltyx.efw.sins.stat.SinEffect;
import com.voltyx.efw.sins.stat.SinStats;
import com.voltyx.efw.sins.stat.Stat;
import com.voltyx.efw.sins.stat.Trigger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * /seven — testing the progression: pages, points, cards, reset, the stats as they are now, reloading the content.
 * The chat of the pack is replaced, so the answers also go to the log.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class SinCommand {
    private SinCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("seven").requires(s -> s.hasPermission(2))
                .then(Commands.literal("reload").executes(c -> {
                    String problem = SinDefs.loadFromConfig();
                    for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) {
                        SinNetwork.sendToPlayer(p, new SinNetwork.SyncDefs(SinDefs.bundle()));
                    }
                    return reply(c, problem == null ? "SEVEN: content reloaded" : "SEVEN: " + problem);
                }))
                .then(Commands.literal("reset").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    data(p).reset();
                    SinEvents.sync(p);
                    return reply(c, "SEVEN: reset");
                }))
                .then(Commands.literal("pages").then(Commands.argument("n", IntegerArgumentType.integer(0, 100)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    data(p).setPages(IntegerArgumentType.getInteger(c, "n"));
                    SinEvents.sync(p);
                    return reply(c, "SEVEN: pages = " + data(p).getPages() + ", points = " + data(p).skillPoints(SinDefs.get()));
                })))
                .then(Commands.literal("points").then(Commands.argument("n", IntegerArgumentType.integer(-20, 20)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    data(p).addBonusPoints(IntegerArgumentType.getInteger(c, "n"));
                    SinEvents.sync(p);
                    return reply(c, "SEVEN: points = " + data(p).skillPoints(SinDefs.get()));
                })))
                .then(Commands.literal("card").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    String id = StringArgumentType.getString(c, "id");
                    if (SinDefs.get().card(id) == null) {
                        return reply(c, "SEVEN: no card " + id);
                    }
                    data(p).takeCard(id);
                    SinEvents.sync(p);
                    return reply(c, "SEVEN: " + id + " rank " + data(p).rank(id));
                })))
                .then(Commands.literal("fire").then(Commands.argument("trigger", StringArgumentType.word()).executes(c -> {
                    Trigger t = Trigger.byId(StringArgumentType.getString(c, "trigger"));
                    if (t == null) {
                        return reply(c, "SEVEN: no such trigger");
                    }
                    SinStats.fire(c.getSource().getPlayerOrException(), t);
                    return reply(c, "SEVEN: fired " + t.id);
                })))
                .then(Commands.literal("stats").executes(SinCommand::stats)));
    }

    private static int stats(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        StringBuilder b = new StringBuilder("SEVEN stats of ").append(p.getName().getString()).append(":");
        for (Stat s : Stat.values()) {
            if (s.kind == Stat.Kind.INSTANT) {
                continue;
            }
            double v = SinStats.value(p, s);
            if (Math.abs(v) > 1.0E-6) {
                b.append("\n  ").append(s.id).append(" = ").append(Math.round(v * 1000) / 1000.0);
            }
        }
        b.append("\n  effects: ").append(SinStats.effects(p).size());
        for (SinEffect e : SinStats.effects(p)) {
            EFW.LOGGER.info("SEVEN {} | {}", e.uid(), e.describe());
        }
        return reply(c, b.toString());
    }

    private static SinData data(ServerPlayer p) {
        return SinData.get(p);
    }

    private static int reply(CommandContext<CommandSourceStack> c, String text) {
        EFW.LOGGER.info(text);
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
