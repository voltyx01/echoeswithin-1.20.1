package com.voltyx.efw.mixin;

import com.voltyx.efw.loot.DoomsdayLoot;
import java.lang.reflect.Field;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Doomsday prop is opened only after the EFW search (see DoomsdayLoot); the packet still does its own distance and config checks. */
@Pseudo
@Mixin(targets = "com.raiiiden.doomsdayfunctionality.network.OpenLootableBlockPacket", remap = false)
public abstract class DoomsdayOpenPacketMixin {
    private static Field efw$pos;

    @Inject(method = "lambda$handle$0", at = @At(value = "INVOKE",
            target = "Lcom/raiiiden/doomsdayfunctionality/blockentity/DoomsdayBlockEntity;tryLoadLoot(Lnet/minecraft/world/entity/player/Player;)V"),
            cancellable = true, remap = false, require = 0)
    private static void efw$searchFirst(Supplier<NetworkEvent.Context> ctx, @Coerce Object msg, CallbackInfo ci) {
        ServerPlayer player = ctx.get().getSender();
        BlockPos pos = efw$pos(msg);
        if (player != null && pos != null && DoomsdayLoot.interceptOpen(player, pos)) {
            ci.cancel();
        }
    }

    private static BlockPos efw$pos(Object msg) {
        try {
            if (efw$pos == null) {
                Field f = msg.getClass().getDeclaredField("pos");
                f.setAccessible(true);
                efw$pos = f;
            }
            return (BlockPos) efw$pos.get(msg);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
