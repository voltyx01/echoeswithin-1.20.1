package com.voltyx.efw.roll;

import com.voltyx.efw.stamina.PlayerData;
import net.minecraft.world.entity.player.Player;

import java.util.function.Predicate;

/** "Is this player rolling right now" for common code (damage immunity, collisions). */
public final class RollState {
    /** Set by the client setup: the animation side knows the roll ticks of remote players too. */
    public static Predicate<Player> clientRolling = p -> false;

    private RollState() {
    }

    public static boolean isRolling(Player player) {
        if (player == null) {
            return false;
        }
        if (player.level().isClientSide && clientRolling.test(player)) {
            return true;
        }
        PlayerData data = PlayerData.get(player);
        return data != null && (data.dashing || data.postDashTimer > 0);
    }
}
