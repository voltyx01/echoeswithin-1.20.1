package com.voltyx.efw.blood;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.voltyx.efw.EFW;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** /blood max|clear|set [target]: sets the contamination of players (operators only). */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class BloodCommand {
    private BloodCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("blood").requires(s -> s.hasPermission(2))
                .then(Commands.literal("max").executes(c -> apply(c.getSource().getPlayerOrException(), 1.0f))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> apply(EntityArgument.getPlayer(c, "target"), 1.0f))))
                .then(Commands.literal("clear").executes(c -> apply(c.getSource().getPlayerOrException(), 0.0f))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> apply(EntityArgument.getPlayer(c, "target"), 0.0f))))
                .then(Commands.literal("set").then(Commands.argument("level", FloatArgumentType.floatArg(0.0f, 1.0f))
                        .executes(c -> apply(c.getSource().getPlayerOrException(), FloatArgumentType.getFloat(c, "level")))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> apply(EntityArgument.getPlayer(c, "target"), FloatArgumentType.getFloat(c, "level")))))));
    }

    private static int apply(ServerPlayer target, float level) {
        BloodManager.setBloodLevel(target.getUUID(), level);
        BloodEvents.syncNow(target);
        target.sendSystemMessage(Component.literal("Blood: " + Math.round(level * 100) + "%"));
        return 1;
    }
}
