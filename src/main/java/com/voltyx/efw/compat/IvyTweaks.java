package com.voltyx.efw.compat;

import com.voltyx.efw.EFW;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Immersive Weathering's ivy is decor here: it is taken off the climbable tag by a KubeJS script (no climbing animation, the
 * Shoulder Surfing camera is not pulled to the centre), standing in it only slows the player a little and a roll cannot start in it.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class IvyTweaks {
    private static final ResourceLocation IVY = new ResourceLocation("immersive_weathering", "ivy");
    /** Horizontal speed kept per tick inside ivy. */
    private static final double SLOW = 0.9;

    private IvyTweaks() {
    }

    /** Whether the player's feet or body are in an ivy block. */
    public static boolean isInIvy(Player player) {
        BlockPos feet = player.blockPosition();
        return isIvy(player, feet) || isIvy(player, feet.above());
    }

    private static boolean isIvy(Player player, BlockPos pos) {
        BlockState state = player.level().getBlockState(pos);
        return IVY.equals(ForgeRegistries.BLOCKS.getKey(state.getBlock()));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.isSpectator() || e.player.getAbilities().flying || !isInIvy(e.player)) {
            return;
        }
        Vec3 motion = e.player.getDeltaMovement();
        e.player.setDeltaMovement(motion.x * SLOW, motion.y, motion.z * SLOW);
    }
}
